import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { stationsApi } from '../../api'
import { useAuth } from '../../auth/AuthContext'
import DashboardPage from './DashboardPage'
import { bookingsApi } from '../bookings/bookingsApi'
import { useNow } from '../bookings/context'
import { invalidateQueries, useQuery } from '../bookings/query'
import { STATUS } from '../bookings/status'
import { DISPLAY_TIME_ZONE, formatColomboRange, parseUtc } from '../bookings/time'
import { refreshDashboard, useReservationSummary } from './hooks'
import { formatRelative, greetingFor } from './time'

const OPERATOR_KEY = 'operator:'

const dateLine = new Intl.DateTimeFormat('en-GB', {
  timeZone: DISPLAY_TIME_ZONE,
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
})

const colomboDay = (time) =>
  new Intl.DateTimeFormat('en-CA', {
    timeZone: DISPLAY_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(time))

const shortRef = (id) => `…${String(id).slice(-8).toUpperCase()}`

const PILL = {
  Pending: 'bg-[#FFF3B0] text-[#1C1914]',
  Approved: 'bg-[#DCFCE7] text-[#15803D]',
  Rejected: 'bg-red-100 text-[#B91C1C]',
  Cancelled: 'bg-slate-200 text-slate-700',
  Completed: 'bg-sky-100 text-sky-800',
}

function remainingOf(slot) {
  if (typeof slot?.remainingBookings === 'number') return slot.remainingBookings
  return Math.max(0, Number(slot?.maximumBookings ?? 0) - Number(slot?.reservedBookings ?? 0))
}

async function loadOpenSlots(dateUtc) {
  const stations = await stationsApi.list()
  const active = (Array.isArray(stations) ? stations : []).filter((station) => station.isActive !== false)
  const lists = await Promise.all(
    active.map((station) =>
      stationsApi.slots(station.id, dateUtc).then(
        (slots) => (Array.isArray(slots) ? slots : []),
        () => [],
      ),
    ),
  )
  let openSlots = 0
  let places = 0
  for (const slots of lists) {
    for (const slot of slots) {
      const left = remainingOf(slot)
      if (left <= 0) continue
      openSlots += 1
      places += left
    }
  }
  const batterySlots = active.reduce((sum, station) => sum + Number(station.batteryStorageSlots || 0), 0)
  return { openSlots, places, stations: active.length, batterySlots }
}

function activityCopy(item) {
  const station = item.stationName || 'Unknown station'
  const ref = shortRef(item.id)
  switch (item.status) {
    case STATUS.Pending:
      return { title: 'Waiting for approval', detail: `${ref} · ${station}` }
    case STATUS.Approved:
      return { title: 'Reservation approved', detail: `${ref} · ${station}` }
    case STATUS.Rejected:
      return { title: 'Reservation rejected', detail: `${ref} · ${station}` }
    case STATUS.Completed:
      return { title: 'Booking completed', detail: `${ref} · ${station}` }
    case STATUS.Cancelled:
      return { title: 'Reservation cancelled', detail: `${ref} · ${station}` }
    default:
      return { title: 'Reservation updated', detail: `${ref} · ${station}` }
  }
}

function firstName(user) {
  const first = user?.name?.trim().split(/\s+/)[0]
  return first || 'Grid Operator'
}

function MetricCard({ to, label, value, hint, loading, tone, icon }) {
  return (
    <Link
      to={to}
      className="group flex flex-col rounded-2xl border border-[#E7E5E4] bg-white p-5 shadow-sm transition-shadow hover:shadow-md"
    >
      <div className="flex items-start justify-between gap-3">
        <span className={`flex h-11 w-11 items-center justify-center rounded-xl ${tone}`}>{icon}</span>
        <span className="text-[#57534E] group-hover:text-[#1C1914]" aria-hidden="true">
          →
        </span>
      </div>
      <p className="mt-4 text-sm font-semibold text-[#57534E]">{label}</p>
      {loading ? (
        <span className="mt-2 h-9 w-16 animate-pulse rounded-lg bg-[#FFDD19]/30" />
      ) : (
        <p className="mt-1 text-4xl font-extrabold tracking-tight text-[#1C1914]">{value}</p>
      )}
      <p className="mt-1 text-xs text-[#78716C]">{hint}</p>
    </Link>
  )
}

function ClockIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-6 w-6" aria-hidden="true">
      <circle cx="12" cy="12" r="8" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <path d="M12 8v4.2l2.6 1.6" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

function CheckIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-6 w-6" aria-hidden="true">
      <circle cx="12" cy="12" r="8" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <path d="M8.5 12.2 11 14.7 15.5 9.5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function CalendarIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-6 w-6" aria-hidden="true">
      <rect x="4" y="5" width="16" height="15" rx="2" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <path d="M8 3.5v3M16 3.5v3M4 10h16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

function SlotIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-6 w-6" aria-hidden="true">
      <rect x="4" y="7" width="16" height="11" rx="2" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <path d="M8 7V5.5M16 7V5.5M12 11v4M10 13h4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

function SectionCard({ title, hint, action, children }) {
  return (
    <section className="flex min-w-0 flex-1 flex-col overflow-hidden rounded-2xl border border-[#E7E5E4] bg-white shadow-sm">
      <div className="flex items-start justify-between gap-3 border-b border-[#F5F5F4] px-5 py-4">
        <div>
          <h2 className="text-base font-bold text-[#1C1914]">{title}</h2>
          {hint && <p className="mt-0.5 text-xs text-[#78716C]">{hint}</p>}
        </div>
        {action}
      </div>
      {children}
    </section>
  )
}

function ViewLink({ to, children }) {
  return (
    <Link to={to} className="shrink-0 text-sm font-semibold text-[#1C1914] hover:underline">
      {children}
    </Link>
  )
}

export function DashboardEntry() {
  const { user } = useAuth()
  if (user?.role === 'GRID_OPERATOR') return <OperatorDashboard />
  return <DashboardPage />
}

export default function OperatorDashboard() {
  const { user } = useAuth()
  const now = useNow(60_000)
  const today = colomboDay(now)
  const summary = useReservationSummary(true)
  const slots = useQuery(`${OPERATOR_KEY}slots:${today}`, () => loadOpenSlots(today))
  const recent = useQuery(`${OPERATOR_KEY}recent`, () => bookingsApi.list({ page: 1, pageSize: 6 }))
  const todayList = useQuery(`${OPERATOR_KEY}today:${today}`, () =>
    bookingsApi.list({ dateUtc: today, page: 1, pageSize: 5 }),
  )
  const completedToday = useQuery(`${OPERATOR_KEY}completed:${today}`, () =>
    bookingsApi.list({ dateUtc: today, status: STATUS.Completed, page: 1, pageSize: 1 }),
  )

  useEffect(() => {
    document.title = 'Dashboard · Smart Solar Microgrid'
  }, [])

  const sources = [summary, slots, recent, todayList, completedToday]
  const updatedAt = Math.max(0, ...sources.map((source) => source.updatedAt))
  const fetching = sources.some((source) => source.isFetching)

  const refresh = () => {
    refreshDashboard()
    invalidateQueries(OPERATOR_KEY)
  }

  const greeting = greetingFor(new Date(now))

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-[#E7E5E4] bg-white px-5 py-5 shadow-sm sm:px-6">
        <div className="flex items-center gap-4">
          <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-[#FFDD19]">
            <img src="/dashboard-solid.png" alt="" className="h-8 w-8 object-contain" />
          </span>
          <div>
            <p className="text-[11px] font-bold uppercase tracking-wider text-[#57534E]">Grid Operator</p>
            <h1 className="text-2xl font-extrabold tracking-tight text-[#1C1914]">
              {greeting}, {firstName(user)}
            </h1>
            <p className="text-sm text-[#78716C]">{dateLine.format(new Date(now))} · Sri Lanka time</p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <p className="text-xs text-[#78716C]">
            {fetching ? 'Refreshing…' : updatedAt ? `Updated ${formatRelative(updatedAt, now)}` : null}
          </p>
          <button
            type="button"
            onClick={refresh}
            disabled={fetching}
            className="rounded-full bg-[#FFDD19] px-4 py-2 text-sm font-semibold text-[#1C1914] hover:bg-[#FFE566] disabled:opacity-60"
          >
            Refresh
          </button>
        </div>
      </header>

      <section aria-label="Today at a glance" className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard
          to="/bookings/pending"
          label="Pending"
          value={summary.data?.pendingCount ?? 0}
          hint="Waiting for a decision"
          loading={summary.isLoading}
          tone="bg-[#FFDD19] text-[#1C1914]"
          icon={<ClockIcon />}
        />
        <MetricCard
          to="/bookings/all?status=Approved"
          label="Approved upcoming"
          value={summary.data?.approvedFutureCount ?? 0}
          hint="Approved slots still ahead"
          loading={summary.isLoading}
          tone="bg-[#DCFCE7] text-[#15803D]"
          icon={<CheckIcon />}
        />
        <MetricCard
          to={`/bookings/all?date=${today}`}
          label="Today's bookings"
          value={todayList.data?.totalCount ?? 0}
          hint={
            completedToday.data
              ? `${completedToday.data.totalCount} completed today`
              : 'Reservations with a slot today'
          }
          loading={todayList.isLoading}
          tone="bg-[#FFDD19] text-[#1C1914]"
          icon={<CalendarIcon />}
        />
        <MetricCard
          to="/dashboard/stations"
          label="Slots available"
          value={slots.data?.openSlots ?? 0}
          hint={
            slots.data
              ? `${slots.data.places} places open today · ${slots.data.batterySlots} battery slots`
              : 'Open booking slots across stations'
          }
          loading={slots.isLoading}
          tone="bg-[#FFF3B0] text-[#1C1914]"
          icon={<SlotIcon />}
        />
      </section>

      {(summary.isError || todayList.isError || slots.isError) && (
        <p className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-[#B91C1C]">
          Some counts could not be loaded.{' '}
          <button type="button" onClick={refresh} className="font-semibold underline">
            Try again
          </button>
        </p>
      )}

      <div className="flex flex-col gap-4 xl:flex-row xl:items-start">
        <SectionCard
          title="Recent activity"
          hint="Latest reservations across the grid"
          action={<ViewLink to="/bookings/all">View all</ViewLink>}
        >
          {recent.isLoading ? (
            <ul className="divide-y divide-[#FFDD19]/20">
              {[0, 1, 2, 3].map((row) => (
                <li key={row} className="flex items-center gap-3 bg-[#FFDD19]/10 px-5 py-4">
                  <span className="h-9 w-9 animate-pulse rounded-full bg-[#FFDD19]/40" />
                  <span className="h-4 flex-1 animate-pulse rounded bg-[#FFDD19]/30" />
                </li>
              ))}
            </ul>
          ) : recent.isError ? (
            <p className="px-5 py-8 text-sm text-[#B91C1C]">Could not load recent activity.</p>
          ) : recent.data.items.length === 0 ? (
            <p className="px-5 py-10 text-center text-sm text-[#78716C]">No reservations yet. New bookings will show up here.</p>
          ) : (
            <ul className="divide-y divide-[#FFDD19]/25">
              {recent.data.items.map((item) => {
                const copy = activityCopy(item)
                const when = parseUtc(item.updatedAtUtc || item.createdAtUtc)
                return (
                  <li key={item.id} className="flex items-center gap-3 bg-[#FFDD19]/10 px-5 py-3.5">
                    <span className={`rounded-full px-2.5 py-1 text-[11px] font-bold ${PILL[item.status] || PILL.Pending}`}>
                      {item.status || 'Update'}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold text-[#1C1914]">{copy.title}</p>
                      <p className="truncate text-xs text-[#57534E]">{copy.detail}</p>
                    </div>
                    {when && (
                      <time dateTime={when.toISOString()} className="shrink-0 text-xs text-[#78716C]">
                        {formatRelative(when.getTime(), now)}
                      </time>
                    )}
                  </li>
                )
              })}
            </ul>
          )}
        </SectionCard>

        <SectionCard
          title="Today's bookings"
          hint="Slots starting today"
          action={<ViewLink to={`/bookings/all?date=${today}`}>View today</ViewLink>}
        >
          {todayList.isLoading ? (
            <ul className="divide-y divide-[#FFDD19]/20">
              {[0, 1, 2].map((row) => (
                <li key={row} className="bg-[#FFDD19]/10 px-5 py-4">
                  <span className="block h-4 w-2/3 animate-pulse rounded bg-[#FFDD19]/30" />
                </li>
              ))}
            </ul>
          ) : todayList.isError ? (
            <p className="px-5 py-8 text-sm text-[#B91C1C]">Could not load today's bookings.</p>
          ) : todayList.data.items.length === 0 ? (
            <p className="px-5 py-10 text-center text-sm text-[#78716C]">No bookings are scheduled for today.</p>
          ) : (
            <ul className="divide-y divide-[#FFDD19]/25">
              {todayList.data.items.map((item) => (
                <li key={item.id} className="flex items-center gap-3 bg-[#FFDD19]/10 px-5 py-3.5">
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold text-[#1C1914]">{item.stationName || 'Unknown station'}</p>
                    <p className="truncate text-xs text-[#57534E]">
                      {shortRef(item.id)}
                      {item.slotStartTimeUtc ? ` · ${formatColomboRange(item.slotStartTimeUtc, item.slotEndTimeUtc)}` : ''}
                    </p>
                  </div>
                  <span className={`rounded-full px-2.5 py-1 text-[11px] font-bold ${PILL[item.status] || PILL.Pending}`}>
                    {item.status || 'Unknown'}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </SectionCard>
      </div>

    </div>
  )
}
