using System.ComponentModel.DataAnnotations;

namespace SolarGrid.Api.Models;

public class ForgotPasswordRequest
{
    [Required]
    [EmailAddress]
    public string Email { get; set; } = string.Empty;
}
