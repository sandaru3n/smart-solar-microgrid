using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace SolarGrid.Api.Models;

/// <summary>
/// Shared with Member 4. Collection name: EnergyReservations.
/// </summary>
public class EnergyReservation
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = string.Empty;

    public string ProsumerId { get; set; } = string.Empty;

    [BsonRepresentation(BsonType.ObjectId)]
    public string StationId { get; set; } = string.Empty;

    [BsonRepresentation(BsonType.ObjectId)]
    public string SlotId { get; set; } = string.Empty;

    public ReservationStatus Status { get; set; }

    public DateTime CreatedAtUtc { get; set; }

    public DateTime UpdatedAtUtc { get; set; }

    public DateTime? CancelledAtUtc { get; set; }

    /// <summary>
    /// Detects concurrent update/cancel on the same reservation.
    /// </summary>
    public long Version { get; set; }
}
