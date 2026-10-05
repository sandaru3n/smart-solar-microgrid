/*
 * File: DeactivationRequestRepository.cs
 * Description: Data access layer for managing Prosumer account deactivation requests.
 * Author: IT23163904_WVADK Chamara
 */
using MongoDB.Driver;
using SolarGrid.Api.Data;
using SolarGrid.Api.Models;

namespace SolarGrid.Api.Repositories;

public class DeactivationRequestRepository
{
    private readonly IMongoCollection<DeactivationRequest> _requests;

    public DeactivationRequestRepository(MongoDbContext dbContext)
    {
        _requests = dbContext.Database.GetCollection<DeactivationRequest>("DeactivationRequests");
    }

    // Retrieves all deactivation requests that are currently waiting for admin approval.
    public async Task<List<DeactivationRequest>> GetPendingAsync()
    {
        return await _requests.Find(r => r.Status == DeactivationRequestStatus.PENDING).ToListAsync();
    }

    // Checks if a specific Prosumer already has an ongoing deactivation request.
    public async Task<DeactivationRequest?> GetPendingByNicAsync(string nic)
    {
        return await _requests.Find(r => r.ProsumerNic == nic && r.Status == DeactivationRequestStatus.PENDING).FirstOrDefaultAsync();
    }

    // Fetches a specific deactivation request by its database ID.
    public async Task<DeactivationRequest?> GetByIdAsync(string id)
    {
        return await _requests.Find(r => r.Id == id).FirstOrDefaultAsync();
    }

    // Creates and saves a new deactivation request submitted by a Prosumer.
    public async Task CreateAsync(DeactivationRequest request)
    {
        await _requests.InsertOneAsync(request);
    }

    // Updates an existing request document, typically when an admin approves or rejects it.
    public async Task UpdateAsync(DeactivationRequest request)
    {
        await _requests.ReplaceOneAsync(r => r.Id == request.Id, request);
    }
}
