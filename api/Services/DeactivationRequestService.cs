using SolarGrid.Api.Models;
using SolarGrid.Api.Repositories;

namespace SolarGrid.Api.Services;

public class DeactivationRequestService
{
    private readonly DeactivationRequestRepository _requestRepository;
    private readonly UserService _userService;
    private readonly IEmailService _emailService;

    public DeactivationRequestService(
        DeactivationRequestRepository requestRepository,
        UserService userService,
        IEmailService emailService)
    {
        _requestRepository = requestRepository;
        _userService = userService;
        _emailService = emailService;
    }

    public async Task<(bool Success, string Message)> CreateRequestAsync(string nic, string reason)
    {
        var existingPending = await _requestRepository.GetPendingByNicAsync(nic);
        if (existingPending != null)
        {
            return (false, "You already have a pending deactivation request.");
        }

        var request = new DeactivationRequest
        {
            ProsumerNic = nic,
            Reason = reason,
            Status = DeactivationRequestStatus.PENDING,
            RequestedAt = DateTime.UtcNow
        };

        await _requestRepository.CreateAsync(request);

        var user = await _userService.GetByNICAsync(nic);
        if (user != null)
        {
            await _emailService.SendEmailAsync(user.Email, "Account Deactivation Request Received",
                "We have received your request to deactivate your Smart Solar Microgrid account. It is currently pending review by our Backoffice team.");
        }

        return (true, "Deactivation request submitted successfully.");
    }

    public async Task<List<DeactivationRequest>> GetPendingRequestsAsync()
    {
        return await _requestRepository.GetPendingAsync();
    }

    public async Task<(bool Success, string Message)> ApproveRequestAsync(string id, string reviewerNic)
    {
        var request = await _requestRepository.GetByIdAsync(id);
        if (request == null)
            return (false, "Request not found.");

        if (request.Status != DeactivationRequestStatus.PENDING)
            return (false, "Request is no longer pending.");

        var userResult = await _userService.DeactivateUserAsync(request.ProsumerNic);
        if (!userResult.Success)
            return (false, userResult.Message);

        request.Status = DeactivationRequestStatus.APPROVED;
        request.ReviewedAt = DateTime.UtcNow;
        request.ReviewerNic = reviewerNic;

        await _requestRepository.UpdateAsync(request);

        var user = await _userService.GetByNICAsync(request.ProsumerNic);
        if (user != null)
        {
            await _emailService.SendEmailAsync(user.Email, "Account Deactivation Request Approved",
                "Your request has been approved and your account is now deactivated. You will be logged out of your active sessions. To reactivate in the future, please contact support.");
        }

        return (true, "Request approved successfully.");
    }

    public async Task<(bool Success, string Message)> RejectRequestAsync(string id, string reason, string reviewerNic)
    {
        var request = await _requestRepository.GetByIdAsync(id);
        if (request == null)
            return (false, "Request not found.");

        if (request.Status != DeactivationRequestStatus.PENDING)
            return (false, "Request is no longer pending.");

        request.Status = DeactivationRequestStatus.REJECTED;
        request.RejectionReason = reason;
        request.ReviewedAt = DateTime.UtcNow;
        request.ReviewerNic = reviewerNic;

        await _requestRepository.UpdateAsync(request);

        var user = await _userService.GetByNICAsync(request.ProsumerNic);
        if (user != null)
        {
            await _emailService.SendEmailAsync(user.Email, "Account Deactivation Request Rejected",
                $"Your deactivation request was rejected by the Backoffice team for the following reason:\n\n{reason}\n\nYour account remains active.");
        }

        return (true, "Request rejected successfully.");
    }
}
