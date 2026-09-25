using Microsoft.AspNetCore.Mvc;
using SolarGrid.Api.Data;

namespace SolarGrid.Api.Controllers;

[ApiController]
[Route("api/database")]
public class DatabaseTestController : ControllerBase
{
    private readonly MongoDbContext _mongoDbContext;

    public DatabaseTestController(MongoDbContext mongoDbContext)
    {
        _mongoDbContext = mongoDbContext;
    }

    [HttpGet("test")]
    public async Task<IActionResult> TestConnection()
    {
        try
        {
            await _mongoDbContext.Database.ListCollectionNamesAsync();

            return Ok(new
            {
                message = "MongoDB Atlas connection successful!"
            });
        }
        catch (Exception ex)
        {
            return StatusCode(500, new
            {
                message = "MongoDB connection failed.",
                error = ex.Message
            });
        }
    }
}