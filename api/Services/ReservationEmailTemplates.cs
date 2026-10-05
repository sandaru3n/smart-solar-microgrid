using System.Globalization;
using System.Net;
using SolarGrid.Api.Models;

namespace SolarGrid.Api.Services;

/// <summary>
/// Builds the emails a prosumer receives when staff approve or reject their booking.
/// Pure functions (no I/O) so they can be unit tested.
/// </summary>
public static class ReservationEmailTemplates
{
    public sealed record ReservationEmail(string Subject, string TextBody, string HtmlBody);

    private static readonly TimeZoneInfo SriLankaZone = ResolveSriLankaZone();
    private static readonly CultureInfo Culture = CultureInfo.GetCultureInfo("en-US");

    public static ReservationEmail BuildDecision(
        bool approved,
        string prosumerName,
        EnergyReservation reservation,
        EnergyBookingSlot slot,
        SolarStation? station)
    {
        var name = string.IsNullOrWhiteSpace(prosumerName) ? "there" : prosumerName.Trim();
        var stationName = string.IsNullOrWhiteSpace(station?.Name) ? "Solar station" : station!.Name;
        var stationAddress = station?.Address ?? string.Empty;
        var start = ToSriLanka(slot.StartTimeUtc);
        var end = ToSriLanka(slot.EndTimeUtc);
        var date = start.ToString("dddd, d MMMM yyyy", Culture);
        var time = $"{start.ToString("h:mm tt", Culture)} – {end.ToString("h:mm tt", Culture)} (Sri Lanka time)";

        var subject = approved
            ? $"Booking approved – {stationName}, {start.ToString("d MMM", Culture)}"
            : $"Booking not approved – {stationName}, {start.ToString("d MMM", Culture)}";

        var headline = approved ? "Your booking is approved" : "Your booking was not approved";
        var intro = approved
            ? "Good news! A grid operator has approved your energy slot booking. Your slot is reserved."
            : "Sorry, a grid operator could not approve your energy slot booking. The slot has been released.";
        var nextSteps = approved
            ? new[]
            {
                "Open the Smart Solar Microgrid app to view your booking and its QR code.",
                "Show the QR code at the station when your slot starts.",
                "Need a different time? You can change or cancel the booking in the app.",
            }
            : new[]
            {
                "This booking will not appear as an active reservation.",
                "Open the Smart Solar Microgrid app to book another available slot.",
            };

        var rows = new List<(string Label, string Value)>
        {
            ("Status", approved ? "Approved" : "Rejected"),
            ("Station", stationName),
        };
        if (!string.IsNullOrWhiteSpace(stationAddress)) rows.Add(("Address", stationAddress));
        rows.Add(("Date", date));
        rows.Add(("Time", time));
        rows.Add(("Reference", reservation.Id));

        var text = BuildText(name, headline, intro, rows, nextSteps);
        var html = BuildHtml(approved, name, headline, intro, rows, nextSteps);

        return new ReservationEmail(subject, text, html);
    }

    private static string BuildText(
        string name,
        string headline,
        string intro,
        IEnumerable<(string Label, string Value)> rows,
        IEnumerable<string> nextSteps)
    {
        var lines = new List<string>
        {
            "Smart Solar Microgrid",
            string.Empty,
            headline,
            string.Empty,
            $"Hi {name},",
            string.Empty,
            intro,
            string.Empty,
            "Booking details",
        };
        lines.AddRange(rows.Select(row => $"  {row.Label}: {row.Value}"));
        lines.Add(string.Empty);
        lines.Add("What's next");
        lines.AddRange(nextSteps.Select(step => $"  - {step}"));
        lines.Add(string.Empty);
        lines.Add("This is an automated message. Please do not reply to this email.");

        return string.Join("\n", lines);
    }

