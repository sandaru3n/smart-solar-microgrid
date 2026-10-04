import { useState, useEffect } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { usersApi } from '../../api';

export default function ProfilePage() {
  const { user: authUser } = useAuth();
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchProfile = async () => {
      try {
        const data = await usersApi.getProfile(authUser.nic);
        setProfile(data);
      } catch (err) {
        setError(err.message || 'Failed to load profile.');
      } finally {
        setLoading(false);
      }
    };
    if (authUser?.nic) {
      fetchProfile();
    }
  }, [authUser?.nic]);

  if (loading) {
    return <div className="p-8 text-center text-secondary">Loading profile...</div>;
  }

  if (error) {
    return <div className="p-8 text-center text-error bg-error-container rounded-lg">{error}</div>;
  }

  if (!profile) return null;

  const roleLabel =
    {
      GRID_OPERATOR: 'Grid Operator',
      BACKOFFICE: 'BackOfficer',
      PROSUMER: 'Prosumer',
    }[profile.role] || profile.role

  const statusLabel =
    profile.accountStatus === 'ACTIVE'
      ? 'Active'
      : profile.accountStatus === 'PENDING'
        ? 'Pending'
        : profile.accountStatus

  const statusClass =
    profile.accountStatus === 'ACTIVE'
      ? 'bg-[#DCFCE7] text-[#15803D]'
      : profile.accountStatus === 'PENDING'
        ? 'bg-[#FFF3B0] text-[#1C1914]'
        : 'bg-[#FEE2E2] text-[#DC2626]'

  const fields = [
    ['NIC', profile.nic],
    ['Email', profile.email || '—'],
    ['Phone', profile.phone || '—'],
    ['Address', profile.address || '—'],
  ]

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <div className="flex flex-col gap-3">
        <span className="inline-flex w-fit rounded-full bg-[#FFDD19] px-2.5 py-1 text-[11px] font-bold uppercase tracking-wider text-[#1C1914]">
          Account
        </span>
        <h1 className="text-[32px] font-extrabold tracking-tight text-[#1C1914]">My Profile</h1>
        <p className="text-sm text-[#64748B]">View your account details</p>
      </div>

      <div className="rounded-[20px] border border-[#E2E8F0] bg-white p-8 shadow-sm">
        <div className="mb-8 flex items-center gap-5">
          <span className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-[#FFDD19] text-[#1C1914]">
            <svg viewBox="0 0 24 24" className="h-8 w-8" aria-hidden="true">
              <circle cx="12" cy="8" r="3.2" fill="currentColor" />
              <path
                d="M5.2 19.2c.9-3.2 3.4-4.8 6.8-4.8s5.9 1.6 6.8 4.8"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
              />
            </svg>
          </span>
          <div className="flex min-w-0 flex-col gap-1">
            <div className="flex flex-wrap items-center gap-3">
              <h2 className="text-xl font-bold text-[#1C1914]">{profile.name}</h2>
              <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-bold ${statusClass}`}>
                <span className="h-1.5 w-1.5 rounded-full bg-current" />
                {statusLabel}
              </span>
            </div>
            <p className="text-sm font-semibold text-[#64748B]">{roleLabel}</p>
          </div>
        </div>

        <div className="grid grid-cols-1 gap-4 border-t border-[#E2E8F0] pt-8 md:grid-cols-2">
          {fields.map(([label, value]) => (
            <div key={label} className="rounded-2xl border border-[#E2E8F0] bg-[#F8FAFC] px-4 py-3">
              <span className="block text-[11px] font-bold uppercase tracking-wider text-[#78716C]">{label}</span>
              <span className="mt-1 block text-sm font-semibold text-[#1C1914]">{value}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
