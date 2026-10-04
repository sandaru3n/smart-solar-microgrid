using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SolarGrid.Api.Models;
using SolarGrid.Api.Services;

namespace SolarGrid.Api.Controllers;

[ApiController]
[Route("api/users")]
public class UserController : ControllerBase
{
    private readonly UserService _userService;

    public UserController(UserService userService)
    {
        _userService = userService;
    }

    [Authorize(Roles = "BACKOFFICE")]
    [HttpGet]
    public async Task<IActionResult> GetAllUsers()
    {
        var users = await _userService.GetAllAsync();
        return Ok(users.Select(UserResponse.FromUser));
    }

    [AllowAnonymous]
    [HttpGet("prosumers")]
    public async Task<IActionResult> ListProsumers()
    {
        var users = await _userService.GetAllAsync();

        var prosumers = users
            .Where(user => user.Role == Role.PROSUMER)
            .Select(user => new
            {
                nic = user.NIC,
                name = user.Name,
                address = user.Address,
                accountStatus = user.AccountStatus.ToString()
            });

        return Ok(prosumers);
    }

    [AllowAnonymous]
    [HttpGet("{nic}/booking")]
    public async Task<IActionResult> GetBookingProfile(string nic)
    {
        if (string.IsNullOrWhiteSpace(nic))
        {
            return BadRequest(new { message = "NIC is required." });
        }

        var user = await _userService.GetByNICAsync(nic.Trim());

        if (user is null)
        {
            return NotFound(new { message = "Prosumer account not found." });
        }

        if (user.Role != Role.PROSUMER)
        {
            return BadRequest(new
            {
                message = "Reservations can only be created for prosumer accounts."
            });
        }

        return Ok(new
        {
            nic = user.NIC,
            name = user.Name,
            address = user.Address,
            accountStatus = user.AccountStatus.ToString()
        });
    }

    [Authorize(Roles = "BACKOFFICE")]
[HttpPost]
public async Task<IActionResult> CreateUser([FromBody] CreateUserRequest request)
{
    // Validate the required Prosumer information.
    if (string.IsNullOrWhiteSpace(request.NIC) ||
        string.IsNullOrWhiteSpace(request.Name) ||
        string.IsNullOrWhiteSpace(request.Email) ||
        string.IsNullOrWhiteSpace(request.Password))
    {
        return BadRequest(new
        {
            message = "NIC, name, email and password are required."
        });
    }

    // This endpoint is only for creating Prosumer accounts.
    if (request.Role != Role.PROSUMER)
    {
        return BadRequest(new
        {
            message = "This endpoint can only create Prosumer accounts."
        });
    }

    // Create the Prosumer user object.
    var user = new User
    {
        NIC = request.NIC,
        Name = request.Name,
        Email = request.Email,
        Phone = request.Phone,
        Address = request.Address,
        Role = Role.PROSUMER,
        AccountStatus = AccountStatus.PENDING
    };

    // Create the Prosumer through the service layer.
    var result = await _userService.CreateUserAsync(
        user,
        request.Password
    );

    // Return conflict if the NIC already exists.
    if (!result.Success)
    {
        return Conflict(new
        {
            message = result.Message
        });
    }

    // Return the newly created Prosumer.
    return CreatedAtAction(
        nameof(GetUser),
        new { nic = user.NIC },
        result.User
    );
}

    [Authorize(Roles = "BACKOFFICE")]
[HttpPost("staff")]
public async Task<IActionResult> CreateStaff(
    [FromBody] CreateStaffRequest request)
{
    // Validate the required staff information.
    if (string.IsNullOrWhiteSpace(request.NIC) ||
        string.IsNullOrWhiteSpace(request.Name) ||
        string.IsNullOrWhiteSpace(request.Email) ||
        string.IsNullOrWhiteSpace(request.Password))
    {
        return BadRequest(new
        {
            message = "NIC, name, email and password are required."
        });
    }

    // Create a user object from the request.
    var user = new User
    {
        NIC = request.NIC,
        Name = request.Name,
        Email = request.Email,
        Phone = request.Phone,
        Address = request.Address,
        Role = request.Role
    };

    // Create the staff account through the service layer.
    var result = await _userService.CreateStaffAsync(
        user,
        request.Password
    );

    // Return conflict when the NIC already exists or the role is invalid.
    if (!result.Success)
    {
        return Conflict(new
        {
            message = result.Message
        });
    }

    // Return the newly created staff account.
    return CreatedAtAction(
        nameof(GetUser),
        new { nic = user.NIC },
        result.User
    );
}

