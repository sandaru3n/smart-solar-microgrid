using System.ComponentModel.DataAnnotations;

namespace SolarGrid.Api.Models;

public class CreateDeactivationRequestDto
{
    [Required]
    [MinLength(10, ErrorMessage = "Reason must be at least 10 characters long.")]
    public string Reason { get; set; } = string.Empty;
}

public class RejectDeactivationRequestDto
{
    [Required]
    public string Reason { get; set; } = string.Empty;
}
