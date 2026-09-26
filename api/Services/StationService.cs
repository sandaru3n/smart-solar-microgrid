using MongoDB.Driver;
using SolarGrid.Api.Models;

namespace SolarGrid.Api.Services;

public class StationService
{
    private readonly IMongoCollection<SolarStation> _stations;
    private readonly IMongoCollection<EnergyBookingSlot> _slots;
    private readonly IMongoCollection<StationSchedule> _schedules;

    public StationService(IMongoDatabase database)
    {
        _stations = database.GetCollection<SolarStation>("SolarStationInfo");
        _slots = database.GetCollection<EnergyBookingSlot>("EnergyBookingSlots");
        _schedules = database.GetCollection<StationSchedule>("StationSchedules");
    }

    public async Task<List<SolarStation>> GetActiveStationsAsync()
    {
        return await _stations
            .Find(station => station.IsActive)
            .ToListAsync();
    }

    public async Task<SolarStation?> GetByIdAsync(string id)
    {
        return await _stations
            .Find(station => station.Id == id)
            .FirstOrDefaultAsync();
    }

    public async Task<SolarStation> CreateAsync(SolarStation station)
    {
        station.IsActive = true;
        station.CreatedAtUtc = DateTime.UtcNow;
        station.UpdatedAtUtc = DateTime.UtcNow;

        await _stations.InsertOneAsync(station);
        return station;
    }

    public async Task<bool> UpdateAsync(string id, SolarStation station)
    {
        station.UpdatedAtUtc = DateTime.UtcNow;

        var result = await _stations.ReplaceOneAsync(
            existing => existing.Id == id,
            station);

        return result.ModifiedCount > 0;
    }

    // True when any slot for the station still has reserved bookings.
    // Member 3 can later extend this to a dedicated Reservations collection.
    public async Task<bool> HasActiveReservationsAsync(string stationId)
    {
        return await _slots
            .Find(slot =>
                slot.StationId == stationId &&
                slot.IsActive &&
                slot.ReservedBookings > 0)
            .AnyAsync();
    }

    public async Task<bool> DeactivateAsync(string id)
    {
        var hasReservations = await HasActiveReservationsAsync(id);

        if (hasReservations)
        {
            return false;
        }

        var update = Builders<SolarStation>.Update
            .Set(station => station.IsActive, false)
            .Set(station => station.UpdatedAtUtc, DateTime.UtcNow);

        var result = await _stations.UpdateOneAsync(
            station => station.Id == id,
            update);

        return result.ModifiedCount > 0;
    }

    public async Task<List<EnergyBookingSlot>> GetAvailableSlotsAsync(
        string stationId,
        DateTime dateUtc)
    {
        var start = dateUtc.Date;
        var end = start.AddDays(1);

        return await _slots.Find(slot =>
                slot.StationId == stationId &&
                slot.IsActive &&
                slot.StartTimeUtc >= start &&
                slot.StartTimeUtc < end &&
                slot.ReservedBookings < slot.MaximumBookings)
            .SortBy(slot => slot.StartTimeUtc)
            .ToListAsync();
    }

    public async Task<List<StationSchedule>> GetSchedulesAsync(string stationId)
    {
        return await _schedules
            .Find(schedule => schedule.StationId == stationId)
            .SortBy(schedule => schedule.Day)
            .ToListAsync();
    }

    public async Task<StationSchedule?> GetScheduleByIdAsync(string scheduleId)
    {
        return await _schedules
            .Find(schedule => schedule.Id == scheduleId)
            .FirstOrDefaultAsync();
    }

    public async Task<StationSchedule> CreateScheduleAsync(StationSchedule schedule)
    {
        await _schedules.InsertOneAsync(schedule);
        return schedule;
    }

    public async Task<bool> UpdateScheduleAsync(
        string scheduleId,
        StationSchedule schedule)
    {
        var result = await _schedules.ReplaceOneAsync(
            existing => existing.Id == scheduleId,
            schedule);

        return result.ModifiedCount > 0;
    }
}
