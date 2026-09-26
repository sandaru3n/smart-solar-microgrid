using Microsoft.AspNetCore.Mvc;
using MongoDB.Driver;
using SolarGrid.Api.DTOs;
using SolarGrid.Api.Models;
using SolarGrid.Api.Services;

namespace SolarGrid.Api.Controllers;

[ApiController]
[Route("api/stations")]
public class StationsController : ControllerBase
{
    private readonly IMongoCollection<SolarStation> _stations;
    private readonly StationService _service;

    public StationsController(
        IMongoDatabase database,
        StationService service)
    {
        _stations = database.GetCollection<SolarStation>(
            "SolarStationInfo");
        _service = service;
    }

    // Creates a new solar station.
    [HttpPost]
    public async Task<IActionResult> CreateStation(
        [FromBody] SolarStation station)
    {
        var validationError = ValidateStationInput(station);
        if (validationError is not null)
        {
            return validationError;
        }

        // Server controls these values.
        station.Id = string.Empty;
        station.IsActive = true;
        station.CreatedAtUtc = DateTime.UtcNow;
        station.UpdatedAtUtc = DateTime.UtcNow;

        await _stations.InsertOneAsync(station);

        return CreatedAtAction(
            nameof(GetStation),
            new { id = station.Id },
            station);
    }

    // Gets all active stations.
    [HttpGet]
    public async Task<IActionResult> GetStations()
    {
        var stations = await _stations
            .Find(station => station.IsActive)
            .ToListAsync();

        return Ok(stations);
    }

    // Finds active stations within a radius.
    [HttpGet("nearby")]
    public async Task<IActionResult> GetNearbyStations(
        [FromQuery] double latitude,
        [FromQuery] double longitude,
        [FromQuery] double radiusKm = 10)
    {
        var stations = await _service.GetActiveStationsAsync();

        var nearbyStations = stations
            .Select(station => new
            {
                Station = station,
                DistanceKm = DistanceCalculator.Kilometres(
                    latitude,
                    longitude,
                    station.Latitude,
                    station.Longitude)
            })
            .Where(item => item.DistanceKm <= radiusKm)
            .OrderBy(item => item.DistanceKm)
            .Select(item => new
            {
                item.Station,
                item.DistanceKm
            });

        return Ok(nearbyStations);
    }

    // Gets a station by ID.
    [HttpGet("{id}")]
    public async Task<IActionResult> GetStation(string id)
    {
        var station = await _stations
            .Find(item => item.Id == id)
            .FirstOrDefaultAsync();

        if (station is null)
        {
            return NotFound(new
            {
                message = "Station not found."
            });
        }

        return Ok(station);
    }

    // Gets available booking slots for a station on a given UTC date.
    [HttpGet("{id}/slots")]
    public async Task<IActionResult> GetAvailableSlots(
        string id,
        [FromQuery] DateTime dateUtc)
    {
        var station = await _service.GetByIdAsync(id);

        if (station is null)
        {
            return NotFound(new
            {
                message = "Station not found."
            });
        }

        var slots = await _service.GetAvailableSlotsAsync(id, dateUtc);
        return Ok(slots);
    }

    // Gets weekly operating schedules for a station.
    [HttpGet("{id}/schedules")]
    public async Task<IActionResult> GetSchedules(string id)
    {
        var station = await _service.GetByIdAsync(id);

        if (station is null)
        {
            return NotFound(new
            {
                message = "Station not found."
            });
        }

        var schedules = await _service.GetSchedulesAsync(id);
        return Ok(schedules);
    }

    // Creates an operating schedule entry for a station.
    [HttpPost("{id}/schedules")]
    public async Task<IActionResult> CreateSchedule(
        string id,
        [FromBody] CreateScheduleRequest request)
    {
        var station = await _service.GetByIdAsync(id);

        if (station is null)
        {
            return NotFound(new
            {
                message = "Station not found."
            });
        }

        if (request.ClosingTime <= request.OpeningTime)
        {
            return BadRequest(new
            {
                message = "Closing time must be after opening time."
            });
        }

        var schedule = new StationSchedule
        {
            StationId = id,
            Day = request.Day,
            OpeningTime = request.OpeningTime,
            ClosingTime = request.ClosingTime,
            IsAvailable = request.IsAvailable
        };

        await _service.CreateScheduleAsync(schedule);

        return CreatedAtAction(
            nameof(GetSchedules),
            new { id },
            schedule);
    }

