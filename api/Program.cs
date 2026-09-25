using DotNetEnv;
using SolarGrid.Api.Data;

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

// Register MongoDB
builder.Services.AddSingleton(mongoSettings);
builder.Services.AddSingleton<MongoDbContext>();

// Add services to the container
builder.Services.AddControllers();

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

app.UseAuthorization();

app.MapControllers();

app.Run();