/**
 * File: EnergyReservation.cs
 * Purpose: MongoDB document for one energy-slot reservation.
 * Author: M.T.C PEIRIS  it23201200
 * Date: 2026
 */

using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace SolarGrid.Api.Models;

/// <summary>
/// Shared with Member 4. Collection name: EnergyReservations.
/// Unknown fields are ignored so older and newer builds can share documents.
/// </summary>
[BsonIgnoreExtraElements]
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


    // Member 4 fields. Null until the matching status change happens.
    public DateTime? ApprovedAtUtc { get; set; }

    public DateTime? RejectedAtUtc { get; set; }

    public DateTime? CompletedAtUtc { get; set; }

    /// <summary>
    /// NIC of the Grid Operator who completed the energy transfer. Set once.
    /// </summary>
    public string? CompletedByOperatorId { get; set; }
}
