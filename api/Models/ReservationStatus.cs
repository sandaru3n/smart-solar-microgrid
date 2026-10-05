/**
 * File: ReservationStatus.cs
 * Purpose: Status values stored on an energy reservation.
 * Author: M.T.C PEIRIS  it23201200
 * Date: 2026
 */

namespace SolarGrid.Api.Models;

public enum ReservationStatus
{
    Pending,
    Approved,
    Cancelled,
    Rejected,
    Completed
}
