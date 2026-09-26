using System.Security.Claims;
using MongoDB.Driver;
using SolarGrid.Api.Models;
using SolarGrid.Api.Repositories;

namespace SolarGrid.Api.Services;

public class ReservationService
{
    private static readonly TimeSpan SevenDays = TimeSpan.FromDays(7);
    private static readonly TimeSpan MinLeadTime = TimeSpan.FromHours(12);

    private readonly ReservationRepository _reservations;
    private readonly UserRepository _users;

    public ReservationService(
        ReservationRepository reservations,
        UserRepository users)
    {
        _reservations = reservations;
        _users = users;
    }

    public async Task<object> GetByIdAsync(string id, ClaimsPrincipal actor)
    {
        var reservation = await LoadAccessibleAsync(id, actor);
        var slot = await _reservations.GetSlotByIdAsync(reservation.SlotId);
        return BuildSummary(reservation, slot, "Reservation details retrieved.");
    }

    public async Task<object> CreateAsync(
        ClaimsPrincipal actor,
        string slotId,
        string? stationId,
        string? requestedProsumerId)
    {
        var (_, role) = RequireAuth(actor);
        var prosumerId = await ResolveProsumerIdAsync(actor, role, requestedProsumerId);
        await EnsureProsumerActiveAsync(prosumerId);

        if (string.IsNullOrWhiteSpace(slotId))
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                "Slot ID is required.");
        }

        var slot = await LoadActiveSlotAsync(slotId);
        EnsureStationMatches(slot, stationId);
        var station = await LoadActiveStationAsync(slot.StationId);
        await EnsureWithinOperatingScheduleAsync(station.Id, slot);
        EnsureWithinSevenDays(slot.StartTimeUtc, DateTime.UtcNow);

        using var session = await _reservations.StartSessionAsync();
        session.StartTransaction();

        try
        {
            await EnsureNoConflictAsync(session, prosumerId, slot, excludeId: null);

            var reserved = await _reservations.TryReserveCapacityAsync(slot.Id, session);
            if (reserved is null)
            {
                throw new ReservationException(
                    ReservationErrorKind.Conflict,
                    "This energy slot has no remaining capacity.");
            }

            var now = DateTime.UtcNow;
            var reservation = new EnergyReservation
            {
                ProsumerId = prosumerId,
                StationId = slot.StationId,
                SlotId = slot.Id,
                Status = ReservationStatus.Pending,
                CreatedAtUtc = now,
                UpdatedAtUtc = now,
                Version = 1
            };

            await _reservations.InsertAsync(reservation, session);
            await session.CommitTransactionAsync();

            return BuildSummary(reservation, reserved, "Reservation created.");
        }
        catch
        {
            await SafeAbortAsync(session);
            throw;
        }
    }

    public async Task<object> UpdateAsync(
        string id,
        ClaimsPrincipal actor,
        string newSlotId,
        string? newStationId,
        long? expectedVersion)
    {
        RequireAuth(actor);

        if (string.IsNullOrWhiteSpace(newSlotId))
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                "New slot ID is required.");
        }

        using var session = await _reservations.StartSessionAsync();
        session.StartTransaction();

        try
        {
            var existing = await _reservations.GetByIdAsync(id, session)
                ?? throw new ReservationException(
                    ReservationErrorKind.NotFound,
                    "Reservation not found.");

            EnsureCanAccess(actor, existing);
            EnsureMutable(existing);

            if (expectedVersion.HasValue && existing.Version != expectedVersion.Value)
            {
                throw new ReservationException(
                    ReservationErrorKind.Conflict,
                    "The reservation was changed by another request. Refresh and try again.");
            }

            var currentSlot = await _reservations.GetSlotByIdAsync(existing.SlotId, session)
                ?? throw new ReservationException(
                    ReservationErrorKind.NotFound,
                    "The reservation's current energy slot was not found.");

            EnsureAtLeastTwelveHours(currentSlot.StartTimeUtc, DateTime.UtcNow);

            if (string.Equals(existing.SlotId, newSlotId, StringComparison.Ordinal))
            {
                throw new ReservationException(
                    ReservationErrorKind.BadRequest,
                    "The reservation already uses this energy slot.");
            }

            var newSlot = await LoadActiveSlotAsync(newSlotId, session);
            EnsureStationMatches(newSlot, newStationId);
            var station = await LoadActiveStationAsync(newSlot.StationId, session);
            await EnsureWithinOperatingScheduleAsync(station.Id, newSlot, session);
            EnsureWithinSevenDays(newSlot.StartTimeUtc, DateTime.UtcNow);
            await EnsureNoConflictAsync(session, existing.ProsumerId, newSlot, existing.Id);

            // Release old then reserve new inside one transaction.
            // If the new slot is full, abort keeps the original booking unchanged.
            if (await _reservations.TryReleaseCapacityAsync(existing.SlotId, session) is null)
            {
                throw new ReservationException(
                    ReservationErrorKind.Conflict,
                    "Could not release capacity from the current slot.");
            }

            var reserved = await _reservations.TryReserveCapacityAsync(newSlot.Id, session);
            if (reserved is null)
            {
                throw new ReservationException(
                    ReservationErrorKind.Conflict,
                    "The selected energy slot has no remaining capacity.");
            }

            var previousVersion = existing.Version;
            var wasApproved = existing.Status == ReservationStatus.Approved;

            existing.StationId = newSlot.StationId;
            existing.SlotId = newSlot.Id;
            existing.Status = ReservationStatus.Pending;
            existing.UpdatedAtUtc = DateTime.UtcNow;
            existing.Version = previousVersion + 1;

            var updated = await _reservations.ReplaceIfVersionMatchesAsync(
                existing,
                previousVersion,
                session);

            if (updated is null)
            {
                throw new ReservationException(
                    ReservationErrorKind.Conflict,
                    "The reservation was changed by another request. Refresh and try again.");
            }

            await session.CommitTransactionAsync();

            var message = wasApproved
                ? "Reservation updated. Status returned to Pending."
                : "Reservation updated.";

            return BuildSummary(updated, reserved, message);
        }
        catch
        {
            await SafeAbortAsync(session);
            throw;
        }
    }

    public async Task<object> CancelAsync(
        string id,
        ClaimsPrincipal actor,
        long? expectedVersion)
    {
        RequireAuth(actor);

        using var session = await _reservations.StartSessionAsync();
        session.StartTransaction();

        try
        {
            var existing = await _reservations.GetByIdAsync(id, session)
                ?? throw new ReservationException(
                    ReservationErrorKind.NotFound,
                    "Reservation not found.");

            EnsureCanAccess(actor, existing);

            if (existing.Status == ReservationStatus.Cancelled)
            {
                throw new ReservationException(
                    ReservationErrorKind.Conflict,
                    "Reservation is already cancelled. Capacity was not released again.");
            }

            if (existing.Status is ReservationStatus.Rejected or ReservationStatus.Completed)
            {
                throw new ReservationException(
                    ReservationErrorKind.BadRequest,
                    $"Cannot cancel a {existing.Status} reservation.");
            }

            if (expectedVersion.HasValue && existing.Version != expectedVersion.Value)
            {
                throw new ReservationException(
                    ReservationErrorKind.Conflict,
                    "The reservation was changed by another request. Refresh and try again.");
            }

            var slot = await _reservations.GetSlotByIdAsync(existing.SlotId, session)
                ?? throw new ReservationException(
                    ReservationErrorKind.NotFound,
                    "The reservation's energy slot was not found.");

            EnsureAtLeastTwelveHours(slot.StartTimeUtc, DateTime.UtcNow);

            if (await _reservations.TryReleaseCapacityAsync(existing.SlotId, session) is null)
            {
                throw new ReservationException(
                    ReservationErrorKind.Conflict,
                    "Could not release reserved capacity for this reservation.");
            }

            var previousVersion = existing.Version;
            var now = DateTime.UtcNow;
            existing.Status = ReservationStatus.Cancelled;
            existing.CancelledAtUtc = now;
            existing.UpdatedAtUtc = now;
            existing.Version = previousVersion + 1;

            var updated = await _reservations.ReplaceIfVersionMatchesAsync(
                existing,
                previousVersion,
                session);

            if (updated is null)
            {
                throw new ReservationException(
                    ReservationErrorKind.Conflict,
                    "The reservation was changed by another request. Refresh and try again.");
            }

            await session.CommitTransactionAsync();
            return BuildSummary(updated, slot, "Reservation cancelled.");
        }
        catch
        {
            await SafeAbortAsync(session);
            throw;
        }
    }

    private async Task<EnergyReservation> LoadAccessibleAsync(string id, ClaimsPrincipal actor)
    {
        RequireAuth(actor);
        var reservation = await _reservations.GetByIdAsync(id)
            ?? throw new ReservationException(
                ReservationErrorKind.NotFound,
                "Reservation not found.");
        EnsureCanAccess(actor, reservation);
        return reservation;
    }

    private static (string Nic, Role Role) RequireAuth(ClaimsPrincipal actor)
    {
        var nic = actor.FindFirstValue(ClaimTypes.NameIdentifier);
        var roleValue = actor.FindFirstValue(ClaimTypes.Role);

        if (string.IsNullOrWhiteSpace(nic) || string.IsNullOrWhiteSpace(roleValue))
        {
            throw new ReservationException(
                ReservationErrorKind.Unauthorized,
                "Authentication is required.");
        }

        if (!Enum.TryParse<Role>(roleValue, ignoreCase: true, out var role))
        {
            throw new ReservationException(
                ReservationErrorKind.Forbidden,
                "Authenticated role is not recognized.");
        }

        return (nic, role);
    }

    private async Task<string> ResolveProsumerIdAsync(
        ClaimsPrincipal actor,
        Role role,
        string? requestedProsumerId)
    {
        var nic = actor.FindFirstValue(ClaimTypes.NameIdentifier)!;

        if (role == Role.PROSUMER)
        {
            if (!string.IsNullOrWhiteSpace(requestedProsumerId) &&
                !string.Equals(requestedProsumerId, nic, StringComparison.OrdinalIgnoreCase))
            {
                throw new ReservationException(
                    ReservationErrorKind.Forbidden,
                    "Prosumers can only create reservations for themselves.");
            }

            return nic;
        }

        if (role is Role.BACKOFFICE or Role.GRID_OPERATOR)
        {
            if (string.IsNullOrWhiteSpace(requestedProsumerId))
            {
                throw new ReservationException(
                    ReservationErrorKind.BadRequest,
                    "Staff must provide the prosumer NIC when creating a reservation.");
            }

            return requestedProsumerId.Trim();
        }

        throw new ReservationException(
            ReservationErrorKind.Forbidden,
            "You are not permitted to create reservations.");
    }

    private async Task EnsureProsumerActiveAsync(string prosumerId)
    {
        var user = await _users.GetByNICAsync(prosumerId)
            ?? throw new ReservationException(
                ReservationErrorKind.NotFound,
                "Prosumer account not found.");

        if (user.Role != Role.PROSUMER)
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                "Reservations can only be created for prosumer accounts.");
        }

        if (user.AccountStatus != AccountStatus.ACTIVE)
        {
            throw new ReservationException(
                ReservationErrorKind.Forbidden,
                "The prosumer account is not active.");
        }
    }

    private static void EnsureCanAccess(ClaimsPrincipal actor, EnergyReservation reservation)
    {
        var (nic, role) = RequireAuth(actor);

        if (role is Role.BACKOFFICE or Role.GRID_OPERATOR)
        {
            return;
        }

        if (role == Role.PROSUMER &&
            string.Equals(reservation.ProsumerId, nic, StringComparison.OrdinalIgnoreCase))
        {
            return;
        }

        throw new ReservationException(
            ReservationErrorKind.Forbidden,
            "You are not permitted to access this reservation.");
    }

    private static void EnsureMutable(EnergyReservation reservation)
    {
        if (reservation.Status is ReservationStatus.Cancelled
            or ReservationStatus.Rejected
            or ReservationStatus.Completed)
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                $"Cannot update a {reservation.Status} reservation.");
        }
    }

    private async Task<EnergyBookingSlot> LoadActiveSlotAsync(
        string slotId,
        IClientSessionHandle? session = null)
    {
        var slot = await _reservations.GetSlotByIdAsync(slotId, session)
            ?? throw new ReservationException(
                ReservationErrorKind.NotFound,
                "Energy slot not found.");

        if (!slot.IsActive)
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                "The selected energy slot is inactive.");
        }

        if (slot.EndTimeUtc <= slot.StartTimeUtc)
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                "Energy slot has an invalid time range.");
        }

        return slot;
    }

    private async Task<SolarStation> LoadActiveStationAsync(
        string stationId,
        IClientSessionHandle? session = null)
    {
        var station = await _reservations.GetStationByIdAsync(stationId, session)
            ?? throw new ReservationException(
                ReservationErrorKind.NotFound,
                "Station not found.");

        if (!station.IsActive)
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                "The selected station is inactive.");
        }

        return station;
    }

    private static void EnsureStationMatches(EnergyBookingSlot slot, string? stationId)
    {
        if (!string.IsNullOrWhiteSpace(stationId) &&
            !string.Equals(slot.StationId, stationId, StringComparison.Ordinal))
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                "The selected slot does not belong to the specified station.");
        }
    }

    private async Task EnsureWithinOperatingScheduleAsync(
        string stationId,
        EnergyBookingSlot slot,
        IClientSessionHandle? session = null)
    {
        var schedule = await _reservations.GetScheduleForDayAsync(
            stationId,
            slot.StartTimeUtc.DayOfWeek,
            session);

        // If Member 2 has not configured a schedule for that day, allow the slot.
        if (schedule is null)
        {
            return;
        }

        if (!schedule.IsAvailable)
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                "The station is not available on the selected day.");
        }

        var start = slot.StartTimeUtc.TimeOfDay;
        var end = slot.EndTimeUtc.TimeOfDay;

        if (start < schedule.OpeningTime || end > schedule.ClosingTime)
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                "The energy slot is outside the station operating schedule.");
        }
    }

    private static void EnsureWithinSevenDays(DateTime slotStartUtc, DateTime nowUtc)
    {
        // Rolling window: now < start <= now + 7 days
        if (slotStartUtc <= nowUtc)
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                "Cannot book a slot that has already started.");
        }

        if (slotStartUtc > nowUtc.Add(SevenDays))
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                "Reservations are only allowed within the next seven days.");
        }
    }

    private static void EnsureAtLeastTwelveHours(DateTime slotStartUtc, DateTime nowUtc)
    {
        if (slotStartUtc - nowUtc < MinLeadTime)
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                "Changes require at least 12 hours before the slot start time.");
        }
    }

    private async Task EnsureNoConflictAsync(
        IClientSessionHandle session,
        string prosumerId,
        EnergyBookingSlot newSlot,
        string? excludeId)
    {
        if (await _reservations.HasActiveForSlotAsync(prosumerId, newSlot.Id, excludeId, session))
        {
            throw new ReservationException(
                ReservationErrorKind.Conflict,
                "You already have an active reservation for this energy slot.");
        }

        var active = await _reservations.GetActiveByProsumerAsync(prosumerId, excludeId, session);

        foreach (var reservation in active)
        {
            var existingSlot = await _reservations.GetSlotByIdAsync(reservation.SlotId, session);
            if (existingSlot is null)
            {
                continue;
            }

            // Overlap: newStart < existingEnd AND newEnd > existingStart
            // Back-to-back bookings do not overlap.
            if (newSlot.StartTimeUtc < existingSlot.EndTimeUtc &&
                newSlot.EndTimeUtc > existingSlot.StartTimeUtc)
            {
                throw new ReservationException(
                    ReservationErrorKind.Conflict,
                    "This booking overlaps an existing active reservation.");
            }
        }
    }

    private static object BuildSummary(
        EnergyReservation reservation,
        EnergyBookingSlot? slot,
        string message)
    {
        return new
        {
            message,
            reservationId = reservation.Id,
            reservation = new
            {
                reservation.Id,
                reservation.ProsumerId,
                reservation.StationId,
                reservation.SlotId,
                status = reservation.Status.ToString(),
                reservation.CreatedAtUtc,
                reservation.UpdatedAtUtc,
                reservation.CancelledAtUtc,
                reservation.Version,
                slotStartTimeUtc = slot?.StartTimeUtc,
                slotEndTimeUtc = slot?.EndTimeUtc,
                remainingBookings = slot?.RemainingBookings
            }
        };
    }

    private static async Task SafeAbortAsync(IClientSessionHandle session)
    {
        try
        {
            await session.AbortTransactionAsync();
        }
        catch
        {
            // Already aborted or never started.
        }
    }
}
