using MongoDB.Driver;
using SolarGrid.Api.Data;
using SolarGrid.Api.Models;

namespace SolarGrid.Api.Repositories;

public class ReservationRepository
{
    private readonly MongoDbContext _context;
    private readonly IMongoCollection<EnergyReservation> _reservations;
    private readonly IMongoCollection<EnergyBookingSlot> _slots;
    private readonly IMongoCollection<SolarStation> _stations;
    private readonly IMongoCollection<StationSchedule> _schedules;

    public ReservationRepository(MongoDbContext context)
    {
        _context = context;
        _reservations = context.Database.GetCollection<EnergyReservation>("EnergyReservations");
        _slots = context.Database.GetCollection<EnergyBookingSlot>("EnergyBookingSlots");
        _stations = context.Database.GetCollection<SolarStation>("SolarStationInfo");
        _schedules = context.Database.GetCollection<StationSchedule>("StationSchedules");
    }

    public Task<IClientSessionHandle> StartSessionAsync() =>
        _context.Client.StartSessionAsync();

    public async Task<EnergyReservation?> GetByIdAsync(
        string id,
        IClientSessionHandle? session = null)
    {
        if (session is null)
        {
            return await _reservations.Find(r => r.Id == id).FirstOrDefaultAsync();
        }

        return await _reservations.Find(session, r => r.Id == id).FirstOrDefaultAsync();
    }

    public Task InsertAsync(EnergyReservation reservation, IClientSessionHandle session) =>
        _reservations.InsertOneAsync(session, reservation);

    public async Task<EnergyReservation?> ReplaceIfVersionMatchesAsync(
        EnergyReservation reservation,
        long expectedVersion,
        IClientSessionHandle session)
    {
        var filter = Builders<EnergyReservation>.Filter.And(
            Builders<EnergyReservation>.Filter.Eq(r => r.Id, reservation.Id),
            Builders<EnergyReservation>.Filter.Eq(r => r.Version, expectedVersion));

        return await _reservations.FindOneAndReplaceAsync(
            session,
            filter,
            reservation,
            new FindOneAndReplaceOptions<EnergyReservation>
            {
                ReturnDocument = ReturnDocument.After
            });
    }

    public async Task<EnergyBookingSlot?> GetSlotByIdAsync(
        string slotId,
        IClientSessionHandle? session = null)
    {
        if (session is null)
        {
            return await _slots.Find(s => s.Id == slotId).FirstOrDefaultAsync();
        }

        return await _slots.Find(session, s => s.Id == slotId).FirstOrDefaultAsync();
    }

    public async Task<SolarStation?> GetStationByIdAsync(
        string stationId,
        IClientSessionHandle? session = null)
    {
        if (session is null)
        {
            return await _stations.Find(s => s.Id == stationId).FirstOrDefaultAsync();
        }

        return await _stations.Find(session, s => s.Id == stationId).FirstOrDefaultAsync();
    }

    public async Task<StationSchedule?> GetScheduleForDayAsync(
        string stationId,
        DayOfWeek day,
        IClientSessionHandle? session = null)
    {
        var filter = Builders<StationSchedule>.Filter.And(
            Builders<StationSchedule>.Filter.Eq(s => s.StationId, stationId),
            Builders<StationSchedule>.Filter.Eq(s => s.Day, day));

        if (session is null)
        {
            return await _schedules.Find(filter).FirstOrDefaultAsync();
        }

        return await _schedules.Find(session, filter).FirstOrDefaultAsync();
    }

