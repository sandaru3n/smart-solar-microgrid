using MongoDB.Bson.Serialization.Attributes;

namespace SolarGrid.Api.Models;

[BsonIgnoreExtraElements]
public class User
{
    [BsonId]
    public string NIC { get; set; } = string.Empty;

    public string Name { get; set; } = string.Empty;

    public string Email { get; set; } = string.Empty;

    public string Phone { get; set; } = string.Empty;

    public string Address { get; set; } = string.Empty;

    public string PasswordHash { get; set; } = string.Empty;

    public Role Role { get; set; }

    public AccountStatus AccountStatus { get; set; }

    public DateTime CreatedDate { get; set; }

    public DateTime UpdatedDate { get; set; }

    public bool EmailVerified { get; set; }

    public string NicImageUrl { get; set; } = string.Empty;

    public string? ProfilePicUrl { get; set; }

    public string NicVerificationStatus { get; set; } = "PENDING_REVIEW";

    public string? ResetPasswordOtp { get; set; }

    public DateTime? ResetPasswordOtpExpiry { get; set; }
    
    public string? PendingNewEmail { get; set; }
}