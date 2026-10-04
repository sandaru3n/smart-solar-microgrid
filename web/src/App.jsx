import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'

const stationLinks = [
  { to: '/dashboard', label: 'Dashboard', end: true },
  { to: '/dashboard/stations', label: 'Stations', end: false },
  { to: '/dashboard/slot-lookup', label: 'Slot lookup', end: true },
  { to: '/reservations', label: 'Reservations', end: false },
]

const getLinks = (role) => {
  if (role === 'BACKOFFICE') {
    return [
      ...stationLinks,
      { to: '/dashboard/users/create-staff', label: 'Create staff', end: true },
      { to: '/dashboard/users', label: 'User Management', end: true },
      { to: '/dashboard/users/pending', label: 'Pending Activations', end: true },
      { to: '/bookings', label: 'Bookings', end: false },
      { to: '/profile', label: 'My Profile', end: true },
    ]
  }
  if (role === 'GRID_OPERATOR') {
    const reservationIcon = { line: '/reservation-line.png', solid: '/reservation-solid.png' }
    return [
      { to: '/dashboard/stations', label: 'Stations', end: true, icon: { line: '/stations-line.png', solid: '/stations-solid.png' } },
      {
        to: '/reservations',
        label: 'Reservations',
        end: true,
        match: 'reservations-home',
        icon: reservationIcon,
        children: [
          { to: '/reservations/new', label: 'Create reservations', end: true, nested: true },
          { to: '/reservations/pending', label: 'Pending reservations', end: true, nested: true },
        ],
      },
      { to: '/bookings', label: 'Bookings History', end: false, icon: { line: '/histroy-line.png', solid: '/histroy-solid.png' } },
      { to: '/profile', label: 'My Profile', end: true, icon: { line: '/profile-line.png', solid: '/profile-solid.png' } },
    ]
  }
  if (role === 'PROSUMER') {
    return [
      { to: '/reservations', label: 'Reservations', end: false },
      { to: '/profile', label: 'My Profile', end: true },
    ]
  }
  return [
    ...stationLinks,
    { to: '/dashboard/users/create-staff', label: 'Create staff', end: true },
  ]
}

const crumbs = {
  '/dashboard': 'Dashboard',
  '/dashboard/stations': 'Stations',
  '/dashboard/stations/create': 'Create station',
  '/dashboard/slot-lookup': 'Slot lookup',
  '/dashboard/users': 'User Management',
  '/dashboard/users/pending': 'Pending Activations',
  '/dashboard/users/create-prosumer': 'Create Prosumer',
  '/dashboard/users/create-staff': 'Create Staff',
  '/profile': 'My Profile',
}

function isReservationNavActive(link, pathname, routerIsActive) {
  if (link.match === 'reservations-home') {
    if (pathname === '/reservations') return true
    if (!pathname.startsWith('/reservations/')) return false
    const rest = pathname.slice('/reservations/'.length)
    return rest !== 'new' && !rest.startsWith('new/') && rest !== 'pending' && !rest.startsWith('pending/')
  }
  if (link.to === '/reservations/new') {
    return pathname === '/reservations/new' || pathname.startsWith('/reservations/new/')
  }
  if (link.to === '/reservations/pending') {
    return pathname === '/reservations/pending' || pathname.startsWith('/reservations/pending/')
  }
  return routerIsActive
}

function reservationCrumb(pathname) {
  if (pathname === '/reservations/new' || pathname.startsWith('/reservations/new/')) return 'Create reservations'
  if (pathname === '/reservations/pending' || pathname.startsWith('/reservations/pending/')) return 'Pending reservations'
  if (pathname === '/reservations' || pathname.startsWith('/reservations/')) return 'Reservations'
  return null
}

