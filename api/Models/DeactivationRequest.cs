/*
 * File: DeactivationRequest.cs
 * Description: Represents a Prosumer's formal request to deactivate their account, pending Backoffice review.
 * Author: IT23163904_WVADK Chamara
 */
using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace SolarGrid.Api.Models;

public class DeactivationRequest
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string? Id { get; set; }

    public string ProsumerNic { get; set; } = string.Empty;
    // Member 1: Captures the reason for deactivation submitted by the Prosumer.
    public string Reason { get; set; } = string.Empty;
    public DeactivationRequestStatus Status { get; set; } = DeactivationRequestStatus.PENDING;
    public DateTime RequestedAt { get; set; } = DateTime.UtcNow;
    public DateTime? ReviewedAt { get; set; }
    public string? ReviewerNic { get; set; }
    public string? RejectionReason { get; set; }
}
