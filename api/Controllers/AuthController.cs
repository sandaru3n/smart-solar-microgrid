using Microsoft.AspNetCore.Mvc;
using SolarGrid.Api.Models;
using SolarGrid.Api.Services;

namespace SolarGrid.Api.Controllers;

[ApiController]
[Route("api/auth")]
public class AuthController : ControllerBase
{
    private readonly UserService _userService;

    public AuthController(UserService userService)
    {
        _userService = userService;
    }

    [HttpPost("login")]
    public async Task<IActionResult> Login(LoginRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.NIC) ||
            string.IsNullOrWhiteSpace(request.Password))
        {
            return BadRequest(new
            {
                message = "NIC and password are required."
            });
        }

        var result = await _userService.LoginAsync(
            request.NIC,
            request.Password
        );

        if (!result.Success)
        {
            return Unauthorized(new
            {
                message = result.Message
            });
        }

        return Ok(result.Response);
    }
}