/**
 * File: ReservationsController.cs
 * Purpose: HTTP endpoints for creating, reading, updating and cancelling reservations.
 * Author: M.T.C PEIRIS  it23201200
 * Date: 2026
 */

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SolarGrid.Api.Services;

namespace SolarGrid.Api.Controllers;

[ApiController]
[Authorize]
[Route("api/reservations")]
public class ReservationsController : ControllerBase
{
    private readonly ReservationService _reservationService;

    // Stores the booking service used by every reservation endpoint.
    public ReservationsController(ReservationService reservationService)
    {
        _reservationService = reservationService;
    }

    // Creates a booking from the web desk on behalf of a prosumer.
    /// <summary>
    /// POST /api/reservations/desk — booking from the web desk, which has no login yet.
    /// Uses the same rules as a Backoffice user creating a reservation for a prosumer.
    /// </summary>
    [AllowAnonymous]
    [HttpPost("desk")]
    public async Task<IActionResult> CreateFromDesk([FromBody] ReservationWriteModel? body)
    {
        try
        {
            var summary = await _reservationService.CreateFromDeskAsync(
                body?.SlotId ?? string.Empty,
                body?.StationId,
                body?.ProsumerId);

            var id = summary.GetType().GetProperty("reservationId")?.GetValue(summary)?.ToString();

            return CreatedAtAction(nameof(GetById), new { id }, summary);
        }
        catch (ReservationException ex)
        {
            return Map(ex);
        }
    }

    // Creates a booking for the signed-in prosumer, or for a chosen prosumer when staff is signed in.
    /// <summary>
    /// POST /api/reservations — create a booking.
    /// Only SlotId, optional StationId, and optional ProsumerId (staff) are used.
    /// </summary>
    [HttpPost]
    public async Task<IActionResult> Create([FromBody] ReservationWriteModel? body)
    {
        try
        {
            var summary = await _reservationService.CreateAsync(
                User,
                body?.SlotId ?? string.Empty,
                body?.StationId,
                body?.ProsumerId);

            var id = summary.GetType().GetProperty("reservationId")?.GetValue(summary)?.ToString();

            return CreatedAtAction(nameof(GetById), new { id }, summary);
        }
        catch (ReservationException ex)
        {
            return Map(ex);
        }
    }

    // Returns one reservation for the web desk summary and edit screens.
    /// <summary>
    /// GET /api/reservations/desk/{id} — booking details for the web desk.
    /// </summary>
    [AllowAnonymous]
    [HttpGet("desk/{id}")]
    public async Task<IActionResult> GetFromDesk(string id)
    {
        try
        {
            return Ok(await _reservationService.GetByIdAsync(id, DeskActor.Create()));
        }
        catch (ReservationException ex)
        {
            return Map(ex);
        }
    }

    // Changes the slot of a reservation from the web desk.
    /// <summary>
    /// PUT /api/reservations/desk/{id} — change a booking from the web desk.
    /// </summary>
    [AllowAnonymous]
    [HttpPut("desk/{id}")]
    public async Task<IActionResult> UpdateFromDesk(string id, [FromBody] ReservationWriteModel? body)
    {
        try
        {
            var summary = await _reservationService.UpdateAsync(
                id,
                DeskActor.Create(),
                body?.SlotId ?? string.Empty,
                body?.StationId,
                body?.Version);

            return Ok(summary);
        }
        catch (ReservationException ex)
        {
            return Map(ex);
        }
    }

    // Cancels a reservation from the web desk and releases its slot space.
    /// <summary>
    /// PATCH /api/reservations/desk/{id}/cancel — cancel a booking from the web desk.
    /// </summary>
    [AllowAnonymous]
    [HttpPatch("desk/{id}/cancel")]
    public async Task<IActionResult> CancelFromDesk(string id, [FromBody] ReservationWriteModel? body)
    {
        try
        {
            var summary = await _reservationService.CancelAsync(id, DeskActor.Create(), body?.Version);
            return Ok(summary);
        }
        catch (ReservationException ex)
        {
            return Map(ex);
        }
    }

    // Returns one reservation the caller is allowed to see.
    /// <summary>
    /// GET /api/reservations/{id} — booking details for edit/summary screens.
    /// </summary>
    [HttpGet("{id}")]
    public async Task<IActionResult> GetById(string id)
    {
        try
        {
            return Ok(await _reservationService.GetByIdAsync(id, User));
        }
        catch (ReservationException ex)
        {
            return Map(ex);
        }
    }

    // Changes the slot of an eligible reservation.
    /// <summary>
    /// PUT /api/reservations/{id} — change slot on an eligible booking.
    /// Only SlotId, optional StationId, and optional Version are used.
    /// </summary>
    [HttpPut("{id}")]
    public async Task<IActionResult> Update(string id, [FromBody] ReservationWriteModel? body)
    {
        try
        {
            var summary = await _reservationService.UpdateAsync(
                id,
                User,
                body?.SlotId ?? string.Empty,
                body?.StationId,
                body?.Version);

            return Ok(summary);
        }
        catch (ReservationException ex)
        {
            return Map(ex);
        }
    }

    // Cancels an eligible reservation and releases its slot space.
    /// <summary>
    /// PATCH /api/reservations/{id}/cancel — cancel an eligible booking.
    /// </summary>
    [HttpPatch("{id}/cancel")]
    public async Task<IActionResult> Cancel(string id, [FromBody] ReservationWriteModel? body)
    {
        try
        {
            var summary = await _reservationService.CancelAsync(id, User, body?.Version);
            return Ok(summary);
        }
        catch (ReservationException ex)
        {
            return Map(ex);
        }
    }

    // Maps a booking-rule failure to the matching HTTP status.
    private IActionResult Map(ReservationException ex) => ex.Kind switch
    {
        ReservationErrorKind.Unauthorized => Unauthorized(new { message = ex.Message }),
        ReservationErrorKind.Forbidden => StatusCode(
            StatusCodes.Status403Forbidden,
            new { message = ex.Message }),
        ReservationErrorKind.BadRequest => BadRequest(new { message = ex.Message }),
        ReservationErrorKind.NotFound => NotFound(new { message = ex.Message }),
        ReservationErrorKind.Conflict => Conflict(new { message = ex.Message }),
        _ => StatusCode(
            StatusCodes.Status500InternalServerError,
            new { message = ex.Message })
    };
}

/// <summary>
/// Incoming write fields only. Status, timestamps, capacity, and Id from clients are ignored.
/// </summary>
public class ReservationWriteModel
{
    public string? SlotId { get; set; }
    public string? StationId { get; set; }
    public string? ProsumerId { get; set; }
    public long? Version { get; set; }
}
