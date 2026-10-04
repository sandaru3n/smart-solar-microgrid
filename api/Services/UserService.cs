using SolarGrid.Api.Models;
using SolarGrid.Api.Repositories;

namespace SolarGrid.Api.Services;

public class UserService
{
    private readonly UserRepository _userRepository;
    private readonly JwtService _jwtService;
    private readonly PendingRegistrationRepository _pendingRegistrationRepository;
    private readonly IEmailService _emailService;
    private readonly Data.MongoDbContext _dbContext;

    public UserService(
        UserRepository userRepository,
        JwtService jwtService,
        PendingRegistrationRepository pendingRegistrationRepository,
        IEmailService emailService,
        Data.MongoDbContext dbContext)
    {
        _userRepository = userRepository;
        _jwtService = jwtService;
        _pendingRegistrationRepository = pendingRegistrationRepository;
        _emailService = emailService;
        _dbContext = dbContext;
    }

    public async Task<User?> GetByNICAsync(string nic)
    {
        return await _userRepository.GetByNICAsync(nic);
    }

    public async Task<List<User>> GetAllAsync()
    {
        return await _userRepository.GetAllAsync();
    }

    public async Task<List<User>> GetPendingUsersAsync()
    {
        var pending = await _pendingRegistrationRepository.GetEmailVerifiedAsync();
        return pending.Select(p => new User
        {
            NIC = p.NIC,
            Name = p.Name,
            Email = p.Email,
            Phone = p.Phone,
            Address = p.Address,
            Role = Role.PROSUMER,
            AccountStatus = AccountStatus.PENDING,
            EmailVerified = true,
            NicImageUrl = p.NicImageUrl,
            NicVerificationStatus = "PENDING_REVIEW",
            CreatedDate = p.CreatedDate
        }).ToList();
    }

    public async Task<(bool Success, string Message, User? User)> CreateUserAsync(
        User user,
        string password)
    {
        var existingUser = await _userRepository.GetByNICAsync(user.NIC);

        if (existingUser != null)
        {
            return (false, "A user with this NIC already exists.", null);
        }

        if (string.IsNullOrWhiteSpace(password))
        {
            return (false, "Password is required.", null);
        }

        user.PasswordHash = BCrypt.Net.BCrypt.HashPassword(password);

        user.CreatedDate = DateTime.UtcNow;
        user.UpdatedDate = DateTime.UtcNow;

        await _userRepository.CreateAsync(user);

        user.PasswordHash = string.Empty;

        return (true, "User created successfully.", user);
    }

    
    public async Task<(bool Success, string Message, string? RegistrationId)> StartRegistrationAsync(RegisterStartRequest request)
    {
        var normalizedNic = NicValidationHelper.Normalize(request.NIC);
        
        if (!NicValidationHelper.IsValid(normalizedNic))
        {
            return (false, "Invalid Sri Lankan NIC format.", null);
        }

        var existingUser = await _userRepository.GetByNICAsync(normalizedNic);
        if (existingUser != null)
        {
            return (false, "This NIC is already registered.", null);
        }

        if (string.IsNullOrEmpty(request.NicImageUrl))
        {
            return (false, "NIC image URL is required.", null);
        }

        var otp = System.Security.Cryptography.RandomNumberGenerator.GetInt32(100000, 1000000).ToString();
        var otpHash = BCrypt.Net.BCrypt.HashPassword(otp);

        var pending = new PendingRegistration
        {
            RegistrationId = Guid.NewGuid().ToString("N"),
            NIC = normalizedNic,
            Name = request.Name.Trim(),
            Email = request.Email.Trim().ToLowerInvariant(),
            Phone = request.Phone?.Trim() ?? string.Empty,
            Address = request.Address?.Trim() ?? string.Empty,
            PasswordHash = BCrypt.Net.BCrypt.HashPassword(request.Password),
            NicImageUrl = request.NicImageUrl,
            OtpHash = otpHash,
            OtpExpiry = DateTime.UtcNow.AddMinutes(10),
            CreatedDate = DateTime.UtcNow,
            UpdatedDate = DateTime.UtcNow
        };

        await _pendingRegistrationRepository.DeleteByNicOrEmailAsync(pending.NIC, pending.Email);

        await _pendingRegistrationRepository.CreateAsync(pending);

        await _emailService.SendOtpEmailAsync(pending.Email, otp);

        return (true, "A verification OTP has been sent to your email.", pending.RegistrationId);
    }

