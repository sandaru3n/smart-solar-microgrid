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

    public async Task<List<DeactivationRequest>> GetPendingAsync()
    {
        return await _requests.Find(r => r.Status == DeactivationRequestStatus.PENDING).ToListAsync();
    }

    public async Task<DeactivationRequest?> GetPendingByNicAsync(string nic)
    {
        return await _requests.Find(r => r.ProsumerNic == nic && r.Status == DeactivationRequestStatus.PENDING).FirstOrDefaultAsync();
    }

    public async Task<DeactivationRequest?> GetByIdAsync(string id)
    {
        return await _requests.Find(r => r.Id == id).FirstOrDefaultAsync();
    }

    public async Task CreateAsync(DeactivationRequest request)
    {
        await _requests.InsertOneAsync(request);
    }

    public async Task UpdateAsync(DeactivationRequest request)
    {
        await _requests.ReplaceOneAsync(r => r.Id == request.Id, request);
    }
}
