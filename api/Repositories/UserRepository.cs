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

    public async Task<User?> GetByNICAsync(string nic)
    {
        return await _users
            .Find(user => user.NIC == nic)
            .FirstOrDefaultAsync();
    }

    public async Task<List<User>> GetAllAsync()
    {
        return await _users
            .Find(_ => true)
            .ToListAsync();
    }

    public async Task CreateAsync(User user)
    {
        await _users.InsertOneAsync(user);
    }

    public async Task UpdateAsync(User user)
    {
        await _users.ReplaceOneAsync(
            existingUser => existingUser.NIC == user.NIC,
            user
        );
    }

    public async Task DeleteAsync(string nic)
    {
        await _users.DeleteOneAsync(user => user.NIC == nic);
    }
}