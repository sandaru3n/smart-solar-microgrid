namespace SolarGrid.Api.DTOs;

public class CreateScheduleRequest
{
    public DayOfWeek Day { get; set; }
    public TimeSpan OpeningTime { get; set; }
    public TimeSpan ClosingTime { get; set; }
    public bool IsAvailable { get; set; } = true;
}
