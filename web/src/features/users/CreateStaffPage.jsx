/*
 * File: CreateStaffPage.jsx
 * Description: Provides a form for Backoffice administrators to create new Backoffice or Grid Operator user accounts directly without OTP verification.
 * Author: IT23163904_WVADK Chamara
 */
import { useState } from 'react';
import { usersApi } from '../../api';

export default function CreateStaffPage() {
  const [formData, setFormData] = useState({
    nic: '',
    name: '',
    email: '',
    phone: '',
    address: '',
    password: '',
    role: 'GRID_OPERATOR'
  });
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });

  const [confirmModalOpen, setConfirmModalOpen] = useState(false);

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  // Validates the form data and displays a confirmation modal before submission.
  const handleInitialSubmit = (e) => {
    e.preventDefault();
    setConfirmModalOpen(true);
  };

  // Submits the new staff account details to the backend API after confirmation.
  const handleSubmit = async () => {
    setConfirmModalOpen(false);
    setLoading(true);
    setMessage({ type: '', text: '' });

    try {
      await usersApi.createStaff(formData);
      setMessage({ type: 'success', text: 'Staff account created successfully.' });
      setFormData({
        nic: '',
        name: '',
        email: '',
        phone: '',
        address: '',
        password: '',
        role: 'GRID_OPERATOR'
      });
    } catch (err) {
      setMessage({ type: 'error', text: err.message || 'Failed to create staff account.' });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex flex-col gap-6 max-w-3xl mx-auto">
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-bold text-on-surface">Create user</h1>
        <p className="text-sm text-secondary">Create a web user with the Backoffice or Grid Operator role.</p>
      </div>

      {message.text && (
        <div className={`p-4 rounded-lg text-sm ${message.type === 'success' ? 'bg-green-100 text-green-800' : 'bg-error-container text-on-error-container'}`}>
          {message.text}
        </div>
      )}

      <div className="rounded-2xl bg-surface-container-lowest p-8 shadow-sm border border-outline-variant/30">
        <form onSubmit={handleInitialSubmit} className="grid grid-cols-1 md:grid-cols-2 gap-6">
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
            <label className="mb-1 block text-sm font-medium text-on-surface">Email *</label>
            <input
              type="email"
              name="email"
              value={formData.email}
              onChange={handleChange}
              className="w-full rounded-lg border border-outline-variant bg-surface px-4 py-2 text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
              required
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
            <label className="mb-1 block text-sm font-medium text-on-surface">Role *</label>
            <select
              name="role"
              value={formData.role}
              onChange={handleChange}
              className="w-full rounded-lg border border-outline-variant bg-surface px-4 py-2 text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
              required
            >
              <option value="BACKOFFICE">Backoffice</option>
              <option value="GRID_OPERATOR">Grid Operator</option>
              <option value="PROSUMER">Prosumer</option>
            </select>
          </div>
          <div className="md:col-span-2 mt-4 flex justify-end">
            <button
              type="submit"
              disabled={loading}
              className="rounded-lg bg-primary px-8 py-2.5 font-semibold text-on-primary hover:bg-primary/90 disabled:opacity-50"
            >
              {loading ? 'Creating...' : 'Create User'}
            </button>
          </div>
        </form>
      </div>

      {/* Confirm Modal */}
      {confirmModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-2xl bg-surface-container-lowest p-6 shadow-xl border border-outline-variant/30">
            <h2 className="text-xl font-bold text-on-surface mb-2">Confirm User Creation</h2>
            <p className="text-sm text-secondary mb-6">
              Are you sure you want to create a <strong>{formData.role}</strong> account for <strong>{formData.name}</strong>?
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setConfirmModalOpen(false)}
                className="rounded-lg px-4 py-2 font-semibold text-secondary hover:bg-secondary-container hover:text-on-secondary-container transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleSubmit}
                className="rounded-lg bg-primary px-6 py-2 font-semibold text-on-primary hover:bg-primary/90 transition-colors"
              >
                Confirm
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
