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

    public ReservationException(ReservationErrorKind kind, string message)
        : base(message)
    {
        Kind = kind;
    }
}
