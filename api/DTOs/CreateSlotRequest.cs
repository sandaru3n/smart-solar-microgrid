namespace SolarGrid.Api.DTOs;

public class CreateSlotRequest
{
    public DateTime StartTimeUtc { get; set; }
    public DateTime EndTimeUtc { get; set; }
    public int MaximumBookings { get; set; }
}