    public async Task<(bool Success, string Message, User? User)> VerifyOtpAsync(VerifyOtpRequest request)
    {
        var pending = await _pendingRegistrationRepository.GetByRegistrationIdAsync(request.RegistrationId);

        if (pending == null)
        {
            return (false, "Invalid registration session.", null);
        }

        if (pending.OtpAttempts >= 5)
        {
            return (false, "Too many verification attempts. Please request a new OTP.", null);
        }

        if (DateTime.UtcNow > pending.OtpExpiry)
        {
            return (false, "Verification OTP has expired. Please request a new OTP.", null);
        }

        bool isValid = BCrypt.Net.BCrypt.Verify(request.Otp, pending.OtpHash);
        if (!isValid)
        {
            pending.OtpAttempts++;
            await _pendingRegistrationRepository.UpdateAsync(pending);
            return (false, "Invalid verification OTP.", null);
        }

        pending.IsEmailVerified = true;
        pending.UpdatedDate = DateTime.UtcNow;
        await _pendingRegistrationRepository.UpdateAsync(pending);

        var tempUser = new User
        {
            NIC = pending.NIC,
            Name = pending.Name,
            Email = pending.Email,
            Phone = pending.Phone,
            Address = pending.Address,
            Role = Role.PROSUMER,
            AccountStatus = AccountStatus.PENDING,
            EmailVerified = true
        };

        return (true, "Email verified successfully. Registration completed. Your account is pending Backoffice activation.", tempUser);
    }

    public async Task<(bool Success, string Message)> ResendOtpAsync(ResendOtpRequest request)
    {
        var pending = await _pendingRegistrationRepository.GetByRegistrationIdAsync(request.RegistrationId);

        if (pending == null)
        {
            return (false, "Invalid registration session.");
        }

        if (DateTime.UtcNow < pending.UpdatedDate.AddMinutes(1))
        {
            return (false, "Please wait before requesting a new OTP.");
        }

        var otp = System.Security.Cryptography.RandomNumberGenerator.GetInt32(100000, 1000000).ToString();
        pending.OtpHash = BCrypt.Net.BCrypt.HashPassword(otp);
        pending.OtpExpiry = DateTime.UtcNow.AddMinutes(10);
        pending.OtpAttempts = 0;
        pending.UpdatedDate = DateTime.UtcNow;

        await _pendingRegistrationRepository.UpdateAsync(pending);

        await _emailService.SendOtpEmailAsync(pending.Email, otp);

        return (true, "A new verification OTP has been sent.");
    }

    public async Task<(bool Success, string Message, User? User)> CreateStaffAsync(
    User user,
    string password)
{
    // Check whether a user with the NIC already exists.
    var existingUser = await _userRepository.GetByNICAsync(user.NIC);

    if (existingUser != null)
    {
        return (false, "A user with this NIC already exists.", null);
    }

    // Check that a password was provided.
    if (string.IsNullOrWhiteSpace(password))
    {
        return (false, "Password is required.", null);
    }

    // Only Backoffice and Grid Operator roles can be created as staff.
    if (user.Role != Role.BACKOFFICE &&
        user.Role != Role.GRID_OPERATOR)
    {
        return (false, "Invalid staff role.", null);
    }

    // Hash the password before storing the user.
    user.PasswordHash = BCrypt.Net.BCrypt.HashPassword(password);

    // Set the account status and timestamps.
    user.AccountStatus = AccountStatus.ACTIVE;
    user.CreatedDate = DateTime.UtcNow;
    user.UpdatedDate = DateTime.UtcNow;

    // Save the staff account to MongoDB.
    await _userRepository.CreateAsync(user);

    // Prevent the password hash from being returned to the client.
    user.PasswordHash = string.Empty;

    return (true, "Staff account created successfully.", user);
}

    public async Task<(bool Success, string Message)> ForgotPasswordAsync(string email)
    {
        var user = await _userRepository.GetByEmailAsync(email);
        if (user == null)
        {
            return (false, "No account found with this email.");
        }

        var otp = System.Security.Cryptography.RandomNumberGenerator.GetInt32(100000, 1000000).ToString();
        user.ResetPasswordOtp = BCrypt.Net.BCrypt.HashPassword(otp);
        user.ResetPasswordOtpExpiry = DateTime.UtcNow.AddMinutes(15);

        await _userRepository.UpdateAsync(user);
        await _emailService.SendOtpEmailAsync(user.Email, otp);

        return (true, "A password reset OTP has been sent to your email.");
    }

