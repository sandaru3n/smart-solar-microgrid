/*
 * File: EmailService.cs
 * Description: Service for handling SMTP email deliveries, including OTPs and account notifications.
 * Author: IT23163904_WVADK Chamara
 */
using System.Net;
using System.Net.Mail;
using System.Text;
using SolarGrid.Api.Models;

namespace SolarGrid.Api.Services;

public class EmailService : IEmailService
{
    private readonly EmailSettings _settings;
    private readonly ILogger<EmailService> _logger;

    // Initializes the EmailService with SMTP settings and a logger.
    public EmailService(EmailSettings settings, ILogger<EmailService> logger)
    {
        _settings = settings;
        _logger = logger;
    }

    // Sends a registration or reset OTP to the specified email address asynchronously in a background thread.
    public async Task SendOtpEmailAsync(string toEmail, string otp)
    {
        try
        {
            _ = Task.Run(async () =>
            {
                try
                {
                    var message = new MailMessage
                    {
                        From = new MailAddress(_settings.FromEmail, _settings.FromName),
                        Subject = "Smart Solar Microgrid - Email Verification",
                        Body = $"Smart Solar Microgrid\n\nYour email verification OTP is:\n\n{otp}\n\nThis OTP expires in approximately 10 minutes.\n",
                        IsBodyHtml = false
                    };
                    message.To.Add(new MailAddress(toEmail));

                    using var client = new SmtpClient(_settings.SmtpHost, _settings.SmtpPort)
                    {
                        Credentials = new NetworkCredential(_settings.SmtpUsername, _settings.SmtpPassword),
                        EnableSsl = true
                    };

                    await client.SendMailAsync(message);
                    _logger.LogInformation($"OTP email sent successfully to {toEmail}");
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, "Failed to send OTP email to {Email}", toEmail);
                }
            });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error scheduling OTP email to {Email}", toEmail);
            throw new InvalidOperationException("Failed to schedule email.");
        }
    }

    // Sends a plain-text email with the given subject and body to the specified email address in the background.
    public async Task SendEmailAsync(string toEmail, string subject, string body)
    {
        try
        {
            _ = Task.Run(async () =>
            {
                try
                {
                    var message = new MailMessage
                    {
                        From = new MailAddress(_settings.FromEmail, _settings.FromName),
                        Subject = subject,
                        Body = body,
                        IsBodyHtml = false
                    };
                    message.To.Add(new MailAddress(toEmail));

                    using var client = new SmtpClient(_settings.SmtpHost, _settings.SmtpPort)
                    {
                        Credentials = new NetworkCredential(_settings.SmtpUsername, _settings.SmtpPassword),
                        EnableSsl = true
                    };

                    await client.SendMailAsync(message);
                    _logger.LogInformation($"Email '{subject}' sent successfully to {toEmail}");
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, "Failed to send email to {Email}", toEmail);
                }
            });
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error scheduling email to {Email}", toEmail);
        }
    }

    // Sends a multipart email (plain-text and HTML) in a background thread to prevent blocking.
    public Task SendEmailAsync(string toEmail, string subject, string textBody, string htmlBody)
    {
        // Fire and forget like the other emails: a slow or failing SMTP server
        // must never block or fail the request that triggered the email.
        _ = Task.Run(async () =>
        {
            try
            {
                using var message = new MailMessage
                {
                    From = new MailAddress(_settings.FromEmail, _settings.FromName),
                    Subject = subject
                };
                message.To.Add(new MailAddress(toEmail));
                message.AlternateViews.Add(
                    AlternateView.CreateAlternateViewFromString(textBody, Encoding.UTF8, "text/plain"));
                message.AlternateViews.Add(
                    AlternateView.CreateAlternateViewFromString(htmlBody, Encoding.UTF8, "text/html"));

                using var client = new SmtpClient(_settings.SmtpHost, _settings.SmtpPort)
                {
                    Credentials = new NetworkCredential(_settings.SmtpUsername, _settings.SmtpPassword),
                    EnableSsl = true
                };

                await client.SendMailAsync(message);
                _logger.LogInformation("Email '{Subject}' sent successfully to {Email}", subject, toEmail);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Failed to send email '{Subject}' to {Email}", subject, toEmail);
            }
        });

        return Task.CompletedTask;
    }
}
