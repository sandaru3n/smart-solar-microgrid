import { useState, useEffect } from 'react';
import { usersApi, deactivationRequestsApi } from '../../api';

export default function UserManagementPage() {
  const [searchNic, setSearchNic] = useState('');
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  
  const [confirmModalOpen, setConfirmModalOpen] = useState(false);
  const [targetUser, setTargetUser] = useState(null);
  const [targetAction, setTargetAction] = useState(''); // 'DEACTIVATED', 'ACTIVE', 'DELETE'

  const [pendingRequests, setPendingRequests] = useState([]);
  const [requestsModalOpen, setRequestsModalOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [rejectingRequestId, setRejectingRequestId] = useState(null);

  // Edit User State
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [editForm, setEditForm] = useState({ name: '', email: '', phone: '', address: '' });

  // Toast state
  const [toast, setToast] = useState({ visible: false, message: '', type: 'success' });

  const showToast = (message, type = 'success') => {
    setToast({ visible: true, message, type });
    setTimeout(() => setToast({ visible: false, message: '', type: 'success' }), 3000);
  };

  const fetchUsers = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await usersApi.getAll();
      setUsers(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.message || 'Failed to load users.');
    } finally {
      setLoading(false);
    }
  };

  const fetchPendingRequests = async () => {
    try {
      const data = await deactivationRequestsApi.getPending();
      setPendingRequests(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('Failed to load pending deactivation requests', err);
    }
  };

  useEffect(() => {
    fetchUsers();
    fetchPendingRequests();
  }, []);

  const promptStatusChange = (user, newStatus) => {
    setTargetUser(user);
    setTargetAction(newStatus);
    setConfirmModalOpen(true);
  };

  const confirmAction = async () => {
    setConfirmModalOpen(false);
    try {
      if (targetAction === 'DEACTIVATED') {
        await usersApi.deactivate(targetUser.nic);
      } else if (targetAction === 'ACTIVE') {
        await usersApi.reactivate(targetUser.nic);
      } else if (targetAction === 'DELETE') {
        await usersApi.deleteUser(targetUser.nic);
      }
      fetchUsers();
      showToast(`User successfully ${targetAction === 'DEACTIVATED' ? 'deactivated' : targetAction === 'ACTIVE' ? 'reactivated' : 'deleted'}.`, 'success');
    } catch (err) {
      showToast(err.message || `Failed to perform action.`, 'error');
    }
  };

  const openEditModal = (user) => {
    setEditingUser(user);
    setEditForm({
      name: user.name || '',
      email: user.email || '',
      phone: user.phone || '',
      address: user.address || ''
    });
    setEditModalOpen(true);
  };

  const handleEditSubmit = async (e) => {
    e.preventDefault();
    try {
      await usersApi.adminUpdateUser(editingUser.nic, editForm);
      setEditModalOpen(false);
      fetchUsers();
      showToast('User updated successfully.', 'success');
    } catch (err) {
      showToast(err.message || 'Failed to update user.', 'error');
    }
  };

  const approveRequest = async (id) => {
    try {
      await deactivationRequestsApi.approve(id);
      fetchUsers();
      const data = await deactivationRequestsApi.getPending();
      const updated = Array.isArray(data) ? data : [];
      setPendingRequests(updated);
      if (updated.length === 0) setRequestsModalOpen(false);
      showToast('Request approved.', 'success');
    } catch (err) {
      showToast(err.message || 'Failed to approve request', 'error');
    }
  };

  const rejectRequest = async () => {
    if (!rejectReason) {
      showToast("Please enter a rejection reason.", "error");
      return;
    }
    try {
      await deactivationRequestsApi.reject(rejectingRequestId, rejectReason);
      setRejectingRequestId(null);
      setRejectReason('');
      const data = await deactivationRequestsApi.getPending();
      const updated = Array.isArray(data) ? data : [];
      setPendingRequests(updated);
      if (updated.length === 0) setRequestsModalOpen(false);
      showToast('Request rejected.', 'success');
    } catch (err) {
      showToast(err.message || 'Failed to reject request', 'error');
    }
  };

  const filteredUsers = users.filter(u => u.nic.toLowerCase().includes(searchNic.toLowerCase()));

  return (
    <div className="flex flex-col gap-6">
      <div className="flex justify-between items-center">
        <div className="flex flex-col gap-2">
          <h1 className="text-2xl font-bold text-on-surface">User Management</h1>
          <p className="text-sm text-secondary">Manage and view all registered users</p>
        </div>
        
        <button
          onClick={() => setRequestsModalOpen(true)}
          className="relative rounded-xl p-3 bg-surface-container hover:bg-surface-container-high transition-colors text-on-surface"
        >
          <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
          </svg>
          {pendingRequests.length > 0 && (
            <span className="absolute -top-1 -right-1 flex h-5 w-5 items-center justify-center rounded-full bg-red-500 text-[10px] font-bold text-white">
              {pendingRequests.length}
            </span>
          )}
        </button>
      </div>

      <div className="rounded-2xl bg-surface-container-lowest p-6 shadow-sm border border-outline-variant/30">
        <div className="flex gap-4 mb-6">
          <input
            type="text"
            placeholder="Search by NIC..."
            value={searchNic}
            onChange={(e) => setSearchNic(e.target.value)}
            className="flex-1 rounded-lg border border-outline-variant bg-surface px-4 py-2 text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          />
        </div>
        
        {error && <p className="mb-4 text-sm text-error">{error}</p>}

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-on-surface">
            <thead className="bg-surface-container text-xs uppercase text-secondary">
              <tr>
                <th className="px-6 py-4 font-semibold">NIC</th>
                <th className="px-6 py-4 font-semibold">Name</th>
                <th className="px-6 py-4 font-semibold">Email</th>
                <th className="px-6 py-4 font-semibold">Role</th>
                <th className="px-6 py-4 font-semibold">Status</th>
                <th className="px-6 py-4 font-semibold text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant/20">
              {loading ? (
                <tr>
                  <td colSpan="6" className="px-6 py-8 text-center text-secondary">Loading users...</td>
                </tr>
              ) : filteredUsers.length === 0 ? (
                <tr>
                  <td colSpan="6" className="px-6 py-8 text-center text-secondary">No users found.</td>
                </tr>
              ) : (
                filteredUsers.map((user) => (
                  <tr key={user.nic} className="hover:bg-surface-container-low transition-colors">
                    <td className="px-6 py-4 font-medium">{user.nic}</td>
                    <td className="px-6 py-4">{user.name}</td>
                    <td className="px-6 py-4">{user.email || '-'}</td>
                    <td className="px-6 py-4">
                      <span className="inline-block rounded-md bg-secondary-container px-2 py-1 text-xs font-semibold text-on-secondary-container">
                        {user.role}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <span className={`inline-block rounded-md px-2 py-1 text-xs font-semibold ${
                        user.accountStatus === 'ACTIVE' ? 'bg-green-100 text-green-800' :
                        user.accountStatus === 'PENDING' ? 'bg-yellow-100 text-yellow-800' :
                        'bg-red-100 text-red-800'
                      }`}>
                        {user.accountStatus}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex justify-end items-center gap-2">
                        {user.accountStatus === 'ACTIVE' && (
                          <button
                            onClick={() => promptStatusChange(user, 'DEACTIVATED')}
                            className="rounded-lg bg-yellow-500 px-3 py-1.5 text-xs font-semibold text-white hover:bg-yellow-600 transition-colors whitespace-nowrap shadow-sm"
                          >
                            Deactivate
                          </button>
                        )}
                        {user.accountStatus === 'DEACTIVATED' && (
                          <button
                            onClick={() => promptStatusChange(user, 'ACTIVE')}
                            className="rounded-lg bg-green-500 px-3 py-1.5 text-xs font-semibold text-white hover:bg-green-600 transition-colors whitespace-nowrap shadow-sm"
                          >
                            Reactivate
                          </button>
                        )}
                        <button
                          onClick={() => openEditModal(user)}
                          className="rounded-lg bg-primary px-3 py-1.5 text-xs font-semibold text-on-primary hover:bg-primary/90 transition-colors whitespace-nowrap shadow-sm"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => promptStatusChange(user, 'DELETE')}
                          className="rounded-lg bg-red-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-red-700 transition-colors whitespace-nowrap shadow-sm"
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>



      {/* Requests Modal */}
      {requestsModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-2xl rounded-2xl bg-surface-container-lowest p-6 shadow-xl border border-outline-variant/30 flex flex-col max-h-[90vh]">
            <div className="flex justify-between items-center mb-6">
              <h2 className="text-xl font-bold text-on-surface">Pending Deactivation Requests</h2>
              <button onClick={() => setRequestsModalOpen(false)} className="text-secondary hover:text-on-surface">
                ✕
              </button>
            </div>
            
            <div className="flex-1 overflow-y-auto">
              {pendingRequests.length === 0 ? (
                <p className="text-secondary text-center py-8">No pending requests.</p>
              ) : (
                <div className="flex flex-col gap-4">
                  {pendingRequests.map(req => (
                    <div key={req.id} className="p-4 rounded-xl border border-outline-variant/30 bg-surface">
                      <div className="flex justify-between items-start mb-2">
                        <div>
                          <p className="font-bold text-on-surface">{req.prosumerNic}</p>
                          <p className="text-xs text-secondary">{new Date(req.requestedAt).toLocaleString()}</p>
                        </div>
                        {rejectingRequestId === req.id ? (
                          <div className="flex flex-col gap-2 w-1/2">
                            <textarea
                              placeholder="Rejection reason..."
                              value={rejectReason}
                              onChange={(e) => setRejectReason(e.target.value)}
                              className="w-full rounded border border-outline-variant p-2 text-sm text-on-surface bg-surface-container-lowest focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                            />
                            <div className="flex justify-end gap-2">
                              <button onClick={() => setRejectingRequestId(null)} className="text-xs text-secondary hover:text-on-surface">Cancel</button>
                              <button onClick={rejectRequest} className="text-xs bg-red-600 text-white px-3 py-1 rounded hover:bg-red-700">Confirm Reject</button>
                            </div>
                          </div>
                        ) : (
                          <div className="flex gap-2">
                            <button onClick={() => setRejectingRequestId(req.id)} className="px-3 py-1 text-xs font-semibold rounded-lg bg-surface-container hover:bg-surface-container-high text-on-surface">Reject</button>
                            <button onClick={() => approveRequest(req.id)} className="px-3 py-1 text-xs font-semibold rounded-lg bg-primary text-on-primary hover:bg-primary/90">Accept</button>
                          </div>
                        )}
                      </div>
                      <p className="text-sm text-on-surface-variant bg-surface-container-lowest p-3 rounded-lg border border-outline-variant/10">"{req.reason}"</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Confirm Modal */}
      {confirmModalOpen && targetUser && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-2xl bg-surface-container-lowest p-6 shadow-xl border border-outline-variant/30">
            <h2 className="text-xl font-bold text-on-surface mb-2">
              Confirm {targetAction === 'DEACTIVATED' ? 'Deactivation' : targetAction === 'DELETE' ? 'Deletion' : 'Reactivation'}
            </h2>
            <p className="text-sm text-secondary mb-6">
              Are you sure you want to {targetAction === 'DEACTIVATED' ? 'deactivate' : targetAction === 'DELETE' ? 'delete' : 'reactivate'} user <strong>{targetUser.nic}</strong>?
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setConfirmModalOpen(false)}
                className="rounded-lg px-4 py-2 font-semibold text-secondary hover:bg-secondary-container hover:text-on-secondary-container transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={confirmAction}
                className={`rounded-lg px-6 py-2 font-semibold text-white transition-colors ${
                  targetAction === 'ACTIVE' ? 'bg-primary hover:bg-primary/90' : 'bg-red-600 hover:bg-red-700'
                }`}
              >
                Confirm
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Edit Modal */}
      {editModalOpen && editingUser && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-lg rounded-2xl bg-surface-container-lowest p-6 shadow-xl border border-outline-variant/30">
            <div className="flex justify-between items-center mb-6">
              <h2 className="text-xl font-bold text-on-surface">Edit User Profile</h2>
              <button onClick={() => setEditModalOpen(false)} className="text-secondary hover:text-on-surface">
                ✕
              </button>
            </div>
            <form onSubmit={handleEditSubmit} className="flex flex-col gap-4">
              <div>
                <label className="mb-1 block text-sm font-medium text-on-surface">Name</label>
                <input
                  type="text"
                  value={editForm.name}
                  onChange={(e) => setEditForm({...editForm, name: e.target.value})}
                  className="w-full rounded-lg border border-outline-variant bg-surface px-4 py-2 text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                  required
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-on-surface">Email</label>
                <input
                  type="email"
                  value={editForm.email}
                  onChange={(e) => setEditForm({...editForm, email: e.target.value})}
                  className="w-full rounded-lg border border-outline-variant bg-surface px-4 py-2 text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                  required
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-on-surface">Phone</label>
                <input
                  type="text"
                  value={editForm.phone}
                  onChange={(e) => setEditForm({...editForm, phone: e.target.value})}
                  className="w-full rounded-lg border border-outline-variant bg-surface px-4 py-2 text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-on-surface">Address</label>
                <input
                  type="text"
                  value={editForm.address}
                  onChange={(e) => setEditForm({...editForm, address: e.target.value})}
                  className="w-full rounded-lg border border-outline-variant bg-surface px-4 py-2 text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                />
              </div>
              <div className="flex justify-end gap-3 mt-4">
                <button
                  type="button"
                  onClick={() => setEditModalOpen(false)}
                  className="rounded-lg px-4 py-2 font-semibold text-secondary hover:bg-secondary-container hover:text-on-secondary-container transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="rounded-lg bg-primary px-6 py-2 font-semibold text-on-primary hover:bg-primary/90 transition-colors"
                >
                  Save Changes
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Toast Notification */}
      {toast.visible && (
        <div className={`fixed bottom-6 left-1/2 transform -translate-x-1/2 px-6 py-3 rounded-full shadow-lg text-sm font-semibold transition-all duration-300 z-[100] ${
          toast.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'
        }`}>
          {toast.message}
        </div>
      )}
    </div>
  );
}
