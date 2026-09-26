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

    public ReservationsController(ReservationService reservationService)
    {
        _reservationService = reservationService;
    }

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
