/**
 * File: DeskActor.cs
 * Purpose: Identity used when the web desk creates a booking without a logged-in staff session.
 * Author: M.T.C PEIRIS  it23201200
 * Date: 2026
 */

using System.Security.Claims;
using SolarGrid.Api.Models;

namespace SolarGrid.Api.Services;

/// <summary>
/// The web reservation desk has no login yet. These calls run with Backoffice rules.
/// </summary>
public static class DeskActor
{
    // Builds a Backoffice principal for desk create, update and cancel.
    public static ClaimsPrincipal Create() =>
        new(new ClaimsIdentity(
            [
                new Claim(ClaimTypes.NameIdentifier, "backoffice-desk"),
                new Claim(ClaimTypes.Role, Role.BACKOFFICE.ToString()),
            ],
            authenticationType: "Desk"));
}
