/**
 * Error thrown for failed API calls. `message` is unchanged from before;
 * `status` (0 = network failure) and `data` (parsed body) are extra details.
 */
export class ApiError extends Error {
  constructor(message, status, data = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.data = data
  }
}

export async function request(path, options = {}) {
  const headers = { ...(options.headers || {}) }
  if (options.body) {
    headers['Content-Type'] = 'application/json'
  }
  
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  let response
  try {
    response = await fetch(`/api${path}`, { ...options, headers })
  } catch {
    throw new ApiError('Cannot reach the API. Start it with dotnet run in the api folder.', 0)
  }

  const text = await response.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = { message: text }
    }
  }

  if (!response.ok) {
    if (response.status === 401) {
      throw new ApiError(data?.message || 'Session expired. Please login again.', 401, data)
    }
    if (response.status === 403) {
      throw new ApiError('You do not have permission to perform this action.', 403, data)
    }
    if (response.status === 404) {
      throw new ApiError('User not found.', 404, data)
    }
    throw new ApiError(data?.message || `Request failed (${response.status})`, response.status, data)
  }

  return data
}

export const authApi = {
  login: (body) => request('/auth/login', { method: 'POST', body: JSON.stringify(body) }),
  forgotPassword: (body) => request('/auth/forgot-password', { method: 'POST', body: JSON.stringify(body) }),
  resetPassword: (body) => request('/auth/reset-password', { method: 'POST', body: JSON.stringify(body) }),
}

export const usersApi = {
  getAll: () => request('/users'),
  getProfile: (nic) => request(`/users/${nic}`),
  createProsumer: (body) => request('/users', { method: 'POST', body: JSON.stringify(body) }),
  createStaff: (body) => request('/users/staff', { method: 'POST', body: JSON.stringify(body) }),
  getPending: () => request('/users/pending'),
  validateNicAi: (nic) => request(`/users/${encodeURIComponent(nic)}/validate-nic-ai`, { method: 'POST' }),
  activate: (nic) => request(`/users/${nic}/activate`, { method: 'PATCH' }),
  deactivate: (nic) => request(`/users/${nic}/deactivate`, { method: 'PATCH' }),
  reactivate: (nic) => request(`/users/${nic}/reactivate`, { method: 'PATCH' }),
  reject: (nic, reason) => request(`/users/${nic}/reject`, { method: 'PATCH', body: JSON.stringify({ reason }) }),
  listProsumers: () => request('/users/prosumers'),
  getBookingProfile: (nic) => request(`/users/${encodeURIComponent(nic)}/booking`),
  deleteUser: (nic) => request(`/users/${nic}`, { method: 'DELETE' }),
  adminUpdateUser: (nic, body) => request(`/users/admin/${nic}`, { method: 'PUT', body: JSON.stringify(body) }),
}

export const stationsApi = {
  list: () => request('/stations'),
  get: (id) => request(`/stations/${id}`),
  create: (body) => request('/stations', { method: 'POST', body: JSON.stringify(body) }),
  update: (id, body) =>
    request(`/stations/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deactivate: (id) => request(`/stations/${id}/deactivate`, { method: 'PATCH' }),
  nearby: ({ latitude, longitude, radiusKm }) =>
    request(
      `/stations/nearby?latitude=${encodeURIComponent(latitude)}&longitude=${encodeURIComponent(longitude)}&radiusKm=${encodeURIComponent(radiusKm)}`,
    ),
  slots: (id, dateUtc) =>
    request(`/stations/${id}/slots?dateUtc=${encodeURIComponent(dateUtc)}`),
  schedules: (id) => request(`/stations/${id}/schedules`),
  createSchedule: (id, body) =>
    request(`/stations/${id}/schedules`, { method: 'POST', body: JSON.stringify(body) }),
  updateSchedule: (id, scheduleId, body) =>
    request(`/stations/${id}/schedules/${scheduleId}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  createSlot: (stationId, body) =>
    request(`/booking-slots/station/${stationId}`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  getSlot: (id) => request(`/booking-slots/${id}`),
}

export const reservationsApi = {
  list: () => request('/reservations/desk?pageSize=100'),
  get: (id) => request(`/reservations/desk/${encodeURIComponent(id)}`),
  create: (body) => request('/reservations/desk', { method: 'POST', body: JSON.stringify(body) }),
  update: (id, body) =>
    request(`/reservations/desk/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  cancel: (id, body) =>
    request(`/reservations/desk/${encodeURIComponent(id)}/cancel`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),
  approve: (id, body) =>
    request(`/reservations/desk/${encodeURIComponent(id)}/approve`, {
      method: 'PATCH',
      body: JSON.stringify(body ?? {}),
    }),
  reject: (id, body) =>
    request(`/reservations/desk/${encodeURIComponent(id)}/reject`, {
      method: 'PATCH',
      body: JSON.stringify(body ?? {}),
    }),
}

export const deactivationRequestsApi = {
  getPending: () => request('/deactivation-requests/pending'),
  approve: (id) => request(`/deactivation-requests/${id}/approve`, { method: 'PATCH' }),
  reject: (id, reason) => request(`/deactivation-requests/${id}/reject`, { method: 'PATCH', body: JSON.stringify({ reason }) }),
}
