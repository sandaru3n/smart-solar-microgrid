import { Link } from 'react-router-dom'
import { PlusIcon, SearchIcon, UserPlusIcon } from '../icons'
import { cardClass, focusRing, tones } from './styles'

/** Same destinations as the sidebar; `backofficeOnly` routes are guarded by ProtectedRoute. */
const ACTIONS = [
  { to: '/dashboard/stations/create', label: 'Create station', icon: PlusIcon },
  { to: '/dashboard/users/create-staff', label: 'Create user', icon: UserPlusIcon, backofficeOnly: true },
  { to: '/dashboard/slot-lookup', label: 'Slot lookup', icon: SearchIcon },
]

export default function QuickActions({ isBackoffice }) {
  const actions = ACTIONS.filter((action) => isBackoffice || !action.backofficeOnly)

  return (
    <section aria-labelledby="dash-actions-heading" className={`${cardClass} flex flex-col gap-4 p-5`}>
      <h2 id="dash-actions-heading" className="text-base font-bold">
        Quick Actions
      </h2>
      <ul className="grid grid-cols-2 gap-2.5">
        {actions.map(({ to, label, icon: Icon }) => (
          <li key={to}>
            <Link
              to={to}
              className={`flex h-full flex-col items-start gap-2 rounded-xl border border-(--dash-border) p-3 text-sm font-semibold transition-colors hover:bg-(--dash-bg) ${focusRing}`}
            >
              <span className={`flex h-8 w-8 items-center justify-center rounded-lg ${tones.neutral.badge}`}>
                <Icon className="h-4 w-4" />
              </span>
              {label}
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
