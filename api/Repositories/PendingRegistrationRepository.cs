/*
 * File: PendingRegistrationRepository.cs
 * Description: Data access layer for managing temporary Prosumer registration states and OTP verification workflows.
 * Author: IT23163904_WVADK Chamara
 */
using MongoDB.Driver;
using SolarGrid.Api.Data;
using SolarGrid.Api.Models;

namespace SolarGrid.Api.Repositories;

public class PendingRegistrationRepository
{
    private readonly IMongoCollection<PendingRegistration> _collection;

    public PendingRegistrationRepository(MongoDbContext dbContext)
    {
        _collection = dbContext.Database.GetCollection<PendingRegistration>("PendingRegistrations");
    }

    // Saves a newly started, unverified registration session into the database.
    public async Task CreateAsync(PendingRegistration registration)
    {
        await _collection.InsertOneAsync(registration);
    }

    // Retrieves a pending registration session using its unique temporary identifier.
    public async Task<PendingRegistration?> GetByRegistrationIdAsync(string registrationId)
    {
        return await _collection.Find(r => r.RegistrationId == registrationId).FirstOrDefaultAsync();
    }

    // Checks if there is already an active registration process for the given NIC or email.
    public async Task<PendingRegistration?> GetByNicOrEmailAsync(string nic, string email)
    {
        return await _collection.Find(r => r.NIC == nic || r.Email == email).FirstOrDefaultAsync();
    }

    // Updates an existing pending registration document (e.g., when the OTP is verified).
    public async Task UpdateAsync(PendingRegistration registration)
    {
        await _collection.ReplaceOneAsync(r => r.Id == registration.Id, registration);
    }

    // Removes obsolete or abandoned registration sessions matching the NIC or email.
    public async Task DeleteByNicOrEmailAsync(string nic, string email)
    {
        await _collection.DeleteManyAsync(r => r.NIC == nic || r.Email == email);
    }

    // Deletes a single pending registration by its internal database ID.
    public async Task DeleteAsync(string id)
    {
        await _collection.DeleteOneAsync(r => r.Id == id);
    }

    // Retrieves all registrations that have passed OTP verification and are awaiting admin approval.
    public async Task<List<PendingRegistration>> GetEmailVerifiedAsync()
    {
        return await _collection.Find(r => r.IsEmailVerified).ToListAsync();
    }
}
