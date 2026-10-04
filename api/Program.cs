using System.Text;
using System.Text.Json.Serialization;
using DotNetEnv;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.IdentityModel.Tokens;
using MongoDB.Driver;
using SolarGrid.Api.Data;
using SolarGrid.Api.Repositories;
using SolarGrid.Api.Services;

using SolarGrid.Api.Models;

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

// QR signing settings (Member 4). If QR_SECRET_KEY is missing the API still
// starts, but the QR endpoints return an error until it is set.
var qrOptions = new QrOptions
{
    SecretKey = Environment.GetEnvironmentVariable("QR_SECRET_KEY"),
    ValidBeforeSlotStart = ReadMinutes("QR_VALID_BEFORE_START_MINUTES", 120),
    ValidAfterSlotEnd = ReadMinutes("QR_VALID_AFTER_END_MINUTES", 0)
};

var emailSettings = new EmailSettings
{
    SmtpHost = Environment.GetEnvironmentVariable("SMTP_HOST") ?? "smtp.gmail.com",
    SmtpPort = int.TryParse(Environment.GetEnvironmentVariable("SMTP_PORT"), out var port) ? port : 587,
    SmtpUsername = Environment.GetEnvironmentVariable("SMTP_USERNAME") ?? string.Empty,
    SmtpPassword = Environment.GetEnvironmentVariable("SMTP_PASSWORD") ?? string.Empty,
    FromEmail = Environment.GetEnvironmentVariable("SMTP_FROM_EMAIL") ?? "noreply@smartsolarmicrogrid.com",
    FromName = Environment.GetEnvironmentVariable("SMTP_FROM_NAME") ?? "Smart Solar Microgrid"
};

// Register MongoDB
builder.Services.AddSingleton(mongoSettings);
builder.Services.AddSingleton<MongoDbContext>();
builder.Services.AddSingleton<IMongoDatabase>(sp =>
    sp.GetRequiredService<MongoDbContext>().Database);

// Register repositories
builder.Services.AddSingleton<UserRepository>();
builder.Services.AddSingleton<ReservationRepository>();
builder.Services.AddSingleton<ReservationMonitoringRepository>();
builder.Services.AddSingleton<PendingRegistrationRepository>();
builder.Services.AddSingleton<DeactivationRequestRepository>();

// Register services
builder.Services.AddSingleton(emailSettings);
builder.Services.AddSingleton<IEmailService, EmailService>();
builder.Services.AddSingleton<UserService>();
builder.Services.AddSingleton<JwtService>();
builder.Services.AddSingleton<StationService>();
builder.Services.AddSingleton<ReservationService>();
builder.Services.AddSingleton<DeactivationRequestService>();

// Member 4: booking monitoring and QR verification
builder.Services.TryAddSingleton(TimeProvider.System);
builder.Services.AddSingleton(qrOptions);
builder.Services.AddSingleton<IQrTokenService, QrTokenService>();
builder.Services.AddSingleton<ReservationMonitoringService>();
builder.Services.AddHostedService<ReservationIndexInitializer>();

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

        options.Events = new JwtBearerEvents
        {
            OnTokenValidated = async context =>
            {
                var userService = context.HttpContext.RequestServices.GetRequiredService<UserService>();
                var nicClaim = context.Principal?.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;
                if (!string.IsNullOrEmpty(nicClaim))
                {
                    var user = await userService.GetByNICAsync(nicClaim);
                    if (user == null || user.AccountStatus == AccountStatus.DEACTIVATED)
                    {
                        context.Fail("Account is deactivated.");
                    }
                }
            }
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

builder.Services.AddSwaggerGen(options =>
{
    options.AddSecurityDefinition("Bearer", new Microsoft.OpenApi.Models.OpenApiSecurityScheme
    {
        Name = "Authorization",
        Type = Microsoft.OpenApi.Models.SecuritySchemeType.Http,
        Scheme = "Bearer",
        BearerFormat = "JWT",
        In = Microsoft.OpenApi.Models.ParameterLocation.Header,
        Description = "Enter your JWT token."
    });

    options.AddSecurityRequirement(new Microsoft.OpenApi.Models.OpenApiSecurityRequirement
    {
        {
            new Microsoft.OpenApi.Models.OpenApiSecurityScheme
            {
                Reference = new Microsoft.OpenApi.Models.OpenApiReference
                {
                    Type = Microsoft.OpenApi.Models.ReferenceType.SecurityScheme,
                    Id = "Bearer"
                }
            },
            Array.Empty<string>()
        }
    });
});

builder.Services.AddScoped<StationService>();
var app = builder.Build();

if (!qrOptions.IsConfigured)
{
    app.Logger.LogWarning(
        "QR_SECRET_KEY is missing from .env. QR endpoints will return an error until it is set.");
}
else
{
    // Fails fast on a key that is too short.
    app.Services.GetRequiredService<IQrTokenService>();
}

// Configure the HTTP request pipeline
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseDefaultFiles();
app.UseStaticFiles();

app.UseHttpsRedirection();

// Authentication must come before authorization
app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();

static TimeSpan ReadMinutes(string name, int defaultMinutes)
{
    var value = Environment.GetEnvironmentVariable(name);
    if (string.IsNullOrWhiteSpace(value))
    {
        return TimeSpan.FromMinutes(defaultMinutes);
    }

    if (!int.TryParse(value, out var minutes) || minutes < 0)
    {
        throw new InvalidOperationException($"{name} in .env must be a whole number of minutes (0 or more).");
    }

    return TimeSpan.FromMinutes(minutes);
}