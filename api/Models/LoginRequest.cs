namespace SolarGrid.Api.Models;

public class LoginRequest
{
    public string NIC { get; set; } = string.Empty;

    public string Password { get; set; } = string.Empty;
}