    private static string BuildHtml(
        bool approved,
        string name,
        string headline,
        string intro,
        IEnumerable<(string Label, string Value)> rows,
        IEnumerable<string> nextSteps)
    {
        static string E(string value) => WebUtility.HtmlEncode(value);

        var badgeBg = approved ? "#DCFCE7" : "#FEE2E2";
        var badgeFg = approved ? "#166534" : "#991B1B";
        var badgeText = approved ? "&#10003;&nbsp; Approved" : "&#10005;&nbsp; Rejected";

        var detailRows = string.Concat(rows.Select(row =>
            $"""
            <tr>
              <td style="padding:10px 0;border-bottom:1px solid #F1F0EE;color:#78716C;font-size:13px;width:110px;vertical-align:top;">{E(row.Label)}</td>
              <td style="padding:10px 0;border-bottom:1px solid #F1F0EE;color:#1C1914;font-size:14px;font-weight:600;vertical-align:top;{(row.Label == "Reference" ? "font-family:Consolas,Menlo,monospace;font-size:13px;word-break:break-all;" : string.Empty)}">{E(row.Value)}</td>
            </tr>
            """));

        var steps = string.Concat(nextSteps.Select(step =>
            $"""<li style="margin:0 0 8px;color:#3F3F46;font-size:14px;line-height:1.55;">{E(step)}</li>"""));

        return $"""
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>{E(headline)}</title>
            </head>
            <body style="margin:0;padding:0;background:#F5F5F4;font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif;">
              <div style="display:none;max-height:0;overflow:hidden;">{E(intro)}</div>
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#F5F5F4;padding:24px 12px;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#FFFFFF;border-radius:16px;overflow:hidden;border:1px solid #E7E5E4;">
                      <tr>
                        <td style="background:#FFDD19;padding:18px 28px;color:#1C1914;font-size:16px;font-weight:700;">
                          &#9728;&nbsp; Smart Solar Microgrid
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:28px 28px 8px;">
                          <span style="display:inline-block;padding:5px 12px;border-radius:999px;background:{badgeBg};color:{badgeFg};font-size:12px;font-weight:700;">{badgeText}</span>
                          <h1 style="margin:16px 0 8px;color:#1C1914;font-size:22px;line-height:1.3;">{E(headline)}</h1>
                          <p style="margin:0 0 6px;color:#3F3F46;font-size:14px;line-height:1.6;">Hi {E(name)},</p>
                          <p style="margin:0;color:#3F3F46;font-size:14px;line-height:1.6;">{E(intro)}</p>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:20px 28px 4px;">
                          <p style="margin:0 0 4px;color:#78716C;font-size:11px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;">Booking details</p>
                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0">{detailRows}</table>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:20px 28px 28px;">
                          <p style="margin:0 0 10px;color:#78716C;font-size:11px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;">What's next</p>
                          <ul style="margin:0;padding-left:20px;">{steps}</ul>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:16px 28px;background:#FAFAF9;border-top:1px solid #E7E5E4;color:#A8A29E;font-size:12px;line-height:1.5;">
                          This is an automated message from Smart Solar Microgrid. Please do not reply to this email.
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """;
    }

    private static DateTime ToSriLanka(DateTime value)
    {
        var utc = value.Kind == DateTimeKind.Utc ? value : DateTime.SpecifyKind(value, DateTimeKind.Utc);
        return TimeZoneInfo.ConvertTimeFromUtc(utc, SriLankaZone);
    }

    private static TimeZoneInfo ResolveSriLankaZone()
    {
        foreach (var id in new[] { "Asia/Colombo", "Sri Lanka Standard Time" })
        {
            try
            {
                return TimeZoneInfo.FindSystemTimeZoneById(id);
            }
            catch (TimeZoneNotFoundException)
            {
            }
            catch (InvalidTimeZoneException)
            {
            }
        }

        return TimeZoneInfo.CreateCustomTimeZone("Asia/Colombo", TimeSpan.FromMinutes(330), "Sri Lanka", "Sri Lanka");
    }
}
