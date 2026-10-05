using SolarGrid.Api.Models;
using SolarGrid.Api.Services;

namespace SolarGrid.Api.Tests;

public class ReservationEmailTemplatesTests
{
    private static readonly EnergyReservation Reservation = new()
    {
        Id = "6710a1f28a1b2c3d4e5f6b01",
        ProsumerId = "200012345678",
        Status = ReservationStatus.Approved
    };

    // 04:30 UTC = 10:00 Sri Lanka time (UTC+5:30).
    private static readonly EnergyBookingSlot Slot = new()
    {
        StartTimeUtc = new DateTime(2026, 10, 7, 4, 30, 0, DateTimeKind.Utc),
        EndTimeUtc = new DateTime(2026, 10, 7, 5, 30, 0, DateTimeKind.Utc)
    };

    private static readonly SolarStation Station = new() { Name = "Kandy Solar Hub", Address = "12 Lake Rd, Kandy" };

    [Fact]
    public void Approved_email_has_status_slot_and_reference()
    {
        var email = ReservationEmailTemplates.BuildDecision(true, "Nimal Perera", Reservation, Slot, Station);

        Assert.Equal("Booking approved – Kandy Solar Hub, 7 Oct", email.Subject);
        Assert.Contains("Hi Nimal Perera,", email.TextBody);
        Assert.Contains("Status: Approved", email.TextBody);
        Assert.Contains("10:00 AM – 11:00 AM (Sri Lanka time)", email.TextBody);
        Assert.Contains("Wednesday, 7 October 2026", email.TextBody);
        Assert.Contains(Reservation.Id, email.TextBody);
        Assert.Contains("QR code", email.HtmlBody);
    }

    [Fact]
    public void Rejected_email_says_slot_was_released()
    {
        var email = ReservationEmailTemplates.BuildDecision(false, "Nimal Perera", Reservation, Slot, Station);

        Assert.StartsWith("Booking not approved", email.Subject);
        Assert.Contains("Status: Rejected", email.TextBody);
        Assert.Contains("released", email.TextBody);
        Assert.DoesNotContain("QR code", email.HtmlBody);
    }

    [Fact]
    public void Html_escapes_user_supplied_values()
    {
        var email = ReservationEmailTemplates.BuildDecision(true, "<script>x</script>", Reservation, Slot, Station);

        Assert.DoesNotContain("<script>", email.HtmlBody);
        Assert.Contains("&lt;script&gt;", email.HtmlBody);
    }

    [Fact]
    public void Missing_name_and_station_fall_back_gracefully()
    {
        var email = ReservationEmailTemplates.BuildDecision(true, " ", Reservation, Slot, station: null);

        Assert.Contains("Hi there,", email.TextBody);
        Assert.Contains("Station: Solar station", email.TextBody);
        Assert.DoesNotContain("Address:", email.TextBody);
    }
}
