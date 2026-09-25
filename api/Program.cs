using System.Text;
using System.Text.Json.Serialization;
using DotNetEnv;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;
using SolarGrid.Api.Data;
using SolarGrid.Api.Repositories;
using SolarGrid.Api.Services;

Env.Load("../.env");

var builder = WebApplication.CreateBuilder(args);

// MongoDB settings from root .env
var mongoSettings = new MongoDbSettings
{
    ConnectionString = Environment.GetEnvironmentVariable("MONGODB_CONNECTION_STRING")
        ?? throw new InvalidOperationException("MONGODB_CONNECTION_STRING is missing from .env"),

    DatabaseName = Environment.GetEnvironmentVariable("MONGODB_DATABASE_NAME")
        ?? throw new InvalidOperationException("MONGODB_DATABASE_NAME is missing from .env")
};

// JWT secret
var jwtSecret = Environment.GetEnvironmentVariable("JWT_SECRET")
    ?? throw new InvalidOperationException("JWT_SECRET is missing from .env");

// Register MongoDB
builder.Services.AddSingleton(mongoSettings);
builder.Services.AddSingleton<MongoDbContext>();

// Register repositories
builder.Services.AddSingleton<UserRepository>();

// Register services
builder.Services.AddSingleton<UserService>();
builder.Services.AddSingleton<JwtService>();

// Configure JWT authentication
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuerSigningKey = true,

            IssuerSigningKey = new SymmetricSecurityKey(
                Encoding.UTF8.GetBytes(jwtSecret)
            ),

            ValidateIssuer = false,
            ValidateAudience = false,

            ValidateLifetime = true,

            ClockSkew = TimeSpan.Zero
        };
    });

// Add controllers
builder.Services.AddControllers()
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.Converters.Add(
            new JsonStringEnumConverter()
        );
    });

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

// Configure the HTTP request pipeline
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();

// Authentication must come before authorization
app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();