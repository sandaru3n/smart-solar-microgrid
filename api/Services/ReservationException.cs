/**
 * File: ReservationException.cs
 * Purpose: Turns a booking-rule failure into an HTTP error kind.
 * Author: M.T.C PEIRIS  it23201200
 * Date: 2026
 */

namespace SolarGrid.Api.Services;

public enum ReservationErrorKind
{
    Unauthorized,
    Forbidden,
    BadRequest,
    NotFound,
    Conflict
}

public sealed class ReservationException : Exception
{
    public ReservationErrorKind Kind { get; }

    // Stores the rule failure and the message returned to the client.
    public ReservationException(ReservationErrorKind kind, string message)
        : base(message)
    {
        Kind = kind;
    }
}