    /// <summary>
    /// Conditionally increments ReservedBookings only when capacity remains.
    /// </summary>
    public async Task<EnergyBookingSlot?> TryReserveCapacityAsync(
        string slotId,
        IClientSessionHandle session)
    {
        var filter = Builders<EnergyBookingSlot>.Filter.And(
            Builders<EnergyBookingSlot>.Filter.Eq(s => s.Id, slotId),
            Builders<EnergyBookingSlot>.Filter.Eq(s => s.IsActive, true),
            Builders<EnergyBookingSlot>.Filter.Where(s =>
                s.ReservedBookings < s.MaximumBookings));

        var update = Builders<EnergyBookingSlot>.Update.Inc(s => s.ReservedBookings, 1);

        return await _slots.FindOneAndUpdateAsync(
            session,
            filter,
            update,
            new FindOneAndUpdateOptions<EnergyBookingSlot>
            {
                ReturnDocument = ReturnDocument.After
            });
    }

    /// <summary>
    /// Decrements ReservedBookings once. Safe for idempotent cancel flows when
    /// status is checked before calling.
    /// </summary>
    public async Task<EnergyBookingSlot?> TryReleaseCapacityAsync(
        string slotId,
        IClientSessionHandle session)
    {
        var filter = Builders<EnergyBookingSlot>.Filter.And(
            Builders<EnergyBookingSlot>.Filter.Eq(s => s.Id, slotId),
            Builders<EnergyBookingSlot>.Filter.Gt(s => s.ReservedBookings, 0));

        var update = Builders<EnergyBookingSlot>.Update.Inc(s => s.ReservedBookings, -1);

        return await _slots.FindOneAndUpdateAsync(
            session,
            filter,
            update,
            new FindOneAndUpdateOptions<EnergyBookingSlot>
            {
                ReturnDocument = ReturnDocument.After
            });
    }

    public async Task<bool> HasActiveForSlotAsync(
        string prosumerId,
        string slotId,
        string? excludeReservationId = null,
        IClientSessionHandle? session = null)
    {
        var filters = new List<FilterDefinition<EnergyReservation>>
        {
            Builders<EnergyReservation>.Filter.Eq(r => r.ProsumerId, prosumerId),
            Builders<EnergyReservation>.Filter.Eq(r => r.SlotId, slotId),
            Builders<EnergyReservation>.Filter.In(
                r => r.Status,
                new[] { ReservationStatus.Pending, ReservationStatus.Approved })
        };

        if (!string.IsNullOrWhiteSpace(excludeReservationId))
        {
            filters.Add(Builders<EnergyReservation>.Filter.Ne(r => r.Id, excludeReservationId));
        }

        var filter = Builders<EnergyReservation>.Filter.And(filters);

        if (session is null)
        {
            return await _reservations.Find(filter).AnyAsync();
        }

        return await _reservations.Find(session, filter).AnyAsync();
    }

    public async Task<List<EnergyReservation>> GetActiveByProsumerAsync(
        string prosumerId,
        string? excludeReservationId = null,
        IClientSessionHandle? session = null)
    {
        var filters = new List<FilterDefinition<EnergyReservation>>
        {
            Builders<EnergyReservation>.Filter.Eq(r => r.ProsumerId, prosumerId),
            Builders<EnergyReservation>.Filter.In(
                r => r.Status,
                new[] { ReservationStatus.Pending, ReservationStatus.Approved })
        };

        if (!string.IsNullOrWhiteSpace(excludeReservationId))
        {
            filters.Add(Builders<EnergyReservation>.Filter.Ne(r => r.Id, excludeReservationId));
        }

        var filter = Builders<EnergyReservation>.Filter.And(filters);

        if (session is null)
        {
            return await _reservations.Find(filter).ToListAsync();
        }

        return await _reservations.Find(session, filter).ToListAsync();
    }

    /// <summary>
    /// Real active-reservation check for Member 2 station deactivation.
    /// </summary>
    public Task<bool> HasActiveForStationAsync(string stationId)
    {
        var filter = Builders<EnergyReservation>.Filter.And(
            Builders<EnergyReservation>.Filter.Eq(r => r.StationId, stationId),
            Builders<EnergyReservation>.Filter.In(
                r => r.Status,
                new[] { ReservationStatus.Pending, ReservationStatus.Approved }));

        return _reservations.Find(filter).AnyAsync();
    }
}
