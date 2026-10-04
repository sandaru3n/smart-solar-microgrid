import { useState, useEffect } from 'react';
import { usersApi } from '../../api';

export default function PendingUsersPage() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  
  // Modals state
  const [aiModalOpen, setAiModalOpen] = useState(false);
  const [aiResult, setAiResult] = useState('');
  const [aiLoading, setAiLoading] = useState(false);
  
  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [rejectNic, setRejectNic] = useState('');
  const [rejectReason, setRejectReason] = useState('');
  const [isRejecting, setIsRejecting] = useState(false);

  // New state
  const [nicModalOpen, setNicModalOpen] = useState(false);
  const [nicImageUrl, setNicImageUrl] = useState('');
  
  const [activateModalOpen, setActivateModalOpen] = useState(false);
  const [activateNic, setActivateNic] = useState('');
  const [isActivating, setIsActivating] = useState(false);
  const [toastMessage, setToastMessage] = useState('');

  const showToast = (message) => {
    setToastMessage(message);
    setTimeout(() => setToastMessage(''), 3000);
  };

  const [alertModalOpen, setAlertModalOpen] = useState(false);
  const [alertMessage, setAlertMessage] = useState('');

  const showAlert = (message) => {
    setAlertMessage(message);
    setAlertModalOpen(true);
  };

  const fetchPending = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await usersApi.getPending();
      setUsers(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.message || 'Failed to load pending users.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPending();
  }, []);

  const handleViewNIC = async (nic) => {
    try {
      const token = localStorage.getItem('token') || sessionStorage.getItem('token');
      const res = await fetch(`/api/users/${encodeURIComponent(nic)}/nic-document`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (!res.ok) throw new Error('Failed to load NIC document.');
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      setNicImageUrl(url);
      setNicModalOpen(true);
    } catch (err) {
      showAlert(err.message);
    }
  };

  const handleAiValidate = async (nic) => {
    setAiLoading(true);
    try {
      const response = await usersApi.validateNicAi(nic);
      setAiResult(response.aiResponse || 'No response from OCR.');
      setAiModalOpen(true);
    } catch (err) {
      setAiResult(`Error: ${err.message}`);
      setAiModalOpen(true);
    } finally {
      setAiLoading(false);
    }
  };

  const handleActivate = async (nic) => {
    setActivateNic(nic);
    setActivateModalOpen(true);
  };

  const confirmActivate = async () => {
    setIsActivating(true);
    try {
      await usersApi.activate(activateNic);
      setActivateModalOpen(false);
      showToast('Activation completed');
      fetchPending();
    } catch (err) {
      showAlert(err.message || 'Failed to activate user.');
    } finally {
      setIsActivating(false);
    }
  };

  const openRejectModal = (nic) => {
    setRejectNic(nic);
    setRejectReason('');
    setRejectModalOpen(true);
  };

  const submitReject = async () => {
    if (rejectReason.trim() === '') {
      showAlert('You must provide a rejection reason.');
      return;
    }
    setIsRejecting(true);
    try {
      await usersApi.reject(rejectNic, rejectReason);
      showAlert('User registration rejected. An email has been sent.');
      setRejectModalOpen(false);
      fetchPending();
    } catch (err) {
      showAlert(err.message || 'Failed to reject user.');
    } finally {
      setIsRejecting(false);
    }
  };

  return (
    <div className="flex flex-col gap-6 relative">
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-bold text-on-surface">Pending Activations</h1>
        <p className="text-sm text-secondary">Review and activate pending prosumer accounts</p>
      </div>

      {error && <p className="text-sm text-error bg-error-container p-4 rounded-lg">{error}</p>}

      <div className="rounded-2xl bg-surface-container-lowest shadow-sm border border-outline-variant/30 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-on-surface">
            <thead className="bg-surface-container text-xs uppercase text-secondary">
              <tr>
                <th className="px-6 py-4 font-semibold">NIC</th>
                <th className="px-6 py-4 font-semibold">Name</th>
                <th className="px-6 py-4 font-semibold">Email</th>
                <th className="px-6 py-4 font-semibold">Created</th>
                <th className="px-6 py-4 font-semibold text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant/20">
              {loading ? (
                <tr><td colSpan="5" className="px-6 py-8 text-center text-secondary">Loading...</td></tr>
              ) : users.length === 0 ? (
                <tr><td colSpan="5" className="px-6 py-8 text-center text-secondary">No pending users found.</td></tr>
              ) : (
                users.map((user) => (
                  <tr key={user.nic} className="hover:bg-surface-container-low transition-colors">
                    <td className="px-6 py-4 font-medium">{user.nic}</td>
                    <td className="px-6 py-4">{user.name}</td>
                    <td className="px-6 py-4">{user.email || '-'}</td>
                    <td className="px-6 py-4">
                      {user.createdAt ? new Date(user.createdAt).toLocaleDateString() : '-'}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <div className="flex justify-end gap-2">
                        <button
                          onClick={() => handleViewNIC(user.nic)}
                          className="rounded-lg border border-secondary px-3 py-1.5 text-xs font-semibold text-secondary hover:bg-secondary hover:text-white transition-colors"
                        >
                          View NIC
                        </button>
                        <button
                          onClick={() => handleAiValidate(user.nic)}
                          disabled={aiLoading}
                          className={`rounded-lg px-3 py-1.5 text-xs font-semibold text-on-tertiary ${aiLoading ? 'bg-tertiary/50 cursor-not-allowed' : 'bg-tertiary hover:bg-tertiary/90'}`}
                        >
                          {aiLoading ? 'Scanning...' : 'AI Verify'}
                        </button>
                        <button
                          onClick={() => handleActivate(user.nic)}
                          className="rounded-lg bg-primary px-3 py-1.5 text-xs font-semibold text-on-primary hover:bg-primary/90"
                        >
                          Activate
                        </button>
                        <button
                          onClick={() => openRejectModal(user.nic)}
                          className="rounded-lg bg-error px-3 py-1.5 text-xs font-semibold text-on-error hover:bg-error/90"
                        >
                          Reject
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

      {/* AI Result Modal */}
      {aiModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-lg rounded-2xl bg-surface-container-lowest p-6 shadow-xl border border-outline-variant/30 flex flex-col max-h-[80vh]">
            <h2 className="text-xl font-bold text-on-surface mb-4">AI Validation Result</h2>
            <div className="flex-1 overflow-y-auto mb-6 bg-surface p-4 rounded-xl border border-outline-variant/50 text-sm whitespace-pre-wrap font-mono text-on-surface">
              {aiResult}
            </div>
            <div className="flex justify-end">
              <button
                onClick={() => setAiModalOpen(false)}
                className="rounded-lg bg-primary px-6 py-2 font-semibold text-on-primary hover:bg-primary/90"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Reject Modal */}
      {rejectModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-2xl bg-surface-container-lowest p-6 shadow-xl border border-outline-variant/30">
            <h2 className="text-xl font-bold text-on-surface mb-2">Reject Registration</h2>
            <p className="text-sm text-secondary mb-4">
              Please provide a reason for rejecting the registration of <strong>{rejectNic}</strong>. This will be emailed directly to the applicant.
            </p>
            <textarea
              className="w-full h-32 p-3 rounded-xl border border-outline-variant bg-surface text-on-surface focus:border-error focus:ring-1 focus:ring-error mb-6 resize-none"
              placeholder="e.g., The uploaded NIC image is too blurry. Please upload a clearer picture."
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
            />
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setRejectModalOpen(false)}
                disabled={isRejecting}
                className="rounded-lg px-4 py-2 font-semibold text-secondary hover:bg-secondary-container hover:text-on-secondary-container transition-colors disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                onClick={submitReject}
                disabled={isRejecting}
                className="rounded-lg bg-error px-6 py-2 font-semibold text-on-error hover:bg-error/90 disabled:opacity-50"
              >
                {isRejecting ? 'Rejecting...' : 'Confirm Reject'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Activate Modal */}
      {activateModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-2xl bg-surface-container-lowest p-6 shadow-xl border border-outline-variant/30">
            <h2 className="text-xl font-bold text-on-surface mb-2">Confirm Activation</h2>
            <p className="text-sm text-secondary mb-6">
              Are you sure you want to activate user <strong>{activateNic}</strong>?
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setActivateModalOpen(false)}
                className="rounded-lg px-4 py-2 font-semibold text-secondary hover:bg-secondary-container hover:text-on-secondary-container transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={confirmActivate}
                disabled={isActivating}
                className="rounded-lg bg-primary px-6 py-2 font-semibold text-on-primary hover:bg-primary/90 disabled:opacity-50"
              >
                {isActivating ? 'Activating...' : 'Confirm'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Alert Modal */}
      {alertModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-sm rounded-2xl bg-surface-container-lowest p-6 shadow-xl border border-outline-variant/30 text-center">
            <p className="text-base text-on-surface mb-6">{alertMessage}</p>
            <button
              onClick={() => setAlertModalOpen(false)}
              className="rounded-lg bg-primary px-6 py-2 font-semibold text-on-primary hover:bg-primary/90 w-full"
            >
              OK
            </button>
          </div>
        </div>
      )}

      {/* NIC Image Modal */}
      {nicModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm" onClick={() => setNicModalOpen(false)}>
          <div className="relative max-w-4xl max-h-[90vh] flex flex-col bg-surface-container-lowest rounded-2xl overflow-hidden shadow-2xl" onClick={e => e.stopPropagation()}>
            <div className="flex justify-between items-center p-4 border-b border-outline-variant/30">
              <h2 className="text-lg font-bold text-on-surface">NIC Document</h2>
              <button onClick={() => setNicModalOpen(false)} className="text-secondary hover:text-on-surface font-bold text-xl px-2">&times;</button>
            </div>
            <div className="overflow-auto p-4 flex justify-center items-center bg-surface-container-low">
              <img src={nicImageUrl} alt="NIC Document" className="max-w-full max-h-[70vh] object-contain rounded-lg" />
            </div>
          </div>
        </div>
      )}
      {/* Toast Notification */}
      {toastMessage && (
        <div className="fixed bottom-6 right-6 z-[60] animate-[slide-up_0.3s_ease-out]">
          <div className="rounded-xl bg-surface-container-high px-6 py-3 shadow-lg border border-outline-variant/30 flex items-center gap-3">
            <span className="text-primary material-symbols-outlined">check_circle</span>
            <p className="font-semibold text-on-surface">{toastMessage}</p>
          </div>
        </div>
      )}
    </div>
  );
}
