import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { authApi } from '../api';

export default function ResetPasswordPage() {
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (location.state?.email) {
      setEmail(location.state.email);
    }
  }, [location]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    const pwdRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,15}$/;
    if (!pwdRegex.test(newPassword)) {
      setError('Password must be 8-15 characters long, contain at least one uppercase letter, one lowercase letter, and one number.');
      return;
    }

    setLoading(true);
    try {
      await authApi.resetPassword({ email, otp, newPassword });
      setSuccess('Password has been reset successfully. You can now login.');
      setTimeout(() => navigate('/login'), 3000);
    } catch (err) {
      setError(err.message || 'Failed to reset password.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div 
      className="flex min-h-screen items-center justify-center p-4"
      style={{ backgroundImage: 'url(/SolarixLogin.png)', backgroundSize: 'cover', backgroundPosition: 'center', backgroundAttachment: 'fixed' }}
    >
      <div className="absolute inset-0 bg-on-surface/40 backdrop-blur-[2px]"></div>

      <div className="relative w-full max-w-md rounded-3xl bg-surface-container-lowest/85 p-10 shadow-2xl backdrop-blur-xl border border-white/20">
        <div className="mb-8 text-center">
          <h2 className="text-2xl font-bold text-on-surface">Reset Password</h2>
          <p className="mt-2 text-sm text-secondary font-medium">Enter your OTP and new password.</p>
        </div>

        {error && <div className="mb-6 rounded-xl bg-error-container/90 p-4 text-sm font-semibold text-on-error-container">{error}</div>}
        {success && <div className="mb-6 rounded-xl bg-primary-container p-4 text-sm font-semibold text-on-primary-container">{success}</div>}

        <form onSubmit={handleSubmit} className="flex flex-col gap-6">
          <div className="group">
            <label className="mb-1.5 block text-xs font-bold uppercase tracking-wider text-secondary">Email Address</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="Enter your email"
              className="w-full rounded-xl border border-outline-variant/60 bg-surface/50 px-5 py-3.5 text-on-surface focus:border-primary focus:bg-surface focus:outline-none focus:ring-2 focus:ring-primary/20"
              required
              disabled={loading || success !== ''}
            />
          </div>

          <div className="group">
            <label className="mb-1.5 block text-xs font-bold uppercase tracking-wider text-secondary">OTP Code</label>
            <input
              type="text"
              value={otp}
              onChange={(e) => setOtp(e.target.value)}
              placeholder="Enter the OTP from your email"
              className="w-full rounded-xl border border-outline-variant/60 bg-surface/50 px-5 py-3.5 text-on-surface tracking-widest focus:border-primary focus:bg-surface focus:outline-none focus:ring-2 focus:ring-primary/20"
              required
              disabled={loading || success !== ''}
            />
          </div>

          <div className="group">
            <label className="mb-1.5 block text-xs font-bold uppercase tracking-wider text-secondary">New Password</label>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder="Enter your new password"
              className="w-full rounded-xl border border-outline-variant/60 bg-surface/50 px-5 py-3.5 text-on-surface focus:border-primary focus:bg-surface focus:outline-none focus:ring-2 focus:ring-primary/20"
              required
              disabled={loading || success !== ''}
            />
          </div>

          <button
            type="submit"
            disabled={loading || success !== ''}
            className="w-full rounded-xl bg-primary px-5 py-4 font-bold text-on-primary shadow-md hover:bg-primary/90 focus:outline-none focus:ring-4 focus:ring-primary/30 active:scale-[0.98] transition-all disabled:opacity-50"
          >
            {loading ? 'Resetting...' : 'Reset Password'}
          </button>

          <button
            type="button"
            onClick={() => navigate('/login')}
            className="w-full text-sm font-bold text-secondary hover:text-on-surface transition-colors"
          >
            Back to Login
          </button>
        </form>
      </div>
    </div>
  );
}
