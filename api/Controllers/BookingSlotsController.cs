using Microsoft.AspNetCore.Mvc;
using MongoDB.Driver;
using SolarGrid.Api.DTOs;
using SolarGrid.Api.Models;

namespace SolarGrid.Api.Controllers;

[ApiController]
[Route("api/booking-slots")]
public class BookingSlotsController : ControllerBase
{
    private readonly IMongoCollection<EnergyBookingSlot> _slots;
    private readonly IMongoCollection<SolarStation> _stations;

    public BookingSlotsController(IMongoDatabase database)
    {
        _slots = database.GetCollection<EnergyBookingSlot>(
            "EnergyBookingSlots");

        _stations = database.GetCollection<SolarStation>(
            "SolarStationInfo");
    }

    // Creates a booking slot for a station.
    [HttpPost("station/{stationId}")]
    public async Task<IActionResult> CreateSlot(
        string stationId,
        [FromBody] CreateSlotRequest request)
    {
        var station = await _stations
            .Find(item => item.Id == stationId && item.IsActive)
            .FirstOrDefaultAsync();

        if (station is null)
        {
            return NotFound(new { message = "Active station not found." });
        }

        if (request.EndTimeUtc <= request.StartTimeUtc)
        {
            return BadRequest(new
            {
                message = "End time must be after start time."
            });
        }

        if (request.MaximumBookings <= 0)
        {
            return BadRequest(new
            {
                message = "Maximum bookings must be greater than zero."
            });
        }

        var slot = new EnergyBookingSlot
        {
            StationId = stationId,
            StartTimeUtc = request.StartTimeUtc,
            EndTimeUtc = request.EndTimeUtc,
            MaximumBookings = request.MaximumBookings,
            ReservedBookings = 0,
            IsActive = true
        };

        await _slots.InsertOneAsync(slot);

        return CreatedAtAction(
            nameof(GetSlot),
            new { id = slot.Id },
            slot);
    }

    // Gets one slot.
    [HttpGet("{id}")]
    public async Task<IActionResult> GetSlot(string id)
    {
        var slot = await _slots
            .Find(item => item.Id == id)
            .FirstOrDefaultAsync();

        return slot is null ? NotFound() : Ok(slot);
    }
}