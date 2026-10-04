import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { NavLink, Outlet, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { bookingsApi, describeError } from './bookingsApi'
import { BOOKINGS_KEY, BookingsContext, DRAWER_PARAM, queryKeys, useSummary } from './context'
import { canApprove as roleCanApprove } from './permissions'
import { invalidateQueries, setQueryData, useSessionGuard } from './query'
import ConfirmDialog from './components/ConfirmDialog'
import ReservationDrawer from './components/ReservationDrawer'
import Toasts from './components/Toasts'
import { focusRing } from './components/styles'
import { DateTime, Reference } from './components/ui'
import { formatColomboRange } from './time'

const TOAST_MS = 6000

const shortRef = (id) => `…${String(id).slice(-8).toUpperCase()}`

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
  const guard = useSessionGuard()
  const [searchParams, setSearchParams] = useSearchParams()
  const drawerId = searchParams.get(DRAWER_PARAM)

  const [toasts, setToasts] = useState([])
  const [pending, setPending] = useState({})
  const [rejectTarget, setRejectTarget] = useState(null)
  const inFlight = useRef(new Set())
  const toastSeq = useRef(0)
  const toastTimers = useRef(new Map())

  const dismissToast = useCallback((id) => {
    clearTimeout(toastTimers.current.get(id))
    toastTimers.current.delete(id)
    setToasts((list) => list.filter((toast) => toast.id !== id))
  }, [])

  const showToast = useCallback(
    (tone, message) => {
      toastSeq.current += 1
      const id = toastSeq.current
      setToasts((list) => [...list.slice(-2), { id, tone, message }])
      toastTimers.current.set(
        id,
        setTimeout(() => dismissToast(id), TOAST_MS),
      )
    },
    [dismissToast],
  )

  useEffect(() => {
    const timers = toastTimers.current
    return () => timers.forEach((timer) => clearTimeout(timer))
  }, [])

  /** Runs approve / reject once per reservation at a time. Resolves true when the dialog can close. */
  const runAction = useCallback(
    async (action, reservation) => {
      const { id } = reservation
      if (inFlight.current.has(id)) return false
      inFlight.current.add(id)
      setPending((map) => ({ ...map, [id]: action }))

      try {
        const updated = await bookingsApi[action](id, reservation.version)
        if (updated?.id) setQueryData(queryKeys.detail(id), updated)
        showToast('success', `Reservation ${shortRef(id)} ${action === 'approve' ? 'approved' : 'rejected. Slot capacity was released'}.`)
        return true
      } catch (error) {
        if (guard(error)) return true
        const info = describeError(error)
        showToast(info.kind === 'conflict' ? 'info' : 'error', info.message)
        // 409: someone else already acted; the refetch below shows the new state.
        return info.kind === 'conflict' || info.kind === 'notFound' || info.kind === 'forbidden'
      } finally {
        inFlight.current.delete(id)
        setPending((map) => {
          const next = { ...map }
          delete next[id]
          return next
        })
        invalidateQueries(BOOKINGS_KEY)
      }
    },
    [guard, showToast],
  )

  const confirmReject = async () => {
    if (!rejectTarget) return
    const close = await runAction('reject', rejectTarget)
    if (close) setRejectTarget(null)
  }

  const openReservation = useCallback(
    (id) => {
      setSearchParams((params) => {
        const next = new URLSearchParams(params)
        next.set(DRAWER_PARAM, id)
        return next
      })
    },
    [setSearchParams],
  )

  const closeReservation = useCallback(() => {
    setSearchParams((params) => {
      const next = new URLSearchParams(params)
      next.delete(DRAWER_PARAM)
      return next
    })
  }, [setSearchParams])

  const canApprove = roleCanApprove(user?.role)

  const context = useMemo(
    () => ({
      canApprove,
      pendingAction: (id) => pending[id],
      approve: (reservation) => runAction('approve', reservation),
      requestReject: (reservation) => setRejectTarget(reservation),
      openReservation,
    }),
    [canApprove, pending, runAction, openReservation],
  )

  return (
    <BookingsContext.Provider value={context}>
      <div className="flex flex-col gap-6">
        <div className="flex flex-col gap-1">
          <p className="text-xs font-semibold uppercase tracking-wider text-secondary">Booking monitoring</p>
          <h1 className="text-2xl font-bold text-on-surface">
            {user?.role === 'GRID_OPERATOR' ? 'Bookings History' : 'Bookings'}
          </h1>
        </div>

        <SectionNav />
        <Outlet />
      </div>

      <ReservationDrawer reservationId={drawerId} onClose={closeReservation} />

      <ConfirmDialog
        open={Boolean(rejectTarget)}
        title="Reject this reservation?"
        icon="cancel"
        confirmLabel="Reject reservation"
        busyLabel="Rejecting…"
        busy={rejectTarget ? pending[rejectTarget.id] === 'reject' : false}
        onConfirm={confirmReject}
        onCancel={() => setRejectTarget(null)}
      >
        {rejectTarget && (
          <>
            <p>
              Reservation <Reference id={rejectTarget.id} className="text-on-surface" /> for prosumer{' '}
              <span className="font-semibold text-on-surface">{rejectTarget.prosumerId || '—'}</span>
              {rejectTarget.slotStartTimeUtc && <> on {formatColomboRange(rejectTarget.slotStartTimeUtc, rejectTarget.slotEndTimeUtc)}</>}{' '}
              will be rejected.
            </p>
            <p>
              <span className="font-semibold text-on-surface">The slot capacity will be released</span> so other
              prosumers can book it. This cannot be undone.
            </p>
            {rejectTarget.createdAtUtc && (
              <p className="text-xs">
                Requested <DateTime value={rejectTarget.createdAtUtc} />
              </p>
            )}
          </>
        )}
      </ConfirmDialog>

      <Toasts toasts={toasts} onDismiss={dismissToast} />
    </BookingsContext.Provider>
  )
}