    // Updates an operating schedule entry for a station.
    [HttpPut("{id}/schedules/{scheduleId}")]
    public async Task<IActionResult> UpdateSchedule(
        string id,
        string scheduleId,
        [FromBody] CreateScheduleRequest request)
    {
        var station = await _service.GetByIdAsync(id);

        if (station is null)
        {
            return NotFound(new
            {
                message = "Station not found."
            });
        }

        var existing = await _service.GetScheduleByIdAsync(scheduleId);

        if (existing is null || existing.StationId != id)
        {
            return NotFound(new
            {
                message = "Schedule not found."
            });
        }

        if (request.ClosingTime <= request.OpeningTime)
        {
            return BadRequest(new
            {
                message = "Closing time must be after opening time."
            });
        }

        existing.Day = request.Day;
        existing.OpeningTime = request.OpeningTime;
        existing.ClosingTime = request.ClosingTime;
        existing.IsAvailable = request.IsAvailable;

        await _service.UpdateScheduleAsync(scheduleId, existing);

        return Ok(existing);
    }

    // Updates station information.
    [HttpPut("{id}")]
    public async Task<IActionResult> UpdateStation(
        string id,
        [FromBody] SolarStation station)
    {
        var existingStation = await _stations
            .Find(item => item.Id == id)
            .FirstOrDefaultAsync();

        if (existingStation is null)
        {
            return NotFound(new
            {
                message = "Station not found."
            });
        }

        var validationError = ValidateStationInput(station);
        if (validationError is not null)
        {
            return validationError;
        }

        existingStation.Name = station.Name.Trim();
        existingStation.Address = station.Address.Trim();
        existingStation.Latitude = station.Latitude;
        existingStation.Longitude = station.Longitude;
        existingStation.CapacityKw = station.CapacityKw;
        existingStation.BatteryStorageSlots =
            station.BatteryStorageSlots;
        existingStation.UpdatedAtUtc = DateTime.UtcNow;

        await _stations.ReplaceOneAsync(
            item => item.Id == id,
            existingStation);

        return Ok(existingStation);
    }

    // Deactivates a station when no active reservations exist.
    [HttpPatch("{id}/deactivate")]
    public async Task<IActionResult> DeactivateStation(string id)
    {
        var station = await _stations
            .Find(item => item.Id == id)
            .FirstOrDefaultAsync();

        if (station is null)
        {
            return NotFound(new
            {
                message = "Station not found."
            });
        }

        var activeReservationsExist =
            await _service.HasActiveReservationsAsync(id);

        if (activeReservationsExist)
        {
            return Conflict(new
            {
                message =
                    "Station cannot be deactivated because active reservations exist."
            });
        }

        var update = Builders<SolarStation>.Update
            .Set(item => item.IsActive, false)
            .Set(item => item.UpdatedAtUtc, DateTime.UtcNow);

        await _stations.UpdateOneAsync(
            item => item.Id == id,
            update);

        return Ok(new
        {
            message = "Station deactivated successfully."
        });
    }

    // Validates shared station create/update input rules.
    private BadRequestObjectResult? ValidateStationInput(SolarStation station)
    {
        if (string.IsNullOrWhiteSpace(station.Name))
        {
            return BadRequest(new
            {
                message = "Station name is required."
            });
        }

        if (station.Latitude < -90 || station.Latitude > 90)
        {
            return BadRequest(new
            {
                message = "Invalid latitude."
            });
        }

        if (station.Longitude < -180 || station.Longitude > 180)
        {
            return BadRequest(new
            {
                message = "Invalid longitude."
            });
        }

        if (station.CapacityKw <= 0)
        {
            return BadRequest(new
            {
                message = "Capacity must be greater than zero."
            });
        }

        return null;
    }
}