    public async Task<(bool Success, string Message)> ResetPasswordAsync(string email, string otp, string newPassword)
    {
        var user = await _userRepository.GetByEmailAsync(email);
        if (user == null || user.ResetPasswordOtp == null || user.ResetPasswordOtpExpiry == null)
        {
            return (false, "Invalid request.");
        }

        if (DateTime.UtcNow > user.ResetPasswordOtpExpiry.Value)
        {
            return (false, "OTP has expired. Please request a new one.");
        }

        if (!BCrypt.Net.BCrypt.Verify(otp, user.ResetPasswordOtp))
        {
            return (false, "Invalid OTP.");
        }

        user.PasswordHash = BCrypt.Net.BCrypt.HashPassword(newPassword);
        user.ResetPasswordOtp = null;
        user.ResetPasswordOtpExpiry = null;
        user.UpdatedDate = DateTime.UtcNow;

        await _userRepository.UpdateAsync(user);

        return (true, "Password has been successfully changed.");
    }

    public async Task<(bool Success, string Message)> UpdateUserAsync(User user)
    {
        var existingUser = await _userRepository.GetByNICAsync(user.NIC);

        if (existingUser == null)
        {
            return (false, "User not found.");
        }

        user.UpdatedDate = DateTime.UtcNow;

        await _userRepository.UpdateAsync(user);

        return (true, "User updated successfully.");
    }

    public async Task<(bool Success, string Message, User? User)> UpdateProfileAsync(string nic, UpdateProfileRequest request)
    {
        var existingUser = await _userRepository.GetByNICAsync(nic);

        if (existingUser == null)
        {
            return (false, "User not found.", null);
        }

        if (!string.IsNullOrWhiteSpace(request.Name))
        {
            existingUser.Name = request.Name.Trim();
        }
        // Email updates are handled by a separate verified flow
        // if (!string.IsNullOrWhiteSpace(request.Email))
        // {
        //     existingUser.Email = request.Email.Trim();
        // }
        
        if (request.Phone != null)
        {
            existingUser.Phone = request.Phone.Trim();
        }
        
        if (request.Address != null)
        {
            existingUser.Address = request.Address.Trim();
        }
        
        if (request.ProfilePicUrl != null)
        {
            existingUser.ProfilePicUrl = request.ProfilePicUrl.Trim();
        }

        existingUser.UpdatedDate = DateTime.UtcNow;

        await _userRepository.UpdateAsync(existingUser);

        existingUser.PasswordHash = string.Empty;

        return (true, "Profile updated successfully.", existingUser);
    }

    public async Task<(bool Success, string Message)> RequestDeactivationAsync(string nic)
    {
        var user = await _userRepository.GetByNICAsync(nic);

        if (user == null)
        {
            return (false, "User not found.");
        }

        if (user.AccountStatus == AccountStatus.DEACTIVATED)
        {
            return (false, "Account is already deactivated.");
        }

        if (user.AccountStatus == AccountStatus.PENDING)
        {
            return (false, "Account is pending activation.");
        }

        user.AccountStatus = AccountStatus.DEACTIVATED;
        user.UpdatedDate = DateTime.UtcNow;

        await _userRepository.UpdateAsync(user);

        return (true, "Account deactivated successfully.");
    }

    public async Task<(bool Success, string Message)> DeactivateUserAsync(string nic)
    {
        var user = await _userRepository.GetByNICAsync(nic);

        if (user == null)
        {
            return (false, "User not found.");
        }

        user.AccountStatus = AccountStatus.DEACTIVATED;
        user.UpdatedDate = DateTime.UtcNow;

        await _userRepository.UpdateAsync(user);

        return (true, "User deactivated successfully.");
    }



    public async Task<(bool Success, string Message)> ReactivateUserAsync(string nic)
{
    // Find the user account using the NIC.
    var user = await _userRepository.GetByNICAsync(nic);

    // Return an error if the user does not exist.
    if (user == null)
    {
        return (false, "User not found.");
    }

    if (user.AccountStatus != AccountStatus.DEACTIVATED)
    {
        return (false, "Only deactivated accounts can be reactivated.");
    }

    // Change the account status back to active.
    user.AccountStatus = AccountStatus.ACTIVE;

    // Update the modification timestamp.
    user.UpdatedDate = DateTime.UtcNow;

    // Save the updated user to MongoDB.
    await _userRepository.UpdateAsync(user);

    return (true, "User reactivated successfully.");
}

public async Task<(bool Success, string Message)> RejectRegistrationAsync(string nic, string reason)
{
    var pending = await _pendingRegistrationRepository.GetByNicOrEmailAsync(nic, nic);
    if (pending == null) return (false, "Pending registration not found.");

    await _pendingRegistrationRepository.DeleteAsync(pending.Id!);



    await _emailService.SendEmailAsync(pending.Email, "Registration Rejected", 
        $"Your registration for Smart Solar Microgrid has been rejected by the backoffice team.\n\nReason: {reason}\n\nPlease correct the issues and try registering again.");

    return (true, "Registration rejected and user notified.");
}

