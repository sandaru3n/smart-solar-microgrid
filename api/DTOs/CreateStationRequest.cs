namespace SolarGrid.Api.DTOs;

public class CreateStationRequest
{
    public string Name { get; set; } = string.Empty;
    public string Address { get; set; } = string.Empty;
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public decimal CapacityKw { get; set; }
    public int BatteryStorageSlots { get; set; }
}