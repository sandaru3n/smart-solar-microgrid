/**
 * File: StationSchedule.cs
 * Purpose: MongoDB document model representing a station's weekly operating schedule (day, opening and closing times).
 * Author: M.S.N. Peiris it23201132
 * Date: 2026
 */
using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace SolarGrid.Api.Models;

public class StationSchedule
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = string.Empty;

    [BsonRepresentation(BsonType.ObjectId)]
    public string StationId { get; set; } = string.Empty;

    public DayOfWeek Day { get; set; }
    public TimeSpan OpeningTime { get; set; }
    public TimeSpan ClosingTime { get; set; }

    public bool IsAvailable { get; set; } = true;
}