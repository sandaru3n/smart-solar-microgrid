import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);

    try {
      await authApi.forgotPassword({ email });
      setSuccess('An OTP has been sent to your email address.');
      setTimeout(() => {
        navigate('/reset-password', { state: { email } });
      }, 2000);
    } catch (err) {
      setError(err.message || 'Failed to send OTP.');
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
          <h2 className="text-2xl font-bold text-on-surface">Forgot Password</h2>
          <p className="mt-2 text-sm text-secondary font-medium">Enter your email to receive an OTP.</p>
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
              className="w-full rounded-xl border border-outline-variant/60 bg-surface/50 px-5 py-3.5 text-on-surface focus:border-primary focus:bg-surface focus:outline-none focus:ring-2 focus:ring-primary/20 transition-all duration-300"
              required
              disabled={loading || success !== ''}
            />
          </div>

          <button
            type="submit"
            disabled={loading || success !== ''}
            className="w-full rounded-xl bg-primary px-5 py-4 font-bold text-on-primary shadow-md hover:bg-primary/90 focus:outline-none focus:ring-4 focus:ring-primary/30 active:scale-[0.98] transition-all disabled:opacity-50"
          >
            {loading ? 'Sending...' : 'Send OTP'}
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