function navClass(isActive, nested, hasMenu) {
  const inset = nested ? 'ml-10' : ''
  const pad = hasMenu ? 'py-2.5 pl-4 pr-10' : 'px-4 py-2.5'
  return isActive
    ? `mx-3 flex items-center gap-2.5 ${inset} rounded-full bg-[#FFDD19] ${pad} font-semibold text-[#1C1914]`
    : `mx-3 flex items-center gap-2.5 ${inset} rounded-full ${pad} text-[#3F3F46] hover:bg-[#FFF3B0] hover:text-[#1C1914]`
}

export default function App() {
  const { pathname } = useLocation()
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/')
  }

  const [reservationsOpen, setReservationsOpen] = useState(() => pathname.startsWith('/reservations'))

  useEffect(() => {
    if (pathname.startsWith('/reservations')) setReservationsOpen(true)
  }, [pathname])

  // Use dynamic links based on role, fallback to [] if user is null
  const links = user ? getLinks(user.role) : getLinks(null)
  const mobileLinks = links.flatMap((link) => (link.type === 'label' ? [] : [link, ...(link.children ?? [])]))

  return (
    <div className="min-h-screen bg-surface font-body-sm text-body-sm text-on-surface antialiased">
      <aside className="fixed left-0 top-0 hidden h-full w-60 flex-col justify-between border-r border-[#E7E5E4] bg-white py-6 lg:flex">
        <div className="flex flex-col gap-6">
          <Link
            to={user?.role === 'BACKOFFICE' ? '/dashboard/users' : user?.role === 'GRID_OPERATOR' ? '/reservations' : '/dashboard/stations'}
            className="flex flex-col items-center gap-2 px-5"
          >
            <span className="flex items-center gap-2.5">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-[#FFDD19] shadow-sm">
                <svg viewBox="0 0 24 24" className="h-5 w-5 text-[#1C1914]" aria-hidden="true">
                  <circle cx="12" cy="12" r="4" fill="currentColor" />
                  <g stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                    <path d="M12 2.5v2.2M12 19.3v2.2M4.6 4.6l1.6 1.6M17.8 17.8l1.6 1.6M2.5 12h2.2M19.3 12h2.2M4.6 19.4l1.6-1.6M17.8 6.2l1.6-1.6" />
                  </g>
                </svg>
              </span>
              <img src="/solarix_logo.png" alt="Solarix" className="h-10 w-auto max-w-full object-contain" />
            </span>
            <span className="text-[11px] font-semibold text-[#3F3F46]">
              {user?.role === 'BACKOFFICE' ? 'BackOfficer' : user?.role === 'GRID_OPERATOR' ? 'Grid Operator' : 'Station Management'}
            </span>
          </Link>
          <nav className="mt-4 flex flex-col gap-1">
            {links.map((link) =>
              link.type === 'label' ? (
                <span
                  key={link.label}
                  className="mx-3 flex items-center gap-2.5 px-4 pt-4 pb-1 text-[11px] font-semibold uppercase tracking-wider text-[#78716C]"
                >
                  {link.label}
                </span>
              ) : (
                <div key={link.to} className="flex flex-col gap-1">
                  <div className="relative">
                    <NavLink
                      to={link.to}
                      end={link.end}
                      className={({ isActive }) =>
                        navClass(isReservationNavActive(link, pathname, isActive), link.nested, Boolean(link.children))
                      }
                    >
                      {({ isActive }) => {
                        const active = isReservationNavActive(link, pathname, isActive)
                        return (
                          <>
                            {link.icon && (
                              <img
                                src={active ? link.icon.solid : link.icon.line}
                                alt=""
                                className="h-5 w-5 shrink-0 object-contain"
                              />
                            )}
                            <span className="min-w-0">{link.label}</span>
                          </>
                        )
                      }}
                    </NavLink>
                    {link.children && (
                      <button
                        type="button"
                        aria-expanded={reservationsOpen}
                        aria-label={reservationsOpen ? 'Hide reservation menus' : 'Show reservation menus'}
                        onClick={() => setReservationsOpen((open) => !open)}
                        className="absolute top-1/2 right-5 flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-full text-[#1C1914]"
                      >
                        <svg
                          viewBox="0 0 20 20"
                          className={`h-4 w-4 transition-transform ${reservationsOpen ? '' : '-rotate-90'}`}
                          aria-hidden="true"
                        >
                          <path
                            d="M5 7.5 10 12.5 15 7.5"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="1.8"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                          />
                        </svg>
                      </button>
                    )}
                  </div>
                  {link.children && reservationsOpen &&
                    link.children.map((child) => (
                      <NavLink
                        key={child.to}
                        to={child.to}
                        end={child.end}
                        className={({ isActive }) =>
                          navClass(isReservationNavActive(child, pathname, isActive), child.nested)
                        }
                      >
                        {child.label}
                      </NavLink>
                    ))}
                </div>
              ),
            )}
          </nav>
        </div>
        <div className="border-t border-outline-variant/20 px-6 pt-4 flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="inline-block h-2 w-2 rounded-full bg-primary-container" />
              <span className="text-label-sm text-secondary">
                {user?.role === 'BACKOFFICE' ? 'BackOfficer' : user?.role === 'GRID_OPERATOR' ? 'Grid Operator' : user?.role || 'Station desk'}
              </span>
            </div>
          </div>
          <button
            type="button"
            onClick={handleLogout}
            className="flex w-full items-center gap-2 rounded-lg bg-error/10 px-4 py-2 text-sm font-semibold text-error hover:bg-error/20 transition-colors"
          >
            <span className="material-symbols-outlined text-[18px]">logout</span>
            Logout
          </button>
        </div>
      </aside>

      <div className="flex min-h-screen flex-col bg-surface lg:pl-60">
        <header className="fixed top-0 right-0 left-0 z-40 flex h-14 items-center justify-between border-b border-outline-variant/20 bg-surface-container-lowest/80 px-4 backdrop-blur-md lg:left-60 lg:px-8">
          <div className="flex items-center gap-3">
            <span className="hidden text-label-sm uppercase tracking-wider text-secondary sm:inline">
              Smart Solar Microgrid
            </span>
            <span className="hidden text-outline-variant sm:inline">/</span>
            <span className="text-label-md font-semibold text-on-surface">
              {reservationCrumb(pathname) ||
                (pathname.startsWith('/bookings')
                  ? user?.role === 'GRID_OPERATOR'
                    ? 'Bookings History'
                    : 'Bookings'
                  : crumbs[pathname] || 'Dashboard')}
            </span>
          </div>
          <div className="flex items-center gap-4">
            <span className="text-sm font-semibold hidden sm:inline">
              {user?.role === 'BACKOFFICE' ? 'BackOfficer' : user?.role === 'GRID_OPERATOR' ? 'Grid Operator' : user?.name}
            </span>
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[#FFDD19] text-[#1C1914]">
              <span className="material-symbols-outlined text-[18px]">person</span>
            </div>
            {user && (
              <button
                onClick={handleLogout}
                className="lg:hidden text-error font-semibold text-sm ml-2"
              >
                Logout
              </button>
            )}
          </div>
        </header>

        <nav className="fixed top-14 right-0 left-0 z-30 flex gap-1 overflow-x-auto border-b border-outline-variant/20 bg-surface-container-lowest px-3 py-2 lg:hidden">
          {mobileLinks.map((link) =>
            link.type === 'label' ? null : (
              <NavLink
                key={link.to}
                to={link.to}
                end={link.end}
                className={({ isActive }) =>
                  isReservationNavActive(link, pathname, isActive)
                    ? 'shrink-0 rounded-full bg-[#FFDD19] px-3 py-1.5 text-label-md font-semibold text-[#1C1914]'
                    : 'shrink-0 rounded-full px-3 py-1.5 text-label-md text-[#3F3F46]'
                }
              >
                {link.label}
              </NavLink>
            ),
          )}
        </nav>

        <main className="w-full flex-1 pt-28 lg:pt-14">
          <div className="mx-auto w-full max-w-7xl p-4 sm:p-8 lg:p-10">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}

