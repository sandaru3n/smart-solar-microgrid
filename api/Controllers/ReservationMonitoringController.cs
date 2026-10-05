using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SolarGrid.Api.DTOs;
using SolarGrid.Api.Services;

namespace SolarGrid.Api.Controllers;

/// <summary>
/// Member 4 IT23250574 endpoints on /api/reservations. 
/// GET /api/reservations/{id} is Member 3's endpoint and is reused, not duplicated.
/// Role and ownership checks are done in ReservationMonitoringService.
/// </summary>
[ApiController]
[Authorize]
[Route("api/reservations")]
public class ReservationMonitoringController : ControllerBase
{
    private readonly ReservationMonitoringService _monitoringService;

    public ReservationMonitoringController(ReservationMonitoringService monitoringService)
    {
        _monitoringService = monitoringService;
    }

    /// <summary>
    /// GET /api/reservations/summary — pending and approved future counts.
    /// Prosumers get their own counts, staff get operational counts.
    /// </summary>
    [HttpGet("summary")]
    public Task<IActionResult> Summary() =>
        Run(() => _monitoringService.GetSummaryAsync(User));

    /// <summary>
    /// GET /api/reservations/mine — the signed-in prosumer's bookings.
    /// </summary>
    [HttpGet("mine")]
    public Task<IActionResult> Mine([FromQuery] MyReservationsQuery query) =>
        Run(() => _monitoringService.GetMineAsync(User, query));

    /// <summary>
    /// GET /api/reservations/desk — booking list for the web desk.
    /// </summary>
    [AllowAnonymous]
    [HttpGet("desk")]
    public Task<IActionResult> ListForDesk([FromQuery] ReservationListQuery query) =>
        Run(() => _monitoringService.GetAllAsync(DeskActor.Create(), query));

    /// <summary>
    /// GET /api/reservations — all reservations for staff, with filters and paging.
    /// </summary>
    [HttpGet]
    public Task<IActionResult> List([FromQuery] ReservationListQuery query) =>
        Run(() => _monitoringService.GetAllAsync(User, query));

    /// <summary>
    /// PATCH /api/reservations/desk/{id}/approve — approve a prosumer booking from the web desk.
    /// </summary>
    [AllowAnonymous]
    [HttpPatch("desk/{id}/approve")]
    public Task<IActionResult> ApproveFromDesk(string id, [FromBody] ReservationActionRequest? body) =>
        Run(() => _monitoringService.ApproveAsync(id, DeskActor.Create(), body?.Version));

    /// <summary>
    /// PATCH /api/reservations/desk/{id}/reject — reject a prosumer booking from the web desk.
    /// </summary>
    [AllowAnonymous]
    [HttpPatch("desk/{id}/reject")]
    public Task<IActionResult> RejectFromDesk(string id, [FromBody] ReservationActionRequest? body) =>
        Run(() => _monitoringService.RejectAsync(id, DeskActor.Create(), body?.Version));

    /// <summary>
    /// PATCH /api/reservations/{id}/approve — approve a pending reservation.
    /// </summary>
    [HttpPatch("{id}/approve")]
    public Task<IActionResult> Approve(string id, [FromBody] ReservationActionRequest? body) =>
        Run(() => _monitoringService.ApproveAsync(id, User, body?.Version));

    /// <summary>
    /// PATCH /api/reservations/{id}/reject — reject a pending reservation and release capacity.
    /// </summary>
    [HttpPatch("{id}/reject")]
    public Task<IActionResult> Reject(string id, [FromBody] ReservationActionRequest? body) =>
        Run(() => _monitoringService.RejectAsync(id, User, body?.Version));

    /// <summary>
    /// GET /api/reservations/{id}/qr — signed QR payload for the owner's approved reservation.
    /// </summary>
    [HttpGet("{id}/qr")]
    public Task<IActionResult> GetQr(string id) =>
        Run(() => _monitoringService.GetQrAsync(id, User));

    /// <summary>
    /// POST /api/reservations/verify-qr — check a scanned QR code. Changes no data.
    /// </summary>
    [HttpPost("verify-qr")]
    public Task<IActionResult> VerifyQr([FromBody] VerifyQrRequest? body) =>
        Run(() => _monitoringService.VerifyQrAsync(User, body?.Payload));

    /// <summary>
    /// PATCH /api/reservations/{id}/complete — record a completed energy transfer.
    /// </summary>
    [HttpPatch("{id}/complete")]
    public Task<IActionResult> Complete(string id, [FromBody] ReservationActionRequest? body) =>
        Run(() => _monitoringService.CompleteAsync(id, User, body?.Version));

    private async Task<IActionResult> Run<T>(Func<Task<T>> action)
    {
        try
        {
            return Ok(await action());
        }
        catch (ReservationException ex)
        {
            return Map(ex);
        }
        catch (QrNotConfiguredException ex)
        {
            return StatusCode(
                StatusCodes.Status500InternalServerError,
                new { message = ex.Message });
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
