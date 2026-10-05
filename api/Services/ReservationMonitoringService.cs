using System.Globalization;
using System.Security.Claims;
using MongoDB.Bson;
using MongoDB.Driver;
using SolarGrid.Api.DTOs;
using SolarGrid.Api.Models;
using SolarGrid.Api.Repositories;

namespace SolarGrid.Api.Services;

/// <summary>
/// Member 4: booking dashboard, lists, approval and QR verification.
/// Works on Member 3's EnergyReservations collection. No separate booking store.
/// </summary>
public class ReservationMonitoringService
{
    private const int DefaultPageSize = 20;
    private const int MaxPageSize = 100;
    private const int MaxPage = 100_000;
    private const int MaxSearchTextLength = 100;

    private const string ConcurrentChangeMessage =
        "The reservation was changed by another request. Refresh and try again.";

    private readonly ReservationRepository _reservations;
    private readonly ReservationMonitoringRepository _monitoring;
    private readonly UserRepository _users;
    private readonly IQrTokenService _qr;
    private readonly QrOptions _qrOptions;
    private readonly TimeProvider _time;
    private readonly IEmailService _email;
    private readonly ILogger<ReservationMonitoringService> _logger;

    public ReservationMonitoringService(
        ReservationRepository reservations,
        ReservationMonitoringRepository monitoring,
        UserRepository users,
        IQrTokenService qr,
        QrOptions qrOptions,
        TimeProvider time,
        IEmailService email,
        ILogger<ReservationMonitoringService> logger)
    {
        _reservations = reservations;
        _monitoring = monitoring;
        _users = users;
        _qr = qr;
        _qrOptions = qrOptions;
        _time = time;
        _email = email;
        _logger = logger;
    }

    private DateTime UtcNow => _time.GetUtcNow().UtcDateTime;

    // ----- Dashboard -----

    public async Task<ReservationSummaryResponse> GetSummaryAsync(ClaimsPrincipal actor)
    {
        var (nic, role) = RequireAuth(actor);

        string? prosumerId;
        if (role == Role.PROSUMER)
        {
            prosumerId = nic;
        }
        else if (ReservationPermissions.Staff.Contains(role))
        {
            prosumerId = null;
        }
        else
        {
            throw new ReservationException(
                ReservationErrorKind.Forbidden,
                "You are not permitted to view the booking summary.");
        }

        var now = UtcNow;
        var pendingCount = await _monitoring.CountByStatusAsync(ReservationStatus.Pending, prosumerId);
        var approvedFutureCount = await _monitoring.CountApprovedFutureAsync(now, prosumerId);

        return new ReservationSummaryResponse
        {
            Message = "Booking summary retrieved.",
            Scope = prosumerId is null ? "All" : "Own",
            PendingCount = pendingCount,
            ApprovedFutureCount = approvedFutureCount
        };
    }

    // ----- Lists -----

    public async Task<ReservationPageResponse> GetMineAsync(
        ClaimsPrincipal actor,
        MyReservationsQuery query)
    {
        var (nic, role) = RequireAuth(actor);
        RequireRole(
            role,
            [Role.PROSUMER],
            "Only prosumers have a personal booking list. Staff should use /api/reservations.");

        var (page, pageSize) = ParsePaging(query.Page, query.PageSize);

        var criteria = new ReservationSearchCriteria
        {
            // Always the signed-in prosumer. Never taken from the request.
            ProsumerId = nic,
            Status = ParseStatus(query.Status),
            SlotDateUtc = ParseDate(query.DateUtc),
            Text = ParseSearchText(query.Q),
            Page = page,
            PageSize = pageSize
        };

        return await SearchAsync(criteria, "Bookings retrieved.");
    }

