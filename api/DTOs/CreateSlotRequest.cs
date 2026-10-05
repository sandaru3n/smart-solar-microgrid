/**
 * File: CreateSlotRequest.cs
 * Purpose: Request body for creating an energy booking slot at a solar station.
 * Author: M.S.N. Peiris it23201132
 * Date: 2026
 */
namespace SolarGrid.Api.DTOs;

public class CreateSlotRequest
{
    public DateTime StartTimeUtc { get; set; }
    public DateTime EndTimeUtc { get; set; }
    public int MaximumBookings { get; set; }
}