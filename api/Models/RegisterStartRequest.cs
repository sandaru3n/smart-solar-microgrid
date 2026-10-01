using Microsoft.AspNetCore.Http;
using System.ComponentModel.DataAnnotations;

namespace SolarGrid.Api.Models;

public class RegisterStartRequest
{
    [Required]
    public string NIC { get; set; } = string.Empty;

    [Required]
    public string Name { get; set; } = string.Empty;

    [Required]
    [EmailAddress]
    public string Email { get; set; } = string.Empty;

    public string Phone { get; set; } = string.Empty;

    public string Address { get; set; } = string.Empty;

    [Required]
    public string Password { get; set; } = string.Empty;

    [Required]
    public string NicImageUrl { get; set; } = string.Empty;
}
