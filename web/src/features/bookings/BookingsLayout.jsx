import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import BookingsProvider from './BookingsProvider'
import { useSummary } from './context'
import { focusRing } from './components/styles'

function SectionNav() {
  const { data: summary } = useSummary()
  const tabs = [
    { to: '/bookings', label: 'Dashboard', icon: 'space_dashboard', end: true },
    { to: '/bookings/pending', label: 'Pending approvals', icon: 'pending_actions', count: summary?.pendingCount },
    { to: '/bookings/all', label: 'All reservations', icon: 'list_alt' },
    { to: '/bookings/history', label: 'History', icon: 'history' },
  ]

  return (
    <nav aria-label="Booking monitoring sections" className="-mx-1 overflow-x-auto px-1">
      <ul className="flex min-w-max gap-1 border-b border-outline-variant/30">
        {tabs.map((tab) => (
          <li key={tab.to}>
            <NavLink
              to={tab.to}
              end={tab.end}
              className={({ isActive }) =>
                `-mb-px flex items-center gap-2 border-b-2 px-3 py-2.5 text-sm font-semibold transition-colors ${focusRing} ${
                  isActive
                    ? 'border-primary text-on-surface'
                    : 'border-transparent text-secondary hover:border-outline-variant hover:text-on-surface'
                }`
              }
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
                {tab.icon}
              </span>
              {tab.label}
              {typeof tab.count === 'number' && tab.count > 0 && (
                <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-bold text-amber-900">
                  {tab.count}
                  <span className="sr-only"> pending</span>
                </span>
              )}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}

export default function BookingsLayout() {
  const { user } = useAuth()

  return (
    <BookingsProvider>
      <div className="flex flex-col gap-6">
        <div className="flex flex-col gap-1">
          <p className="text-xs font-semibold uppercase tracking-wider text-secondary">Booking monitoring</p>
          <h1 className="text-2xl font-bold text-on-surface">
            {user?.role === 'GRID_OPERATOR' ? 'Booking Management' : 'Bookings'}
          </h1>
        </div>

        <SectionNav />
        <Outlet />
      </div>
    </BookingsProvider>
  )
}
