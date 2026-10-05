using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace SolarGrid.Api.Models;

public class DeactivationRequest
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string? Id { get; set; }

    public string ProsumerNic { get; set; } = string.Empty;
    public string Reason { get; set; } = string.Empty;
    public DeactivationRequestStatus Status { get; set; } = DeactivationRequestStatus.PENDING;
    public DateTime RequestedAt { get; set; } = DateTime.UtcNow;
    public DateTime? ReviewedAt { get; set; }
    public string? ReviewerNic { get; set; }
    public string? RejectionReason { get; set; }
}
