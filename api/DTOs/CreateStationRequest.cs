/**
 * File: CreateStationRequest.cs
 * Purpose: Request body for creating a new microgrid solar station.
 * Author: M.S.N. Peiris it23201132
 * Date: 2026
 */
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