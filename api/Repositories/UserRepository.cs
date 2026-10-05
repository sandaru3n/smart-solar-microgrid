/*
 * File: UserRepository.cs
 * Description: Data access layer for User accounts, integrating with MongoDB.
 * Author: IT23163904_WVADK Chamara
 */
using System.Text.RegularExpressions;
using MongoDB.Bson;
using MongoDB.Driver;
using SolarGrid.Api.Data;
using SolarGrid.Api.Models;

namespace SolarGrid.Api.Repositories;

public class UserRepository
{
    private readonly IMongoCollection<User> _users;

    public UserRepository(MongoDbContext mongoDbContext)
    {
        _users = mongoDbContext.Database.GetCollection<User>("Users");
    }

    // Retrieves a single user from the database matching the exact provided NIC.
    public async Task<User?> GetByNICAsync(string nic)
    {
        return await _users
            .Find(user => user.NIC == nic)
            .FirstOrDefaultAsync();
    }

    // Performs a case-insensitive search for a user by their email address.
    public async Task<User?> GetByEmailAsync(string email)
    {
        var pattern = new BsonRegularExpression($"^{Regex.Escape(email.Trim())}$", "i");
        return await _users
            .Find(Builders<User>.Filter.Regex(user => user.Email, pattern))
            .FirstOrDefaultAsync();
    }

    // Fetches every registered user account from the database.
    public async Task<List<User>> GetAllAsync()
    {
        return await _users
            .Find(_ => true)
            .ToListAsync();
    }

    // Find all users whose accounts are waiting for activation (Member 1 scope).
    public async Task<List<User>> GetPendingUsersAsync()
{
    return await _users
        .Find(user => user.AccountStatus == AccountStatus.PENDING)
        .ToListAsync();
}

    // Inserts a new user record into the MongoDB collection.
    public async Task CreateAsync(User user)
    {
        await _users.InsertOneAsync(user);
    }

    // Replaces the existing user document with the updated model using NIC as the identifier.
    public async Task UpdateAsync(User user)
    {
        await _users.ReplaceOneAsync(
            existingUser => existingUser.NIC == user.NIC,
            user
        );
    }

    // Deletes a user document completely from the database.
    public async Task DeleteAsync(string nic)
    {
        await _users.DeleteOneAsync(user => user.NIC == nic);
    }


    public async Task<User?> GetByRoleAsync(Role role)
{
    return await _users
        .Find(user => user.Role == role)
        .FirstOrDefaultAsync();
}
}