    [Authorize]
[HttpGet("{nic}")]
public async Task<IActionResult> GetUser(string nic)
{
    // Get the NIC of the currently authenticated user from the JWT token.
    var loggedInNIC = User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;

    // Check whether the logged-in user is a Backoffice user.
    var isBackoffice = User.IsInRole("BACKOFFICE");

    // Non-Backoffice users can only access their own profile.
    if (!isBackoffice && loggedInNIC != nic)
    {
        return Forbid();
    }

    // Retrieve the requested user from the database.
    var user = await _userService.GetByNICAsync(nic);

    // Return 404 if the requested user does not exist.
    if (user == null)
    {
        return NotFound(new { message = "User not found." });
    }

    // Do not expose the password hash in the API response.
    user.PasswordHash = string.Empty;

    return Ok(user);
}


[Authorize(Roles = "BACKOFFICE")]
[HttpGet("pending")]
public async Task<IActionResult> GetPendingUsers()
{
    // Retrieve all accounts waiting for Backoffice activation.
    var users = await _userService.GetPendingUsersAsync();

    // Hide password hashes before returning the users.
    foreach (var user in users)
    {
        user.PasswordHash = string.Empty;
    }

    return Ok(users);
}


[Authorize(Roles = "BACKOFFICE")]
[HttpPatch("{nic}/deactivate")]
public async Task<IActionResult> DeactivateUser(string nic)
{
    var loggedInNIC = User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;
    if (loggedInNIC == nic)
    {
        return BadRequest(new { message = "You cannot deactivate your own account." });
    }

    // Deactivate the selected user account.
    var result = await _userService.DeactivateUserAsync(nic);

    // Return 404 if the user does not exist.
    if (!result.Success && result.Message == "User not found.")
    {
        return NotFound(new { message = result.Message });
    }

    // Return a successful response when the account is deactivated.
    return Ok(new { message = result.Message });
}

[Authorize(Roles = "BACKOFFICE")]
[HttpPatch("{nic}/reactivate")]
public async Task<IActionResult> ReactivateUser(string nic)
{
    // Reactivate the selected user account.
    var result = await _userService.ReactivateUserAsync(nic);

    // Return 404 if the user does not exist.
    if (!result.Success && result.Message == "User not found.")
    {
        return NotFound(new { message = result.Message });
    }

    // Return a successful response when the account is reactivated.
    return Ok(new { message = result.Message });
}

[Authorize(Roles = "BACKOFFICE")]
[HttpPatch("{nic}/activate")]
public async Task<IActionResult> ActivateUser(string nic)
{
    // Activate the selected pending user account.
    var result = await _userService.ActivateUserAsync(nic);

    if (!result.Success)
    {
        if (result.Message == "User not found.")
        {
            return NotFound(new { message = result.Message });
        }

        return BadRequest(new { message = result.Message });
    }

    return Ok(new { message = result.Message });
}

public class RejectRequest { public string Reason { get; set; } = ""; }

[Authorize(Roles = "BACKOFFICE")]
[HttpPatch("{nic}/reject")]
public async Task<IActionResult> RejectRegistration(string nic, [FromBody] RejectRequest request)
{
    var result = await _userService.RejectRegistrationAsync(nic, request.Reason);
    if (!result.Success)
    {
        return BadRequest(new { message = result.Message });
    }
    return Ok(new { message = result.Message });
}

[Authorize(Roles = "BACKOFFICE")]
[HttpDelete("{nic}")]
public async Task<IActionResult> DeleteUser(string nic)
{
    var targetUser = await _userService.GetByNICAsync(nic);
    if (targetUser == null) 
    {
        return NotFound(new { message = "User not found." });
    }

    if (targetUser.Role == Role.BACKOFFICE)
    {
        return Forbid();
    }

    var result = await _userService.DeleteUserAsync(nic);
    if (!result.Success) 
    {
        return BadRequest(new { message = result.Message });
    }
    return Ok(new { message = result.Message });
}