    public async Task<string?> GetNicImageUrlAsync(string nic)
    {
        var user = await _userRepository.GetByNICAsync(nic);
        if (user != null && !string.IsNullOrEmpty(user.NicImageUrl))
        {
            return user.NicImageUrl;
        }

        var pending = await _pendingRegistrationRepository.GetByNicOrEmailAsync(nic, nic);
        if (pending != null && !string.IsNullOrEmpty(pending.NicImageUrl))
        {
            return pending.NicImageUrl;
        }

        return null;
    }

    public async Task<(bool Success, string Message, string? AiResponse)> ValidateNicWithAiAsync(string nic)
    {
        var pending = await _pendingRegistrationRepository.GetByNicOrEmailAsync(nic, nic);
        if (pending == null || string.IsNullOrEmpty(pending.NicImageUrl))
        {
            return (false, "Could not find NIC document URL to validate.", null);
        }
        var imageUrl = pending.NicImageUrl;

        try
        {
            using var client = new System.Net.Http.HttpClient();
            client.DefaultRequestHeaders.Add("apikey", "helloworld"); // Free test key
            
            var content = new System.Net.Http.FormUrlEncodedContent(new[]
            {
                new KeyValuePair<string, string>("url", imageUrl)
            });

            // Call OCR.space API
            var response = await client.PostAsync("https://api.ocr.space/parse/image", content);
            var responseStr = await response.Content.ReadAsStringAsync();

            if (!response.IsSuccessStatusCode)
            {
                return (false, "Failed to connect to OCR service.", null);
            }

            using var document = System.Text.Json.JsonDocument.Parse(responseStr);
            var root = document.RootElement;
            
            if (root.TryGetProperty("IsErroredOnProcessing", out var isErrored) && isErrored.GetBoolean())
            {
                var errorMsg = root.GetProperty("ErrorMessage").EnumerateArray().FirstOrDefault().GetString();
                return (false, $"OCR Processing Error: {errorMsg}", null);
            }

            // Extract parsed text
            var parsedResults = root.GetProperty("ParsedResults");
            if (parsedResults.GetArrayLength() == 0)
            {
                return (true, "Success", "❌ INVALID DOCUMENT: Could not detect any readable text in this image.");
            }

            string parsedText = parsedResults[0].GetProperty("ParsedText").GetString() ?? "";

            // Regex to find Sri Lankan NIC formats (e.g. 199912345678 or 991234567V)
            var nicMatch = System.Text.RegularExpressions.Regex.Match(parsedText, @"\b(?:19|20)?\d{2}[0-35-8]\d{7}\b|\b\d{9}[vVxX]\b");
            var nameMatch = System.Text.RegularExpressions.Regex.Match(parsedText, @"(?i)(?:Name|Name in Full)[\s:]*([A-Za-z\s\.]+)");

            string report = "";
            bool isNicValid = false;
            
            if (nicMatch.Success)
            {
                var detectedNic = nicMatch.Value.ToUpper();
                if (detectedNic == pending.NIC.ToUpper())
                {
                    report += $"✅ NIC MATCH: Detected NIC ({detectedNic}) matches the registered NIC perfectly!\n\n";
                    isNicValid = true;
                }
                else
                {
                    report += $"❌ NIC MISMATCH: Detected NIC ({detectedNic}) does NOT match the registered NIC ({pending.NIC}).\n\n";
                }
            }
            else if (parsedText.Replace(" ", "").Contains(pending.NIC, StringComparison.OrdinalIgnoreCase))
            {
                report += $"✅ NIC MATCH: Found the registered NIC ({pending.NIC}) in the text, although it was not formatted perfectly.\n\n";
                isNicValid = true;
            }
            else if (parsedText.Contains("Identity Card", StringComparison.OrdinalIgnoreCase) || parsedText.Contains("National", StringComparison.OrdinalIgnoreCase))
            {
                report += $"⚠️ PARTIAL MATCH: Found ID keywords, but could not detect the registered NIC ({pending.NIC}).\n\n";
            }
            else
            {
                report += $"❌ INVALID DOCUMENT: Could not find any NIC numbers or keywords. Expected {pending.NIC}.\n\n";
            }

            bool isNameValid = false;
            if (nameMatch.Success)
            {
                report += $"Detected Name Field (OCR): {nameMatch.Groups[1].Value.Trim()}\n";
            }

            var nameParts = pending.Name.Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries);
            int matchedParts = 0;
            foreach (var part in nameParts)
            {
                if (part.Length > 2 && parsedText.Contains(part, StringComparison.OrdinalIgnoreCase))
                {
                    matchedParts++;
                }
            }

            if (matchedParts > 0)
            {
                report += $"✅ NAME MATCH: Found {matchedParts} out of {nameParts.Length} name parts from registered name ({pending.Name}) in the document.\n\n";
                isNameValid = true;
            }
            else
            {
                report += $"❌ NAME MISMATCH: Could not find the registered name ({pending.Name}) anywhere in the document.\n\n";
            }
            
            if (isNicValid && isNameValid)
            {
                report = "🌟 OVERALL RESULT: VERIFIED\nBoth Name and NIC match the registration details!\n\n" + report;
            }
            else
            {
                report = "⛔ OVERALL RESULT: MISMATCH OR UNCLEAR\nPlease verify manually.\n\n" + report;
            }

            report += "\n--- Raw Scanned Text ---\n" + parsedText.Replace("\r", " ").Replace("\n", " ");

            return (true, "Success", report);
        }
        catch (Exception ex)
        {
            return (false, $"Error contacting OCR service: {ex.Message}", null);
        }
    }


