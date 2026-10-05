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
                message = "NIC or email and password are required."
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



[HttpPost("register/start")]
public async Task<IActionResult> StartRegistration([FromBody] RegisterStartRequest request)
{
    if (!ModelState.IsValid)
    {
        return BadRequest(ModelState);
    }

    var result = await _userService.StartRegistrationAsync(request);

    if (!result.Success)
    {
        if (result.Message == "This NIC is already registered.")
            return Conflict(new { message = result.Message });

        return BadRequest(new { message = result.Message });
    }

    return Ok(new
    {
        message = result.Message,
        registrationId = result.RegistrationId
    });
}

[HttpPost("register/verify-otp")]
public async Task<IActionResult> VerifyOtp([FromBody] VerifyOtpRequest request)
{
    if (!ModelState.IsValid)
    {
        return BadRequest(ModelState);
    }

    var result = await _userService.VerifyOtpAsync(request);

    if (!result.Success)
    {
        return BadRequest(new { message = result.Message });
    }

    return Ok(result.User);
}

[HttpPost("register/resend-otp")]
public async Task<IActionResult> ResendOtp([FromBody] ResendOtpRequest request)
{
    if (!ModelState.IsValid)
    {
        return BadRequest(ModelState);
    }

    var result = await _userService.ResendOtpAsync(request);

    if (!result.Success)
    {
        return BadRequest(new { message = result.Message });
    }

    return Ok(new { message = result.Message });
}

    [HttpPost("forgot-password")]
    public async Task<IActionResult> ForgotPassword([FromBody] ForgotPasswordRequest request)
    {
        if (!ModelState.IsValid)
        {
            return BadRequest(ModelState);
        }

        var result = await _userService.ForgotPasswordAsync(request.Email);

        if (!result.Success)
        {
            return BadRequest(new { message = result.Message });
        }

        return Ok(new { message = result.Message });
    }

    [HttpPost("reset-password")]
    public async Task<IActionResult> ResetPassword([FromBody] ResetPasswordRequest request)
    {
        if (!ModelState.IsValid)
        {
            return BadRequest(ModelState);
        }

        var result = await _userService.ResetPasswordAsync(request.Email, request.Otp, request.NewPassword);

        if (!result.Success)
        {
            return BadRequest(new { message = result.Message });
        }

        return Ok(new { message = result.Message });
    }
}