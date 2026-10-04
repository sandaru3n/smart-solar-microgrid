import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from './AuthContext';

export default function LoginPage() {
  const [nic, setNic] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [mounted, setMounted] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    setMounted(true);
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const user = await login(nic, password);
      
      // Redirect based on role
      if (user.role === 'BACKOFFICE') {
        navigate('/dashboard');
      } else if (user.role === 'GRID_OPERATOR') {
        navigate('/dashboard');
      } else {
        navigate('/dashboard/stations');
      }
    } catch (err) {
      setError(err.message || 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div 
      className="flex min-h-screen items-center justify-center p-4 transition-all duration-700"
      style={{ 
        backgroundImage: 'url(/SolarixLogin.png)', 
        backgroundSize: 'cover', 
        backgroundPosition: 'center',
        backgroundAttachment: 'fixed'
      }}
    >
      {/* Dark overlay for better readability */}
      <div className="absolute inset-0 bg-on-surface/40 backdrop-blur-[2px]"></div>

      <div 
        className={`relative w-full max-w-md transform rounded-3xl bg-surface-container-lowest/85 p-10 shadow-2xl backdrop-blur-xl border border-white/20 transition-all duration-1000 ${mounted ? 'translate-y-0 opacity-100' : 'translate-y-12 opacity-0'}`}
      >
        <div className="mb-8 text-center">
          <img 
            src="/solarix_logo.png" 
            alt="Solarix Logo" 
            className="mx-auto h-20 w-auto mb-2 drop-shadow-md animate-fade-in" 
          />
          <p className="mt-3 text-sm text-secondary font-medium">
            Welcome back! Please sign in to access your dashboard.
          </p>
        </div>

        {error && (
          <div className="mb-6 rounded-xl bg-error-container/90 backdrop-blur-sm p-4 text-sm font-semibold text-on-error-container border border-error/20 shadow-sm animate-[bounce_0.5s_ease-in-out]">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-6">
          <div className="group">
            <label className="mb-1.5 block text-xs font-bold uppercase tracking-wider text-secondary group-focus-within:text-primary transition-colors">NIC or Email</label>
            <input
              type="text"
              value={nic}
              onChange={(e) => setNic(e.target.value)}
              placeholder="Enter your NIC or email"
              autoComplete="username"
              className="w-full rounded-xl border border-outline-variant/60 bg-surface/50 px-5 py-3.5 text-on-surface placeholder:text-secondary/50 backdrop-blur-sm focus:border-primary focus:bg-surface focus:outline-none focus:ring-2 focus:ring-primary/20 transition-all duration-300"
              required
              disabled={loading}
            />
          </div>
          <div className="group">
            <label className="mb-1.5 block text-xs font-bold uppercase tracking-wider text-secondary group-focus-within:text-primary transition-colors">Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter your password"
              className="w-full rounded-xl border border-outline-variant/60 bg-surface/50 px-5 py-3.5 text-on-surface placeholder:text-secondary/50 backdrop-blur-sm focus:border-primary focus:bg-surface focus:outline-none focus:ring-2 focus:ring-primary/20 transition-all duration-300"
              required
              disabled={loading}
            />
          </div>
          <button
            type="submit"
            disabled={loading}
            className="mt-4 w-full relative overflow-hidden rounded-xl bg-primary py-3.5 text-base font-bold text-on-primary shadow-lg shadow-primary/25 hover:bg-primary/90 hover:-translate-y-0.5 active:translate-y-0 disabled:opacity-50 disabled:hover:translate-y-0 transition-all duration-300"
          >
            {loading ? (
              <span className="flex items-center justify-center gap-2">
                <svg className="h-5 w-5 animate-spin text-on-primary" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                Authenticating...
              </span>
            ) : 'Sign In'}
          </button>
        </form>
      </div>
    </div>
  );
}