public async Task<(bool Success, string Message)> ActivateUserAsync(string nic)
{
    var pending = await _pendingRegistrationRepository.GetByNicOrEmailAsync(nic, nic);
    if (pending != null)
    {
        var newUser = new User
        {
            NIC = pending.NIC,
            Name = pending.Name,
            Email = pending.Email,
            Phone = pending.Phone,
            Address = pending.Address,
            Role = Role.PROSUMER,
            AccountStatus = AccountStatus.ACTIVE,
            EmailVerified = true,
            PasswordHash = pending.PasswordHash,
            NicImageUrl = pending.NicImageUrl,
            NicVerificationStatus = "VERIFIED",
            CreatedDate = pending.CreatedDate,
            UpdatedDate = DateTime.UtcNow
        };

        await _userRepository.CreateAsync(newUser);
        await _pendingRegistrationRepository.DeleteAsync(pending.Id);
        
        await _emailService.SendEmailAsync(pending.Email, "Account Approved", 
            "Your account has been approved by the SolarGrid backoffice team. You can now log in and book energy slots.");
            
        return (true, "User activated successfully.");
    }

    var user = await _userRepository.GetByNICAsync(nic);

    if (user == null)
    {
        return (false, "User not found.");
    }

    if (user.AccountStatus != AccountStatus.PENDING)
    {
        return (false, "Only pending accounts can be activated.");
    }

    user.AccountStatus = AccountStatus.ACTIVE;
    user.UpdatedDate = DateTime.UtcNow;

    await _userRepository.UpdateAsync(user);

    await _emailService.SendEmailAsync(user.Email, "Account Approved", 
        "Your account has been approved by the SolarGrid backoffice team. You can now log in and book energy slots.");

    return (true, "User activated successfully.");
}

