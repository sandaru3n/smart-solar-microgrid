import { useCallback, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { DEFAULT_PAGE_SIZE, PAGE_SIZES, bookingsApi, isReservationId } from '../bookingsApi'
import { BOOKINGS_KEY, DRAWER_PARAM, queryKeys, useStations } from '../context'
import { invalidateQueries, useQuery } from '../query'
import { ALL_STATUSES, HISTORY_STATUSES, STATUS, STATUS_META } from '../status'
import EmptyState from '../components/EmptyState'
import ErrorState from '../components/ErrorState'
import FilterBar from '../components/FilterBar'
import LastRefreshed from '../components/LastRefreshed'
import Pagination from '../components/Pagination'
import ReservationTable from '../components/ReservationTable'
import TableSkeleton from '../components/TableSkeleton'
import { cardClass } from '../components/styles'
import { Button } from '../components/ui'

/*
 * One list screen, four modes:
 *   all      — every status, status chips (single select: the API filters one status at a time)
 *   open     — Pending and Approved only (the reservations menu page)
 *   pending  — fixed to Pending, with Approve / Reject
 *   history  — Completed / Rejected / Cancelled chips, with completion columns
 * Every filter lives in the URL query string, so links, refresh and Back keep them.
 */
const MODES = {
  all: {
    title: 'All reservations',
    statusOptions: ALL_STATUSES,
    defaultStatus: '',
    emptyTitle: 'No reservations yet',
    emptyText: 'Reservations created by prosumers will appear here.',
  },
  open: {
    title: 'All reservations',
    statusOptions: [STATUS.Pending, STATUS.Approved],
    defaultStatus: '',
    combineStatuses: [STATUS.Pending, STATUS.Approved],
    emptyTitle: 'No pending or approved reservations',
    emptyText: 'Pending and approved reservations will appear here.',
  },
  pending: {
    title: 'Pending approvals',
    statusOptions: null,
    defaultStatus: STATUS.Pending,
    emptyTitle: 'All caught up',
    emptyText: 'There are no reservations waiting for approval.',
    showActions: true,
  },
  history: {
    title: 'History',
    statusOptions: HISTORY_STATUSES,
    defaultStatus: STATUS.Completed,
    emptyTitle: 'No history yet',
    emptyText: 'Completed, rejected and cancelled reservations will appear here.',
    showCompletion: true,
  },
}

const OBJECT_ID = /^[a-f\d]{24}$/i
const DATE = /^\d{4}-\d{2}-\d{2}$/
const FETCH_PAGE_SIZE = 50

/** Loads every page for one status. The list API accepts only one status at a time. */
async function listEveryPage(params) {
  const first = await bookingsApi.list({ ...params, page: 1, pageSize: FETCH_PAGE_SIZE })
  if (first.totalPages <= 1) return first.items
  const rest = await Promise.all(
    Array.from({ length: first.totalPages - 1 }, (_, index) =>
      bookingsApi.list({ ...params, page: index + 2, pageSize: FETCH_PAGE_SIZE }).then((page) => page.items),
    ),
  )
  return first.items.concat(...rest)
}

/** Pending and Approved together, newest first, then sliced to the requested page. */
async function listCombined(params, statuses, page, pageSize) {
  const groups = await Promise.all(statuses.map((status) => listEveryPage({ ...params, status })))
  const items = groups.flat().sort((left, right) => {
    const byTime = (Date.parse(right.createdAtUtc || '') || 0) - (Date.parse(left.createdAtUtc || '') || 0)
    return byTime || String(right.id).localeCompare(String(left.id))
  })
  const start = (page - 1) * pageSize
  return {
    items: items.slice(start, start + pageSize),
    page,
    pageSize,
    totalCount: items.length,
    totalPages: items.length === 0 ? 0 : Math.ceil(items.length / pageSize),
  }
}

function positiveInt(value, fallback) {
  const number = Number(value)
  return Number.isInteger(number) && number >= 1 ? number : fallback
}

/** Reads and validates filters from the URL (bad values are ignored, not sent to the API). */
function readFilters(searchParams, mode) {
  const config = MODES[mode]
  const rawStatus = searchParams.get('status') ?? ''
  const status = config.statusOptions?.includes(rawStatus) ? rawStatus : config.defaultStatus
  const station = searchParams.get('station') ?? ''
  const date = searchParams.get('date') ?? ''
  const size = positiveInt(searchParams.get('size'), DEFAULT_PAGE_SIZE)

  return {
    q: (searchParams.get('q') ?? '').trim().slice(0, 100),
    station: OBJECT_ID.test(station) ? station : '',
    date: DATE.test(date) ? date : '',
    status,
    page: positiveInt(searchParams.get('page'), 1),
    size: PAGE_SIZES.includes(size) ? size : DEFAULT_PAGE_SIZE,
  }
}

function toApiParams(filters) {
  const params = {
    status: filters.status || undefined,
    stationId: filters.station || undefined,
    dateUtc: filters.date || undefined,
    page: filters.page,
    pageSize: filters.size,
  }
  // The API's `reference` must be a full id; any other text searches station names.
  if (filters.q) {
    if (isReservationId(filters.q)) params.reference = filters.q
    else params.q = filters.q
  }
  return params
}

export default function ReservationListView({ mode }) {
  const config = MODES[mode]
  const [searchParams, setSearchParams] = useSearchParams()
  const filters = readFilters(searchParams, mode)
  const apiParams = toApiParams(filters)
  const combineStatuses = config.combineStatuses && !filters.status ? config.combineStatuses : null
  const stations = useStations()

  const query = useQuery(
    queryKeys.list(combineStatuses ? { ...apiParams, status: combineStatuses.join('+') } : apiParams),
    () =>
      combineStatuses
        ? listCombined(apiParams, combineStatuses, filters.page, filters.size)
        : bookingsApi.list(apiParams),
    { keepPreviousData: true },
  )

  useEffect(() => {
    document.title = `${config.title} · Smart Solar Microgrid`
  }, [config.title])

  /** Sets URL filters; any change other than paging goes back to page 1. */
  const setFilters = useCallback(
    (patch, { keepPage = false } = {}) => {
      setSearchParams(
        (current) => {
          const next = new URLSearchParams(current)
          for (const [key, value] of Object.entries(patch)) {
            if (value === '' || value === null || value === undefined) next.delete(key)
            else next.set(key, String(value))
          }
          if (!keepPage) next.delete('page')
          if (next.get('page') === '1') next.delete('page')
          return next
        },
        { replace: true },
      )
    },
    [setSearchParams],
  )

  const onSearch = useCallback((q) => setFilters({ q }), [setFilters])

  const clearAll = () =>
    setSearchParams(
      (current) => {
        const next = new URLSearchParams()
        // Keep only the page size and an open drawer.
        if (current.get('size')) next.set('size', current.get('size'))
        if (current.get(DRAWER_PARAM)) next.set(DRAWER_PARAM, current.get(DRAWER_PARAM))
        return next
      },
      { replace: true },
    )

  // If the current page no longer exists (e.g. the last item was approved), go to the last page.
  const totalPages = query.data?.totalPages ?? 0
  useEffect(() => {
    if (!query.isPlaceholder && totalPages > 0 && filters.page > totalPages) {
      setFilters({ page: totalPages }, { keepPage: true })
    }
  }, [query.isPlaceholder, totalPages, filters.page, setFilters])

  const stationLabel = (id) => stations.nameById.get(id) ?? 'Selected station'
  const statusIsFilter = Boolean(config.statusOptions) && filters.status !== config.defaultStatus

  const applied = [
    filters.q && {
      key: 'q',
      label: isReservationId(filters.q) ? `Reference: …${filters.q.slice(-8).toUpperCase()}` : `Search: “${filters.q}”`,
      onRemove: () => setFilters({ q: '' }),
    },
    filters.station && { key: 'station', label: `Station: ${stationLabel(filters.station)}`, onRemove: () => setFilters({ station: '' }) },
    filters.date && { key: 'date', label: `Slot date (UTC): ${filters.date}`, onRemove: () => setFilters({ date: '' }) },
    statusIsFilter && {
      key: 'status',
      label: `Status: ${filters.status ? STATUS_META[filters.status].label : 'All'}`,
      onRemove: () => setFilters({ status: '' }),
    },
  ].filter(Boolean)

  const hasFilters = applied.length > 0
  const data = query.data
  const heading = `${config.title}${mode === 'history' ? ` · ${STATUS_META[filters.status].label}` : ''}`

  return (
    <section className={`${cardClass} overflow-hidden`} aria-labelledby="list-heading">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-outline-variant/20 px-4 py-4 lg:px-6">
        <div className="flex flex-col">
          <h2 id="list-heading" className="text-base font-bold text-on-surface">
            {heading}
          </h2>
          <p className="text-xs text-secondary" aria-live="polite">
            {data && !query.isPlaceholder
              ? `${data.totalCount} reservation${data.totalCount === 1 ? '' : 's'}`
              : query.isError
                ? ''
                : 'Loading…'}
          </p>
        </div>
        <LastRefreshed updatedAt={query.updatedAt} fetching={query.isFetching} onRefresh={() => invalidateQueries(BOOKINGS_KEY)} />
      </div>

      <FilterBar
        search={{ value: filters.q, onChange: onSearch }}
        station={{
          value: filters.station,
          onChange: (station) => setFilters({ station }),
          options: stations.stations,
          loading: stations.isLoading,
          error: stations.isError,
        }}
        date={{ value: filters.date, onChange: (date) => setFilters({ date }) }}
        status={
          config.statusOptions
            ? {
                value: filters.status,
                onChange: (status) => setFilters({ status: status === config.defaultStatus ? '' : status }),
                options: config.statusOptions,
                allLabel: mode === 'history' ? null : mode === 'open' ? 'Pending & Approved' : 'All',
              }
            : undefined
        }
        applied={applied}
        onClearAll={clearAll}
      />

      {query.error && !query.isError && (
        <div className="px-4 pt-4 lg:px-6">
          <ErrorState variant="banner" error={query.error} onRetry={query.refetch} retrying={query.isFetching} />
        </div>
      )}

      {query.isLoading ? (
        <TableSkeleton rows={Math.min(filters.size, 8)} columns={config.showCompletion ? 8 : 7} />
      ) : query.isError ? (
        <ErrorState error={query.error} onRetry={query.refetch} retrying={query.isFetching} title="Could not load reservations" />
      ) : data.items.length === 0 ? (
        hasFilters ? (
          <EmptyState
            icon="search_off"
            title="No reservations match these filters"
            description="Try a different reference, station, date or status."
            action={
              <Button icon="filter_alt_off" onClick={clearAll}>
                Clear filters
              </Button>
            }
          />
        ) : (
          <EmptyState icon={mode === 'pending' ? 'task_alt' : 'inbox'} title={config.emptyTitle} description={config.emptyText} />
        )
      ) : (
        <>
          <ReservationTable
            caption={heading}
            rows={data.items}
            busy={query.isPlaceholder}
            showActions={config.showActions}
            showCompletion={config.showCompletion}
          />
          <Pagination
            page={filters.page}
            pageSize={filters.size}
            totalCount={data.totalCount}
            totalPages={data.totalPages}
            onPageChange={(page) => setFilters({ page }, { keepPage: true })}
            onPageSizeChange={(size) => setFilters({ size: size === DEFAULT_PAGE_SIZE ? '' : size })}
          />
        </>
      )}
    </section>
  )
}
