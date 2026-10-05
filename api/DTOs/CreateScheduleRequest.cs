/**
 * File: CreateScheduleRequest.cs
 * Purpose: Request body for creating or updating a station weekly operating schedule entry.
 * Author: M.S.N. Peiris it23201132
 * Date: 2026
 */
namespace SolarGrid.Api.DTOs;

public class CreateScheduleRequest
{
    public DayOfWeek Day { get; set; }
    public TimeSpan OpeningTime { get; set; }
    public TimeSpan ClosingTime { get; set; }
    public bool IsAvailable { get; set; } = true;
}