    [Authorize(Roles = "BACKOFFICE")]
    [HttpGet("backoffice-test")]
    public IActionResult BackofficeTest()
{
    return Ok(new
    {
        message = "You have Backoffice access."
    });
}

    [Authorize(Roles = "BACKOFFICE")]
    [HttpGet("staff")]
    public async Task<IActionResult> GetStaff()
    {
        var users = await _userService.GetAllAsync();
        var staff = users.Where(u => u.Role == Role.BACKOFFICE || u.Role == Role.GRID_OPERATOR)
                         .Select(UserResponse.FromUser)
                         .ToList();
        return Ok(staff);
    }

    [Authorize]
    [HttpPatch("{nic}/profile")]
    public async Task<IActionResult> UpdateProfile(string nic, [FromBody] UpdateProfileRequest request)
    {
        var loggedInNIC = User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;

        if (loggedInNIC != nic)
        {
            return Forbid();
        }

        var result = await _userService.UpdateProfileAsync(nic, request);

        if (!result.Success)
        {
            return NotFound(new { message = result.Message });
        }

        return Ok(result.User);
    }

    [Authorize]
    [HttpPatch("{nic}/deactivation-request")]
    public async Task<IActionResult> RequestDeactivation(string nic)
    {
        var loggedInNIC = User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;

        if (loggedInNIC != nic)
        {
            return Forbid();
        }

        var result = await _userService.RequestDeactivationAsync(nic);

        if (!result.Success)
        {
            if (result.Message == "User not found.")
                return NotFound(new { message = result.Message });
            else
                return BadRequest(new { message = result.Message });
        }

        return Ok(new { message = result.Message });
    }

    [Authorize(Roles = "BACKOFFICE")]
    [HttpGet("{nic}/nic-document")]
    public async Task<IActionResult> GetNicDocument(string nic)
    {
        var url = await _userService.GetNicImageUrlAsync(nic);
        if (string.IsNullOrEmpty(url))
        {
            return NotFound(new { message = "NIC image URL not found for this user." });
        }
        return Redirect(url);
    }

    [Authorize(Roles = "BACKOFFICE")]
    [HttpPost("{nic}/validate-nic-ai")]
    public async Task<IActionResult> ValidateNicWithAi(string nic)
    {
        var result = await _userService.ValidateNicWithAiAsync(nic);
        if (!result.Success)
        {
            return BadRequest(new { message = result.Message });
        }
        return Ok(new { message = result.Message, aiResponse = result.AiResponse });
    }

    [Authorize]
    [HttpPost("{nic}/request-email-change")]
    public async Task<IActionResult> RequestEmailChange(string nic, [FromBody] EmailChangeRequest request)
    {
        var loggedInNIC = User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;
        if (loggedInNIC != nic) return Forbid();

        var result = await _userService.RequestEmailChangeAsync(nic, request.NewEmail);
        if (!result.Success) return BadRequest(new { message = result.Message });
        
        return Ok(new { message = result.Message });
    }

    [Authorize]
    [HttpPost("{nic}/verify-email-change")]
    public async Task<IActionResult> VerifyEmailChange(string nic, [FromBody] EmailVerifyRequest request)
    {
        var loggedInNIC = User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;
        if (loggedInNIC != nic) return Forbid();

        var result = await _userService.VerifyEmailChangeAsync(nic, request.Otp);
        if (!result.Success) return BadRequest(new { message = result.Message });
        
        return Ok(new { message = result.Message });
    }
}

public class EmailChangeRequest
{
    public string NewEmail { get; set; } = string.Empty;
}

public class EmailVerifyRequest
{
    public string Otp { get; set; } = string.Empty;
}