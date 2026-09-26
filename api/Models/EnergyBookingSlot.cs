using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace SolarGrid.Api.Models;

public class EnergyBookingSlot
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = string.Empty;

    [BsonRepresentation(BsonType.ObjectId)]
    public string StationId { get; set; } = string.Empty;

    public DateTime StartTimeUtc { get; set; }
    public DateTime EndTimeUtc { get; set; }

    public int MaximumBookings { get; set; }
    public int ReservedBookings { get; set; }

    public bool IsActive { get; set; } = true;

    [BsonIgnore]
    public int RemainingBookings =>
        Math.Max(0, MaximumBookings - ReservedBookings);
}