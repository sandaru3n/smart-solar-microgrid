using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SolarGrid.Api.Models;
using SolarGrid.Api.Services;

namespace SolarGrid.Api.Controllers;

[ApiController]
[Route("api/deactivation-requests")]
public class DeactivationRequestController : ControllerBase
{
    private readonly DeactivationRequestService _requestService;

    public DeactivationRequestController(DeactivationRequestService requestService)
    {
        _requestService = requestService;
    }

    [Authorize(Roles = "PROSUMER")]
    [HttpPost("users/{nic}")]
    public async Task<IActionResult> CreateRequest(string nic, [FromBody] CreateDeactivationRequestDto dto)
    {
        var userIdClaim = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
        if (userIdClaim != nic)
        {
            return Forbid();
        }

        var result = await _requestService.CreateRequestAsync(nic, dto.Reason);
        if (!result.Success)
            return Conflict(new { message = result.Message });

        return Ok(new { message = result.Message });
    }

    [Authorize(Roles = "BACKOFFICE")]
    [HttpGet("pending")]
    public async Task<IActionResult> GetPendingRequests()
    {
        var requests = await _requestService.GetPendingRequestsAsync();
        return Ok(requests);
    }

    [Authorize(Roles = "BACKOFFICE")]
    [HttpPatch("{id}/approve")]
    public async Task<IActionResult> ApproveRequest(string id)
    {
        var reviewerNic = User.FindFirst(ClaimTypes.NameIdentifier)?.Value ?? "SYSTEM";
        var result = await _requestService.ApproveRequestAsync(id, reviewerNic);
        
        if (!result.Success)
            return BadRequest(new { message = result.Message });

        return Ok(new { message = result.Message });
    }

    [Authorize(Roles = "BACKOFFICE")]
    [HttpPatch("{id}/reject")]
    public async Task<IActionResult> RejectRequest(string id, [FromBody] RejectDeactivationRequestDto dto)
    {
        var reviewerNic = User.FindFirst(ClaimTypes.NameIdentifier)?.Value ?? "SYSTEM";
        var result = await _requestService.RejectRequestAsync(id, dto.Reason, reviewerNic);
        
        if (!result.Success)
            return BadRequest(new { message = result.Message });

        return Ok(new { message = result.Message });
    }
}
