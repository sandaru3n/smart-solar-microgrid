import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { BlockGridOperator, HomeRedirect, ProtectedRoute } from './auth/ProtectedRoute'
import LoginPage from './auth/LoginPage'
import ForgotPasswordPage from './auth/ForgotPasswordPage'
import ResetPasswordPage from './auth/ResetPasswordPage'
import UserManagementPage from './features/users/UserManagementPage'
import PendingUsersPage from './features/users/PendingUsersPage'
import CreateProsumerPage from './features/users/CreateProsumerPage'
import CreateStaffPage from './features/users/CreateStaffPage'
import ProfilePage from './features/users/ProfilePage'
import App from './App.jsx'
import HomePage from './features/home/HomePage.jsx'
import ReservationsModule from './features/reservations/ReservationsModule.jsx'
import SlotLookupPage from './features/stations/components/SlotLookupPage.jsx'
import CreateStationPage from './features/stations/components/CreateStationPage.jsx'
import { DashboardEntry } from './features/dashboard/OperatorDashboard.jsx'
import StationsPage from './features/stations/components/StationsPage.jsx'
import WeeklySchedulePage from './features/stations/components/WeeklySchedulePage.jsx'
import BookingsLayout from './features/bookings/BookingsLayout.jsx'
import BookingsDashboardPage from './features/bookings/pages/BookingsDashboardPage.jsx'
import PendingPage from './features/bookings/pages/PendingPage.jsx'
import AllReservationsPage from './features/bookings/pages/AllReservationsPage.jsx'
import HistoryPage from './features/bookings/pages/HistoryPage.jsx'
import { BOOKINGS_ROLES } from './features/bookings/permissions.js'
import './index.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />

          <Route element={<App />}>
            <Route path="/dashboard/stations" element={<StationsPage />} />
            <Route path="/dashboard" element={<DashboardEntry />} />
            <Route element={<BlockGridOperator />}>
              <Route path="/dashboard/stations/create" element={<CreateStationPage />} />
              <Route path="/dashboard/weekly-schedule" element={<WeeklySchedulePage />} />
              <Route path="/dashboard/slot-lookup" element={<SlotLookupPage />} />
            </Route>
            <Route path="/reservations/*" element={<ReservationsModule />} />

            {/* Member 4 - Booking monitoring (staff only; Grid Operators included) */}
            <Route element={<ProtectedRoute roles={BOOKINGS_ROLES} />}>
              <Route path="/bookings" element={<BookingsLayout />}>
                <Route index element={<BookingsDashboardPage />} />
                <Route path="pending" element={<PendingPage />} />
                <Route path="all" element={<AllReservationsPage />} />
                <Route path="history" element={<HistoryPage />} />
                <Route path="*" element={<Navigate to="/bookings" replace />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute roles={['BACKOFFICE']} />}>
              <Route path="/dashboard/users">
                <Route index element={<UserManagementPage />} />
                <Route path="pending" element={<PendingUsersPage />} />
                <Route path="create-prosumer" element={<CreateProsumerPage />} />
                <Route path="create-staff" element={<CreateStaffPage />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute />}>
              <Route path="/profile" element={<ProfilePage />} />
            </Route>

            <Route path="*" element={<HomeRedirect />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  </StrictMode>,
)
