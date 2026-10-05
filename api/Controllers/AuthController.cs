/*
 * File: AuthController.cs
 * Description: Controller for handling authentication, login, registration, OTP verification, and password resets.
 * Author: IT23163904_WVADK Chamara
 */
using Microsoft.AspNetCore.Mvc;
using SolarGrid.Api.Models;
using SolarGrid.Api.Services;

namespace SolarGrid.Api.Controllers;

[ApiController]
[Route("api/auth")]
public class AuthController : ControllerBase
{
    private readonly UserService _userService;

    // Initializes the AuthController with the necessary UserService.
    public AuthController(UserService userService)
    {
        _userService = userService;
    }

    // Handles user login requests, returning a JWT token upon successful authentication.
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



// Starts the prosumer registration process, validating the NIC and sending an OTP to the provided email.
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

// Verifies the OTP sent during registration and finalizes account creation if valid.
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

// Resends the OTP to the user's email if the previous one expired or was not received.
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

    // Initiates the password reset process by generating and emailing an OTP to the user.
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

    // Resets the user's password if the provided OTP is valid.
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