    public async Task<ReservationPageResponse> GetAllAsync(
        ClaimsPrincipal actor,
        ReservationListQuery query)
    {
        var (_, role) = RequireAuth(actor);
        RequireRole(
            role,
            ReservationPermissions.Staff,
            "Only Backoffice and Grid Operator staff can view all reservations.");

        var (page, pageSize) = ParsePaging(query.Page, query.PageSize);

        var criteria = new ReservationSearchCriteria
        {
            Status = ParseStatus(query.Status),
            StationId = ParseOptionalId(query.StationId, "stationId"),
            ReservationId = ParseOptionalId(query.Reference, "reference"),
            SlotDateUtc = ParseDate(query.DateUtc),
            Text = ParseSearchText(query.Q),
            Page = page,
            PageSize = pageSize
        };

        return await SearchAsync(criteria, "Reservations retrieved.");
    }

    // ----- Approval and completion -----

    public Task<ReservationActionResponse> ApproveAsync(
        string id,
        ClaimsPrincipal actor,
        long? expectedVersion) =>
        ChangeStatusAsync(id, actor, expectedVersion, ReservationAction.Approve);

    public Task<ReservationActionResponse> RejectAsync(
        string id,
        ClaimsPrincipal actor,
        long? expectedVersion) =>
        ChangeStatusAsync(id, actor, expectedVersion, ReservationAction.Reject);

    public Task<ReservationActionResponse> CompleteAsync(
        string id,
        ClaimsPrincipal actor,
        long? expectedVersion) =>
        ChangeStatusAsync(id, actor, expectedVersion, ReservationAction.Complete);

    private async Task<ReservationActionResponse> ChangeStatusAsync(
        string id,
        ClaimsPrincipal actor,
        long? expectedVersion,
        ReservationAction action)
    {
        var (nic, role) = RequireAuth(actor);

        if (action == ReservationAction.Complete)
        {
            RequireRole(
                role,
                ReservationPermissions.TransferOperators,
                "Only a Grid Operator can complete an energy transfer.");
        }
        else
        {
            RequireRole(
                role,
                ReservationPermissions.Approvers,
                "You are not permitted to approve or reject reservations.");
        }

        EnsureValidReservationId(id);

        using var session = await _reservations.StartSessionAsync();
        session.StartTransaction();

        try
        {
            var existing = await _reservations.GetByIdAsync(id, session)
                ?? throw new ReservationException(
                    ReservationErrorKind.NotFound,
                    "Reservation not found.");

            if (expectedVersion.HasValue && existing.Version != expectedVersion.Value)
            {
                throw new ReservationException(
                    ReservationErrorKind.Conflict,
                    ConcurrentChangeMessage);
            }

            // Checked before any capacity change, so a second reject or
            // complete never releases capacity again.
            if (!ReservationStatusRules.CanTransition(existing.Status, action))
            {
                throw new ReservationException(
                    ReservationErrorKind.Conflict,
                    ReservationStatusRules.BlockedMessage(existing.Status, action));
            }

            var slot = await _reservations.GetSlotByIdAsync(existing.SlotId, session)
                ?? throw new ReservationException(
                    ReservationErrorKind.NotFound,
                    "The reservation's energy slot was not found.");

            var now = UtcNow;

            if (action == ReservationAction.Approve && slot.StartTimeUtc <= now)
            {
                throw new ReservationException(
                    ReservationErrorKind.BadRequest,
                    "Cannot approve a reservation whose slot has already started.");
            }

            // Filter on Id + Status + Version: only one concurrent request can win.
            var updated = await _monitoring.TryChangeStatusAsync(
                existing.Id,
                existing.Status,
                existing.Version,
                ReservationStatusRules.TargetStatus(action),
                now,
                action == ReservationAction.Complete ? nic : null,
                session)
                ?? throw new ReservationException(
                    ReservationErrorKind.Conflict,
                    ConcurrentChangeMessage);

            if (ReservationStatusRules.ReleasesCapacity(action))
            {
                // Same transaction as the status change (Member 3 capacity model).
                slot = await _reservations.TryReleaseCapacityAsync(existing.SlotId, session)
                    ?? throw new ReservationException(
                        ReservationErrorKind.Conflict,
                        "Could not release reserved capacity for this reservation.");
            }

            await session.CommitTransactionAsync();

            var station = await _reservations.GetStationByIdAsync(updated.StationId);

            if (action is ReservationAction.Approve or ReservationAction.Reject)
            {
                await NotifyProsumerAsync(action == ReservationAction.Approve, updated, slot, station);
            }

            return new ReservationActionResponse
            {
                Message = action switch
                {
                    ReservationAction.Approve => "Reservation approved.",
                    ReservationAction.Reject => "Reservation rejected. Slot capacity released.",
                    _ => "Energy transfer completed."
                },
                ReservationId = updated.Id,
                Reservation = ToDetails(updated, slot, station)
            };
        }
        catch (MongoException ex) when (ex.HasErrorLabel("TransientTransactionError"))
        {
            // Another transaction touched the same document at the same time.
            await SafeAbortAsync(session);
            throw new ReservationException(ReservationErrorKind.Conflict, ConcurrentChangeMessage);
        }
        catch
        {
            await SafeAbortAsync(session);
            throw;
        }
    }

