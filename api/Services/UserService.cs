using SolarGrid.Api.Models;
using SolarGrid.Api.Repositories;

namespace SolarGrid.Api.Services;

public class UserService
{
    private readonly UserRepository _userRepository;
    private readonly JwtService _jwtService;

    public UserService(UserRepository userRepository, JwtService jwtService)
    {
        _userRepository = userRepository;
        _jwtService = jwtService;
    }

    public async Task<User?> GetByNICAsync(string nic)
    {
        return await _userRepository.GetByNICAsync(nic);
    }

    public async Task<List<User>> GetAllAsync()
    {
        return await _userRepository.GetAllAsync();
    }

    public async Task<(bool Success, string Message, User? User)> CreateUserAsync(
        User user,
        string password)
    {
        var existingUser = await _userRepository.GetByNICAsync(user.NIC);

        if (existingUser != null)
        {
            return (false, "A user with this NIC already exists.", null);
        }

        if (string.IsNullOrWhiteSpace(password))
        {
            return (false, "Password is required.", null);
        }

        user.PasswordHash = BCrypt.Net.BCrypt.HashPassword(password);

        user.CreatedDate = DateTime.UtcNow;
        user.UpdatedDate = DateTime.UtcNow;

        await _userRepository.CreateAsync(user);

        user.PasswordHash = string.Empty;

        return (true, "User created successfully.", user);
    }

    public async Task<(bool Success, string Message)> UpdateUserAsync(User user)
    {
        var existingUser = await _userRepository.GetByNICAsync(user.NIC);

        if (existingUser == null)
        {
            return (false, "User not found.");
        }

        user.UpdatedDate = DateTime.UtcNow;

        await _userRepository.UpdateAsync(user);

        return (true, "User updated successfully.");
    }

    public async Task<(bool Success, string Message)> DeactivateUserAsync(string nic)
    {
        var user = await _userRepository.GetByNICAsync(nic);

        if (user == null)
        {
            return (false, "User not found.");
        }

        user.AccountStatus = AccountStatus.DEACTIVATED;
        user.UpdatedDate = DateTime.UtcNow;

        await _userRepository.UpdateAsync(user);

        return (true, "User deactivated successfully.");
    }

    public async Task<(bool Success, string Message, LoginResponse? Response)> LoginAsync(
    string nic,
    string password)
{
    var user = await _userRepository.GetByNICAsync(nic);

    if (user == null)
    {
        return (false, "Invalid NIC or password.", null);
    }

    if (user.AccountStatus != AccountStatus.ACTIVE)
    {
        return (false, "Your account is not active.", null);
    }

    if (string.IsNullOrWhiteSpace(user.PasswordHash))
    {
        return (false, "Invalid NIC or password.", null);
    }

    var passwordValid = BCrypt.Net.BCrypt.Verify(
        password,
        user.PasswordHash
    );

    if (!passwordValid)
    {
        return (false, "Invalid NIC or password.", null);
    }

    var token = _jwtService.GenerateToken(user);

    var response = new LoginResponse
    {
        Message = "Login successful.",
        Token = token,
        NIC = user.NIC,
        Name = user.Name,
        Role = user.Role.ToString(),
        AccountStatus = user.AccountStatus.ToString()
    };

    return (true, "Login successful.", response);
}
}