public async Task<(bool Success, string Message)> DeleteUserAsync(string nic)
{
    var user = await _userRepository.GetByNICAsync(nic);
    if (user == null) return (false, "User not found.");
    
    await _userRepository.DeleteAsync(user.NIC);
    return (true, "User deleted successfully.");
}


    public async Task<(bool Success, string Message, LoginResponse? Response)> LoginAsync(
    string nicOrEmail,
    string password)
{
    var identifier = nicOrEmail.Trim();
    var user = await _userRepository.GetByNICAsync(identifier);

    if (user == null)
    {
        user = await _userRepository.GetByEmailAsync(identifier);
    }

    if (user == null)
    {
        var pending = await _pendingRegistrationRepository.GetByNicOrEmailAsync(identifier, identifier);
        if (pending != null)
        {
            var pendingValid = BCrypt.Net.BCrypt.Verify(password, pending.PasswordHash);
            if (pendingValid)
            {
                if (pending.IsEmailVerified)
                {
                    return (false, "Your account is pending backoffice approval.", null);
                }

                var unverifiedResponse = new LoginResponse
                {
                    Message = "Email verification required.",
                    Token = "",
                    NIC = pending.NIC,
                    Name = pending.Name,
                    Role = "PROSUMER",
                    AccountStatus = "UNVERIFIED",
                    RegistrationId = pending.RegistrationId
                };
                return (true, "Email verification required.", unverifiedResponse);
            }
        }
        return (false, "Invalid NIC, email, or password.", null);
    }

    if (user.AccountStatus == AccountStatus.PENDING)
    {
        return (false, "Your account is pending backoffice approval.", null);
    }

    if (user.AccountStatus != AccountStatus.ACTIVE)
    {
        return (false, $"Your account is {user.AccountStatus}.", null);
    }

    if (string.IsNullOrWhiteSpace(user.PasswordHash))
    {
        return (false, "Invalid NIC, email, or password.", null);
    }

    var passwordValid = BCrypt.Net.BCrypt.Verify(
        password,
        user.PasswordHash
    );

    if (!passwordValid)
    {
        return (false, "Invalid NIC, email, or password.", null);
    }

    var token = _jwtService.GenerateToken(user);

    var response = new LoginResponse
    {
        Message = "Login successful.",
        Token = token,
        NIC = user.NIC,
        Name = user.Name,
        Role = user.Role.ToString(),
        AccountStatus = user.AccountStatus.ToString()
    };

    return (true, "Login successful.", response);
}


public async Task<(bool Success, string Message, User? User)> InitializeBackofficeAsync(
    User user,
    string password)
{
    var existingBackoffice = await _userRepository.GetByRoleAsync(
        Role.BACKOFFICE
    );

    if (existingBackoffice != null)
    {
        return (
            false,
            "Initial Backoffice account has already been created.",
            null
        );
    }

    if (string.IsNullOrWhiteSpace(password))
    {
        return (false, "Password is required.", null);
    }

    user.Role = Role.BACKOFFICE;
    user.AccountStatus = AccountStatus.ACTIVE;

    user.PasswordHash = BCrypt.Net.BCrypt.HashPassword(password);

    user.CreatedDate = DateTime.UtcNow;
    user.UpdatedDate = DateTime.UtcNow;

    await _userRepository.CreateAsync(user);

    user.PasswordHash = string.Empty;

    return (
        true,
        "Initial Backoffice account created successfully.",
        user
    );
}

    public async Task<(bool Success, string Message)> RequestEmailChangeAsync(string nic, string newEmail)
    {
        var user = await _userRepository.GetByNICAsync(nic);
        if (user == null) return (false, "User not found.");

        var existingWithEmail = await _userRepository.GetByEmailAsync(newEmail);
        if (existingWithEmail != null && existingWithEmail.NIC != nic)
            return (false, "This email is already in use by another account.");

        // Generate OTP
        var random = new Random();
        var otp = random.Next(100000, 999999).ToString();
        
        user.PendingNewEmail = newEmail;
        user.ResetPasswordOtp = otp;
        user.ResetPasswordOtpExpiry = DateTime.UtcNow.AddMinutes(10);
        
        await _userRepository.UpdateAsync(user);

        // Send OTP to new email
        await _emailService.SendOtpEmailAsync(newEmail, otp);

        return (true, "An OTP has been sent to your new email address.");
    }

    public async Task<(bool Success, string Message)> VerifyEmailChangeAsync(string nic, string otp)
    {
        var user = await _userRepository.GetByNICAsync(nic);
        if (user == null) return (false, "User not found.");

        if (string.IsNullOrEmpty(user.PendingNewEmail) || string.IsNullOrEmpty(user.ResetPasswordOtp))
            return (false, "No pending email change request found.");

        if (user.ResetPasswordOtp != otp)
            return (false, "Invalid OTP.");

        if (user.ResetPasswordOtpExpiry < DateTime.UtcNow)
            return (false, "OTP has expired.");

        user.Email = user.PendingNewEmail;
        user.EmailVerified = true;
        
        // Clear OTP
        user.PendingNewEmail = null;
        user.ResetPasswordOtp = null;
        user.ResetPasswordOtpExpiry = null;
        user.UpdatedDate = DateTime.UtcNow;

        await _userRepository.UpdateAsync(user);

        return (true, "Email has been updated successfully.");
    }
}