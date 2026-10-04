using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

using MongoDB.Bson.Serialization.Attributes;

namespace SolarGrid.Api.Models;

[BsonIgnoreExtraElements]
public class PendingRegistration
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = string.Empty;

    public string RegistrationId { get; set; } = string.Empty;

    public string NIC { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public string Phone { get; set; } = string.Empty;
    public string Address { get; set; } = string.Empty;
    public string PasswordHash { get; set; } = string.Empty;

    public string OtpHash { get; set; } = string.Empty;
    public DateTime OtpExpiry { get; set; }
    public int OtpAttempts { get; set; } = 0;

    public string NicImageUrl { get; set; } = string.Empty;

    public bool IsEmailVerified { get; set; } = false;

    public DateTime CreatedDate { get; set; }
    public DateTime UpdatedDate { get; set; }
}
