namespace SolarGrid.Api.Models;

public class UserResponse
{
    public string NIC { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public string Phone { get; set; } = string.Empty;
    public string Address { get; set; } = string.Empty;
    public string Role { get; set; } = string.Empty;
    public string AccountStatus { get; set; } = string.Empty;
    public DateTime CreatedDate { get; set; }
    public DateTime UpdatedDate { get; set; }
    public string? ProfilePicUrl { get; set; }

    public static UserResponse FromUser(User user)
    {
        return new UserResponse
        {
            NIC = user.NIC,
            Name = user.Name,
            Email = user.Email,
            Phone = user.Phone,
            Address = user.Address,
            Role = user.Role.ToString(),
            AccountStatus = user.AccountStatus.ToString(),
            CreatedDate = user.CreatedDate,
            UpdatedDate = user.UpdatedDate,
            ProfilePicUrl = user.ProfilePicUrl
        };
    }
}
