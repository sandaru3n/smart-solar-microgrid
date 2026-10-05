namespace SolarGrid.Api.Services;

public interface IEmailService
{
    Task SendOtpEmailAsync(string toEmail, string otp);
    Task SendEmailAsync(string toEmail, string subject, string body);

    /// <summary>Sends a styled HTML email with a plain-text fallback for clients that block HTML.</summary>
    Task SendEmailAsync(string toEmail, string subject, string textBody, string htmlBody);
}