    /// <summary>
    /// Emails the prosumer about an approval decision. Runs after the commit and
    /// never throws: the decision is already saved, so a mail problem must not
    /// turn a successful approve/reject into an error.
    /// </summary>
    private async Task NotifyProsumerAsync(
        bool approved,
        EnergyReservation reservation,
        EnergyBookingSlot slot,
        SolarStation? station)
    {
        try
        {
            var prosumer = await _users.GetByNICAsync(reservation.ProsumerId);
            if (prosumer is null || string.IsNullOrWhiteSpace(prosumer.Email))
            {
                _logger.LogWarning(
                    "No email address for prosumer {ProsumerId}; skipped booking decision email for {ReservationId}.",
                    reservation.ProsumerId,
                    reservation.Id);
                return;
            }

            var email = ReservationEmailTemplates.BuildDecision(approved, prosumer.Name, reservation, slot, station);
            await _email.SendEmailAsync(prosumer.Email, email.Subject, email.TextBody, email.HtmlBody);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Could not send booking decision email for {ReservationId}.", reservation.Id);
        }
    }

    // ----- QR -----

    public async Task<QrCodeResponse> GetQrAsync(string id, ClaimsPrincipal actor)
    {
        var (nic, role) = RequireAuth(actor);
        RequireRole(
            role,
            [Role.PROSUMER],
            "Only the prosumer who owns the reservation can display its QR code.");

        EnsureValidReservationId(id);

        var reservation = await _reservations.GetByIdAsync(id)
            ?? throw new ReservationException(
                ReservationErrorKind.NotFound,
                "Reservation not found.");

        if (!string.Equals(reservation.ProsumerId, nic, StringComparison.OrdinalIgnoreCase))
        {
            throw new ReservationException(
                ReservationErrorKind.Forbidden,
                "You are not permitted to access this reservation.");
        }

        if (!ReservationStatusRules.CanIssueQr(reservation.Status))
        {
            throw new ReservationException(
                ReservationErrorKind.Conflict,
                $"A QR code is only available for an approved reservation. Current status: {reservation.Status}.");
        }

        var slot = await _reservations.GetSlotByIdAsync(reservation.SlotId)
            ?? throw new ReservationException(
                ReservationErrorKind.NotFound,
                "The reservation's energy slot was not found.");

        if (UtcNow >= slot.EndTimeUtc.Add(_qrOptions.ValidAfterSlotEnd))
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                "The energy slot has ended. A QR code can no longer be issued.");
        }

        var token = _qr.GenerateToken(reservation, slot);

        return new QrCodeResponse
        {
            Message = "QR code issued.",
            ReservationId = reservation.Id,
            Qr = new QrCodeDto
            {
                Payload = token.Payload,
                IssuedAtUtc = token.IssuedAtUtc,
                ValidFromUtc = slot.StartTimeUtc.Subtract(_qrOptions.ValidBeforeSlotStart),
                ExpiresAtUtc = token.ExpiresAtUtc
            }
        };
    }

    /// <summary>
    /// Read-only. Checks the signature and expiry, then re-checks the
    /// reservation in the database because its status may have changed.
    /// </summary>
    public async Task<QrVerificationResponse> VerifyQrAsync(ClaimsPrincipal actor, string? payload)
    {
        var (_, role) = RequireAuth(actor);
        RequireRole(
            role,
            ReservationPermissions.TransferOperators,
            "Only a Grid Operator can verify transaction QR codes.");

        if (string.IsNullOrWhiteSpace(payload))
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                "QR payload is required.");
        }

        var result = _qr.ValidateToken(payload);

        switch (result.Status)
        {
            case QrValidationStatus.Malformed:
            case QrValidationStatus.InvalidSignature:
                throw new ReservationException(
                    ReservationErrorKind.BadRequest,
                    "Invalid QR code. It is damaged or has been tampered with.");
            case QrValidationStatus.Expired:
                throw new ReservationException(
                    ReservationErrorKind.BadRequest,
                    "This QR code has expired.");
        }

        var qr = result.Payload!;

        if (!ObjectId.TryParse(qr.ReservationId, out _))
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                "Invalid QR code. It is damaged or has been tampered with.");
        }

        var reservation = await _reservations.GetByIdAsync(qr.ReservationId)
            ?? throw new ReservationException(
                ReservationErrorKind.NotFound,
                "The reservation for this QR code was not found.");

        switch (reservation.Status)
        {
            case ReservationStatus.Cancelled:
                throw new ReservationException(
                    ReservationErrorKind.Conflict,
                    "This reservation was cancelled. The QR code is no longer valid.");
            case ReservationStatus.Rejected:
                throw new ReservationException(
                    ReservationErrorKind.Conflict,
                    "This reservation was rejected. The QR code is not valid.");
            case ReservationStatus.Completed:
                throw new ReservationException(
                    ReservationErrorKind.Conflict,
                    "This energy transfer is already completed.");
            case ReservationStatus.Pending:
                throw new ReservationException(
                    ReservationErrorKind.Conflict,
                    "This reservation is not approved.");
        }

        // The booking may have been moved to another slot after the QR was issued.
        if (!string.Equals(reservation.ProsumerId, qr.ProsumerId, StringComparison.Ordinal) ||
            !string.Equals(reservation.StationId, qr.StationId, StringComparison.Ordinal) ||
            !string.Equals(reservation.SlotId, qr.SlotId, StringComparison.Ordinal))
        {
            throw new ReservationException(
                ReservationErrorKind.Conflict,
                "This QR code no longer matches the reservation. Ask the prosumer to open the QR code again.");
        }

        var slot = await _reservations.GetSlotByIdAsync(reservation.SlotId)
            ?? throw new ReservationException(
                ReservationErrorKind.NotFound,
                "The reservation's energy slot was not found.");

        var validFrom = slot.StartTimeUtc.Subtract(_qrOptions.ValidBeforeSlotStart);
        if (UtcNow < validFrom)
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                $"This QR code is not valid yet. Scanning opens at {validFrom:yyyy-MM-dd HH:mm} UTC.");
        }

        var station = await _reservations.GetStationByIdAsync(reservation.StationId);
        var prosumer = await _users.GetByNICAsync(reservation.ProsumerId);

        return new QrVerificationResponse
        {
            Message = "QR code verified. The reservation is approved and ready to complete.",
            ReservationId = reservation.Id,
            Reservation = ToDetails(reservation, slot, station),
            // Only NIC and name. Never the full user document.
            Prosumer = new QrProsumerDto
            {
                Nic = reservation.ProsumerId,
                Name = prosumer?.Name
            },
            QrIssuedAtUtc = qr.IssuedAtUtc,
            QrExpiresAtUtc = qr.ExpiresAtUtc
        };
    }

    // ----- Helpers -----

    private async Task<ReservationPageResponse> SearchAsync(
        ReservationSearchCriteria criteria,
        string message)
    {
        var (items, totalCount) = await _monitoring.SearchAsync(criteria);

        var slots = await _monitoring.GetSlotsByIdsAsync(items.Select(r => r.SlotId));
        var stations = await _monitoring.GetStationsByIdsAsync(items.Select(r => r.StationId));

        return new ReservationPageResponse
        {
            Message = message,
            Items = items
                .Select(r => ToDetails(
                    r,
                    slots.GetValueOrDefault(r.SlotId),
                    stations.GetValueOrDefault(r.StationId)))
                .ToList(),
            Page = criteria.Page,
            PageSize = criteria.PageSize,
            TotalCount = totalCount,
            TotalPages = (int)Math.Ceiling(totalCount / (double)criteria.PageSize)
        };
    }

    private static ReservationDetailsDto ToDetails(
        EnergyReservation reservation,
        EnergyBookingSlot? slot,
        SolarStation? station)
    {
        return new ReservationDetailsDto
        {
            Id = reservation.Id,
            ProsumerId = reservation.ProsumerId,
            StationId = reservation.StationId,
            StationName = station?.Name,
            SlotId = reservation.SlotId,
            Status = reservation.Status,
            CreatedAtUtc = reservation.CreatedAtUtc,
            UpdatedAtUtc = reservation.UpdatedAtUtc,
            CancelledAtUtc = reservation.CancelledAtUtc,
            ApprovedAtUtc = reservation.ApprovedAtUtc,
            RejectedAtUtc = reservation.RejectedAtUtc,
            CompletedAtUtc = reservation.CompletedAtUtc,
            CompletedByOperatorId = reservation.CompletedByOperatorId,
            Version = reservation.Version,
            SlotStartTimeUtc = slot?.StartTimeUtc,
            SlotEndTimeUtc = slot?.EndTimeUtc,
            RemainingBookings = slot?.RemainingBookings
        };
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

        if (!Enum.TryParse<Role>(roleValue, ignoreCase: true, out var role) ||
            !Enum.IsDefined(role))
        {
            throw new ReservationException(
                ReservationErrorKind.Forbidden,
                "Authenticated role is not recognized.");
        }

        return (nic, role);
    }

    private static void RequireRole(Role role, IReadOnlyList<Role> allowed, string message)
    {
        if (!allowed.Contains(role))
        {
            throw new ReservationException(ReservationErrorKind.Forbidden, message);
        }
    }

    private static void EnsureValidReservationId(string id)
    {
        if (!ObjectId.TryParse(id, out _))
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                "Reservation id is not valid.");
        }
    }

    private static string? ParseOptionalId(string? value, string name)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            return null;
        }

        var trimmed = value.Trim();
        if (!ObjectId.TryParse(trimmed, out _))
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                $"{name} must be a valid 24-character id.");
        }

        return trimmed;
    }

    private static ReservationStatus? ParseStatus(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            return null;
        }

        if (Enum.TryParse<ReservationStatus>(value.Trim(), ignoreCase: true, out var status) &&
            Enum.IsDefined(status))
        {
            return status;
        }

        throw new ReservationException(
            ReservationErrorKind.BadRequest,
            "status must be Pending, Approved, Cancelled, Rejected or Completed.");
    }

    private static DateTime? ParseDate(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            return null;
        }

        if (DateTime.TryParse(
                value.Trim(),
                CultureInfo.InvariantCulture,
                DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal,
                out var date))
        {
            return DateTime.SpecifyKind(date.Date, DateTimeKind.Utc);
        }

        throw new ReservationException(
            ReservationErrorKind.BadRequest,
            "dateUtc must be a date such as 2026-09-30.");
    }

    private static string? ParseSearchText(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            return null;
        }

        var trimmed = value.Trim();
        if (trimmed.Length > MaxSearchTextLength)
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                $"Search text must be {MaxSearchTextLength} characters or fewer.");
        }

        return trimmed;
    }

    private static (int Page, int PageSize) ParsePaging(int? page, int? pageSize)
    {
        var p = page ?? 1;
        var size = pageSize ?? DefaultPageSize;

        if (p < 1 || p > MaxPage)
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                $"page must be between 1 and {MaxPage}.");
        }

        if (size < 1 || size > MaxPageSize)
        {
            throw new ReservationException(
                ReservationErrorKind.BadRequest,
                $"pageSize must be between 1 and {MaxPageSize}.");
        }

        return (p, size);
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
