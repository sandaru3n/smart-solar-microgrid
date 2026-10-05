/*
 * File: CreateProsumerPage.jsx
 * Description: Provides a form for Backoffice administrators to manually create new Prosumer accounts in a pending state without requiring mobile OTP validation.
 * Author: IT23163904_WVADK Chamara
 */
import { useState } from 'react';
import { usersApi } from '../../api';

export default function CreateProsumerPage() {
  const [formData, setFormData] = useState({
    nic: '',
    name: '',
    email: '',
    phone: '',
    address: '',
    password: '',
    role: 'PROSUMER'
  });
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  // Submits the new Prosumer account details directly to the backend API.
  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setMessage({ type: '', text: '' });

    try {
      await usersApi.createProsumer(formData);
      setMessage({ type: 'success', text: 'Prosumer account created successfully. Waiting for activation.' });
      setFormData({
        nic: '',
        name: '',
        email: '',
        phone: '',
        address: '',
        password: '',
        role: 'PROSUMER'
      });
    } catch (err) {
      setMessage({ type: 'error', text: err.message || 'Failed to create prosumer.' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex flex-col gap-6 max-w-3xl mx-auto">
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-bold text-on-surface">Create Prosumer</h1>
        <p className="text-sm text-secondary">Register a new prosumer account in the microgrid</p>
      </div>

      {message.text && (
        <div className={`p-4 rounded-lg text-sm ${message.type === 'success' ? 'bg-green-100 text-green-800' : 'bg-error-container text-on-error-container'}`}>
          {message.text}
        </div>
      )}

      <div className="rounded-2xl bg-surface-container-lowest p-8 shadow-sm border border-outline-variant/30">
        <form onSubmit={handleSubmit} className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="mb-1 block text-sm font-medium text-on-surface">NIC *</label>
            <input
              type="text"
              name="nic"
              value={formData.nic}
              onChange={handleChange}
              className="w-full rounded-lg border border-outline-variant bg-surface px-4 py-2 text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
              required
            />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-on-surface">Name *</label>
            <input
              type="text"
              name="name"
              value={formData.name}
              onChange={handleChange}
              className="w-full rounded-lg border border-outline-variant bg-surface px-4 py-2 text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
              required
            />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-on-surface">Email</label>
            <input
              type="email"
              name="email"
              value={formData.email}
              onChange={handleChange}
              className="w-full rounded-lg border border-outline-variant bg-surface px-4 py-2 text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
            />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-on-surface">Phone</label>
            <input
              type="text"
              name="phone"
              value={formData.phone}
              onChange={handleChange}
              className="w-full rounded-lg border border-outline-variant bg-surface px-4 py-2 text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
            />
          </div>
          <div className="md:col-span-2">
            <label className="mb-1 block text-sm font-medium text-on-surface">Address</label>
            <input
              type="text"
              name="address"
              value={formData.address}
              onChange={handleChange}
              className="w-full rounded-lg border border-outline-variant bg-surface px-4 py-2 text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
            />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-on-surface">Password *</label>
            <input
              type="password"
              name="password"
              value={formData.password}
              onChange={handleChange}
              className="w-full rounded-lg border border-outline-variant bg-surface px-4 py-2 text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
              required
            />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-on-surface">Role</label>
            <input
              type="text"
              value="PROSUMER"
              disabled
              className="w-full rounded-lg border border-outline-variant/50 bg-surface-container px-4 py-2 text-secondary cursor-not-allowed"
            />
          </div>
          <div className="md:col-span-2 mt-4 flex justify-end">
            <button
              type="submit"
              disabled={loading}
              className="rounded-lg bg-primary px-8 py-2.5 font-semibold text-on-primary hover:bg-primary/90 disabled:opacity-50"
            >
              {loading ? 'Creating...' : 'Create Prosumer'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
