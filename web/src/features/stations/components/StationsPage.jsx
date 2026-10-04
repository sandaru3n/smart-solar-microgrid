import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { stationsApi } from '../../../api.js'
import { useAuth } from '../../../auth/AuthContext'
import LocationPickerMap from '../../../components/LocationPickerMap.jsx'

const emptyStation = {
  name: '',
  address: '',
  latitude: '',
  longitude: '',
  capacityKw: '',
  batteryStorageSlots: '',
}

function toStationBody(form) {
  return {
    name: form.name.trim(),
    address: form.address.trim(),
    latitude: Number(form.latitude),
    longitude: Number(form.longitude),
    capacityKw: Number(form.capacityKw),
    batteryStorageSlots: Number(form.batteryStorageSlots || 0),
  }
}

function validateStation(form) {
  const body = toStationBody(form)
  if (!body.name) return 'Station name is required.'
  if (Number.isNaN(body.latitude) || body.latitude < -90 || body.latitude > 90) {
    return 'Latitude must be between -90 and 90.'
  }
  if (Number.isNaN(body.longitude) || body.longitude < -180 || body.longitude > 180) {
    return 'Longitude must be between -180 and 180.'
  }
  if (!(body.capacityKw > 0)) return 'Capacity must be greater than zero.'
  return null
}

function stationToForm(station) {
  return {
    name: station.name ?? '',
    address: station.address ?? '',
    latitude: station.latitude ?? '',
    longitude: station.longitude ?? '',
    capacityKw: station.capacityKw ?? '',
    batteryStorageSlots: station.batteryStorageSlots ?? '',
  }
}

const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']

function clock(value) {
  if (!value) return '00:00:00'
  const text = String(value)
  if (text.length >= 8) return text.slice(0, 8)
  return `${text}:00`.slice(0, 8)
}

function mergeWeek(existing) {
  return days.map((day) => {
    const matches = (existing || []).filter((item) => item.day === day)
    const first = matches[0]
    return {
      day,
      ids: matches.map((item) => item.id).filter(Boolean),
      openingTime: clock(first?.openingTime || '08:00:00'),
      closingTime: clock(first?.closingTime || '17:00:00'),
      isAvailable: first ? Boolean(first.isAvailable) : true,
    }
  })
}

function fieldClass() {
  return 'h-11 w-full rounded-lg bg-surface-container-lowest px-3.5 text-body-sm text-on-surface shadow-sm outline-none focus:ring-2 focus:ring-primary-container'
}

export default function StationsPage() {
  const { user } = useAuth()
  const viewOnly = user?.role === 'GRID_OPERATOR'
  const [stations, setStations] = useState([])
  const [query, setQuery] = useState('')
  const [selectedId, setSelectedId] = useState('')
  const [showEdit, setShowEdit] = useState(false)
  const [editForm, setEditForm] = useState(emptyStation)
  const [schedules, setSchedules] = useState(() => mergeWeek([]))
  const [editingDay, setEditingDay] = useState('')
  const [notice, setNotice] = useState(null)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const openRequest = useRef(0)

  const visibleStations = useMemo(() => {
    const text = query.trim().toLowerCase()
    if (!text) return stations
    return stations.filter((station) =>
      `${station.name} ${station.address}`.toLowerCase().includes(text),
    )
  }, [stations, query])

  const totalCapacity = stations.reduce((sum, station) => sum + Number(station.capacityKw || 0), 0)
  const totalSlots = stations.reduce((sum, station) => sum + Number(station.batteryStorageSlots || 0), 0)
  const selected = stations.find((station) => station.id === selectedId)

  function toast(type, text) {
    setNotice({ type, text })
  }

  async function loadStations(keepId = selectedId) {
    const data = await stationsApi.list()
    setStations(data)
    return data.find((station) => station.id === keepId) ? keepId : data[0]?.id || ''
  }

  function updateSchedule(day, patch) {
    setSchedules((current) => current.map((item) => (item.day === day ? { ...item, ...patch } : item)))
  }

  async function openStation(id, { edit = false } = {}) {
    const request = ++openRequest.current
    const canEdit = edit && !viewOnly
    setSelectedId(id)
    setShowEdit(canEdit)
    setEditingDay('')
    const station = await stationsApi.get(id)
    if (request !== openRequest.current) return
    setEditForm(stationToForm(station))
    if (canEdit || viewOnly) {
      const rows = await stationsApi.schedules(id)
      if (request !== openRequest.current) return
      setSchedules(mergeWeek(rows))
    }
  }

  useEffect(() => {
    loadStations('')
      .then((id) => {
        if (id) return openStation(id)
      })
      .catch((error) => toast('error', error.message))
  }, [])

  async function handleUpdate(event) {
    event.preventDefault()
    const error = validateStation(editForm)
    if (error) {
      toast('error', error)
      return
    }
    const prepared = schedules.map((item) => ({
      ...item,
      openingTime: clock(item.openingTime),
      closingTime: clock(item.closingTime),
    }))
    const invalid = prepared.find((item) => item.closingTime <= item.openingTime)
    if (invalid) {
      toast('error', `Closing time must be after opening time for ${invalid.day}.`)
      return
    }
    setLoading(true)
    try {
      await stationsApi.update(selectedId, toStationBody(editForm))
      for (const item of prepared) {
        const body = {
          day: item.day,
          openingTime: item.openingTime,
          closingTime: item.closingTime,
          isAvailable: item.isAvailable,
        }
        if (item.ids.length === 0) {
          await stationsApi.createSchedule(selectedId, body)
        } else {
          for (const scheduleId of item.ids) {
            await stationsApi.updateSchedule(selectedId, scheduleId, body)
          }
        }
      }
      await loadStations(selectedId)
      setShowEdit(false)
      toast('ok', 'Station updated.')
    } catch (error) {
      toast('error', error.message)
    } finally {
      setLoading(false)
    }
  }

  async function confirmDeactivate() {
    setConfirmOpen(false)
    setLoading(true)
    try {
      await stationsApi.deactivate(selectedId)
      const nextId = await loadStations('')
      if (nextId) await openStation(nextId)
      else setSelectedId('')
      toast('ok', 'Station deactivated.')
    } catch (error) {
      toast('error', error.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex w-full flex-col gap-8">
      {notice && (
        <div
          className={`flex items-center justify-between rounded-xl border-l-4 bg-surface-container-lowest px-4 py-3 shadow-md ${
            notice.type === 'error' ? 'border-[#B91C1C]' : 'border-primary'
          }`}
        >
          <div className="flex items-center gap-3">
            <div
              className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${
                notice.type === 'error' ? 'bg-[#FEE2E2]' : 'bg-[#DCFCE7]'
              }`}
            >
              <span
                className={`material-symbols-outlined text-[18px] ${
                  notice.type === 'error' ? 'text-[#B91C1C]' : 'text-[#15803D]'
                }`}
              >
                {notice.type === 'error' ? 'error' : 'check_circle'}
              </span>
            </div>
            <p className="font-medium text-on-surface">{notice.text}</p>
          </div>
          <button
            type="button"
            className="rounded-lg p-1.5 text-secondary hover:bg-surface-container hover:text-on-surface"
            onClick={() => setNotice(null)}
          >
            <span className="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>
      )}

      <div className="flex flex-col justify-between gap-4 border-b border-outline-variant/30 pb-2 md:flex-row md:items-end">
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <span className="rounded-full bg-[#FFDD19] px-2.5 py-1 text-[11px] font-bold uppercase tracking-wide text-[#1C1914]">
              {viewOnly ? 'View only' : 'BackOfficer Control'}
            </span>
            <span className="text-label-sm text-secondary">
              {selected ? `Station ${selected.id.slice(-6)}` : 'No station selected'}
            </span>
          </div>
          <h1 className="font-headline-xl text-headline-xl font-bold tracking-tight text-on-surface">
            {viewOnly ? 'Stations' : 'Station Management'}
          </h1>
          <p className="text-secondary">
            {viewOnly
              ? 'View solar stations and battery storage capacities.'
              : 'Manage solar stations and battery storage capacities.'}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-3 rounded-xl bg-surface-container-lowest px-4 py-2 shadow-sm">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-[#FFDD19] text-[#1C1914]">
              <span className="material-symbols-outlined text-[18px]">solar_power</span>
            </div>
            <div className="flex flex-col">
              <span className="text-label-sm uppercase text-secondary">Nominal output</span>
              <span className="font-bold text-on-surface">{totalCapacity} kW</span>
            </div>
          </div>
          <div className="flex items-center gap-3 rounded-xl bg-surface-container-lowest px-4 py-2 shadow-sm">
            <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-[#DCFCE7] text-[#15803D]">
              <span className="material-symbols-outlined text-[18px]">battery_charging_full</span>
            </div>
            <div className="flex flex-col">
              <span className="text-label-sm uppercase text-secondary">Battery slots</span>
              <span className="font-bold text-on-surface">{totalSlots} total</span>
            </div>
          </div>
          {!viewOnly && (
            <Link
              to="/dashboard/stations/create"
              className="flex h-11 items-center gap-2 rounded-lg bg-primary-container px-5 text-label-md font-semibold text-on-primary-container shadow-sm"
            >
              <span className="material-symbols-outlined text-[18px]">add</span>
              Create station
            </Link>
          )}
        </div>
      </div>

      <div className="flex flex-col gap-8">
        <div className="flex w-full flex-col gap-6">
          <div className="flex w-full flex-col gap-5 rounded-2xl bg-surface-container-lowest p-6 shadow-sm">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-[#FFDD19] text-[#1C1914]">
                  <span className="material-symbols-outlined text-[20px]">ev_station</span>
                </span>
                <h2 className="font-headline-md text-headline-md font-semibold text-on-surface">All stations</h2>
              </div>
              <span className="flex items-center gap-1.5 rounded-full bg-[#DCFCE7] px-2.5 py-0.5 text-label-sm font-semibold text-[#15803D]">
                <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-[#15803D]" />
                {stations.length} Active
              </span>
            </div>
            <p className="-mt-2 text-secondary">
              {viewOnly ? 'Select a station to view its details.' : 'Select a station to update its details.'}
            </p>
            <div className="relative">
              <span className="material-symbols-outlined absolute top-3 left-3 text-[18px] text-secondary">search</span>
              <input
                className="h-10 w-full rounded-full border border-[#E2E8F0] bg-[#F8FAFC] pr-3 pl-9 text-on-surface outline-none placeholder:text-secondary focus:border-[#FFDD19] focus:ring-2 focus:ring-[#FFDD19]"
                placeholder="Filter stations..."
                value={query}
                onChange={(event) => setQuery(event.target.value)}
              />
            </div>
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              {visibleStations.length === 0 && (
                <p className="text-secondary">No stations yet.</p>
              )}
              {visibleStations.map((station) => {
                const active = station.id === selectedId
                return (
                  <div
                    key={station.id}
                    role="button"
                    tabIndex={0}
                    onClick={() => openStation(station.id).catch((error) => toast('error', error.message))}
                    onKeyDown={(event) => {
                      if (event.target !== event.currentTarget) return
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault()
                        openStation(station.id).catch((error) => toast('error', error.message))
                      }
                    }}
                    className={`flex cursor-pointer flex-col gap-2.5 rounded-2xl p-4 text-left transition ${
                      active
                        ? 'border-2 border-[#FFDD19] bg-[#FFFBEB]'
                        : 'border border-[#E2E8F0] bg-white hover:border-[#FFDD19]'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex flex-col">
                        <span className="flex items-center gap-1.5 font-bold text-on-surface">
                          {station.name}
                          {active && (
                            <span className="material-symbols-outlined text-[16px] text-[#1C1914]">verified</span>
                          )}
                        </span>
                        <span className="flex items-center gap-1 text-secondary">
                          <span className="material-symbols-outlined text-[14px]">location_on</span>
                          {station.address || 'No address'}
                        </span>
                      </div>
                      <div className="flex items-center gap-2">
                        {!viewOnly && (
                          <button
                            type="button"
                            aria-label={`Edit ${station.name}`}
                            title="Edit station"
                            onClick={(event) => {
                              event.stopPropagation()
                              openStation(station.id, { edit: true }).catch((error) => toast('error', error.message))
                            }}
                            className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary-container text-on-primary-container"
                          >
                            <span className="material-symbols-outlined text-[18px]">edit</span>
                          </button>
                        )}
                        <span className="inline-flex items-center gap-1 rounded-full bg-[#DCFCE7] px-2 py-0.5 text-label-sm font-semibold text-[#15803D]">
                          <span className="h-1.5 w-1.5 rounded-full bg-[#15803D]" />
                          Active
                        </span>
                      </div>
                    </div>
                    <div className="mt-1 flex items-center justify-between border-t border-outline-variant/20 pt-2 text-label-sm font-medium text-on-surface-variant">
                      <span className="flex items-center gap-1">
                        <span className="material-symbols-outlined text-[14px] text-[#1C1914]">bolt</span>
                        {station.capacityKw} kW
                      </span>
                      <span className="flex items-center gap-1">
                        <span className="material-symbols-outlined text-[14px] text-[#1C1914]">grid_view</span>
                        {station.batteryStorageSlots} slots
                      </span>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>

        </div>

        <div className="flex w-full flex-col gap-8">
          {viewOnly && selected && (
            <div className="flex flex-col gap-6 rounded-2xl bg-surface-container-lowest p-6 shadow-sm">
              <div className="flex items-center gap-3 border-b border-outline-variant/20 pb-4">
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-[#FFDD19] text-[#1C1914]">
                  <span className="material-symbols-outlined text-[22px]">visibility</span>
                </div>
                <div>
                  <h2 className="font-headline-md text-headline-md font-semibold">{selected.name}</h2>
                  <p className="text-secondary">Station details are view only.</p>
                </div>
              </div>
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                {[
                  ['Address', editForm.address || 'No address', true],
                  ['Latitude', editForm.latitude],
                  ['Longitude', editForm.longitude],
                  ['Capacity', `${editForm.capacityKw} kW`],
                  ['Battery slots', editForm.batteryStorageSlots],
                ].map(([label, value, wide]) => (
                  <div key={label} className={`rounded-2xl border border-[#E2E8F0] bg-[#F8FAFC] px-4 py-3 ${wide ? 'md:col-span-2' : ''}`}>
                    <span className="block text-[11px] font-bold uppercase tracking-wider text-[#78716C]">{label}</span>
                    <p className="mt-1 font-semibold text-[#1C1914]">{value}</p>
                  </div>
                ))}
              </div>
              <div className="overflow-x-auto rounded-xl border border-[#E2E8F0] bg-white">
                <table className="w-full text-left">
                  <thead>
                    <tr className="bg-[#FFDD19] text-[11px] font-bold uppercase tracking-wider text-[#1C1914]">
                      <th className="px-4 py-3">Day</th>
                      <th className="px-4 py-3">Opening</th>
                      <th className="px-4 py-3">Closing</th>
                      <th className="px-4 py-3">Available</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#FFDD19]/30 bg-[#FFDD19]/15">
                    {schedules.map((schedule) => (
                      <tr key={schedule.day}>
                        <td className="px-4 py-3 font-semibold">{schedule.day}</td>
                        <td className="px-4 py-3">{clock(schedule.openingTime)}</td>
                        <td className="px-4 py-3">{clock(schedule.closingTime)}</td>
                        <td className="px-4 py-3">
                          <span className={`rounded-full px-2.5 py-0.5 text-label-sm font-semibold ${schedule.isAvailable ? 'bg-[#DCFCE7] text-[#15803D]' : 'bg-surface-container text-secondary'}`}>
                            {schedule.isAvailable ? 'Yes' : 'No'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
          {selectedId && showEdit && (
              <div className="flex flex-col gap-6 rounded-2xl bg-surface-container-lowest p-6 shadow-sm">
                <div className="flex items-start justify-between gap-3 border-b border-outline-variant/20 pb-4">
                  <div className="flex items-center gap-3">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary-container/30 text-primary">
                      <span className="material-symbols-outlined text-[22px]">edit</span>
                    </div>
                    <div>
                      <h2 className="font-headline-md text-headline-md font-semibold">Edit station</h2>
                      <p className="text-secondary">Update station details, weekly hours, or deactivate it.</p>
                    </div>
                  </div>
                  <button
                    type="button"
                    aria-label="Close edit"
                    onClick={() => setShowEdit(false)}
                    className="flex h-9 w-9 items-center justify-center rounded-lg text-secondary hover:bg-surface-container"
                  >
                    <span className="material-symbols-outlined text-[20px]">close</span>
                  </button>
                </div>
                <form className="flex flex-col gap-5" onSubmit={handleUpdate}>
                  <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                    <label className="flex flex-col gap-1.5 md:col-span-2">
                      <span className="text-label-md font-semibold">Station name</span>
                      <input className={fieldClass()} value={editForm.name} onChange={(event) => setEditForm({ ...editForm, name: event.target.value })} />
                    </label>
                    <div className="md:col-span-2">
                      <LocationPickerMap
                        address={editForm.address}
                        latitude={editForm.latitude}
                        longitude={editForm.longitude}
                        onAddressChange={(nextAddress) =>
                          setEditForm((current) => ({ ...current, address: nextAddress }))
                        }
                        onChange={(latitude, longitude) =>
                          setEditForm((current) => ({ ...current, latitude: String(latitude), longitude: String(longitude) }))
                        }
                      />
                    </div>
                    <label className="flex flex-col gap-1.5">
                      <span className="text-label-md font-semibold">Latitude</span>
                      <input className={fieldClass()} type="number" step="any" value={editForm.latitude} onChange={(event) => setEditForm({ ...editForm, latitude: event.target.value })} />
                    </label>
                    <label className="flex flex-col gap-1.5">
                      <span className="text-label-md font-semibold">Longitude</span>
                      <input className={fieldClass()} type="number" step="any" value={editForm.longitude} onChange={(event) => setEditForm({ ...editForm, longitude: event.target.value })} />
                    </label>
                    <label className="flex flex-col gap-1.5">
                      <span className="text-label-md font-semibold">Capacity (kW)</span>
                      <input className={fieldClass()} type="number" step="any" value={editForm.capacityKw} onChange={(event) => setEditForm({ ...editForm, capacityKw: event.target.value })} />
                    </label>
                    <label className="flex flex-col gap-1.5">
                      <span className="text-label-md font-semibold">Battery slots</span>
                      <input className={fieldClass()} type="number" value={editForm.batteryStorageSlots} onChange={(event) => setEditForm({ ...editForm, batteryStorageSlots: event.target.value })} />
                    </label>
                  </div>
                  <div className="flex flex-col gap-4 border-t border-outline-variant/20 pt-5">
                    <div>
                      <h3 className="flex items-center gap-2 font-headline-md text-headline-md font-semibold">
                        <span className="material-symbols-outlined text-[22px] text-secondary">calendar_today</span>
                        Weekly schedule
                      </h3>
                      <p className="text-secondary">Use edit to change a day. Save changes stores the full week.</p>
                    </div>
                    <div className="overflow-x-auto rounded-xl border border-[#E2E8F0] bg-white">
                      <table className="w-full text-left">
                        <thead>
                          <tr className="bg-[#FFDD19] text-[11px] font-bold uppercase tracking-wider text-[#1C1914]">
                            <th className="px-4 py-3">Day</th>
                            <th className="px-4 py-3">Opening</th>
                            <th className="px-4 py-3">Closing</th>
                            <th className="px-4 py-3">Available</th>
                            <th className="px-4 py-3 text-right">Edit</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-[#FFDD19]/30 bg-[#FFDD19]/15">
                          {schedules.map((schedule) => {
                            const editing = editingDay === schedule.day
                            return (
                              <tr key={schedule.day}>
                                <td className="px-4 py-3 font-semibold">{schedule.day}</td>
                                <td className="px-4 py-3">
                                  {editing ? (
                                    <input
                                      className={fieldClass()}
                                      aria-label={`${schedule.day} opening`}
                                      value={schedule.openingTime}
                                      onChange={(event) => updateSchedule(schedule.day, { openingTime: event.target.value })}
                                    />
                                  ) : (
                                    clock(schedule.openingTime)
                                  )}
                                </td>
                                <td className="px-4 py-3">
                                  {editing ? (
                                    <input
                                      className={fieldClass()}
                                      aria-label={`${schedule.day} closing`}
                                      value={schedule.closingTime}
                                      onChange={(event) => updateSchedule(schedule.day, { closingTime: event.target.value })}
                                    />
                                  ) : (
                                    clock(schedule.closingTime)
                                  )}
                                </td>
                                <td className="px-4 py-3">
                                  {editing ? (
                                    <select
                                      className={fieldClass()}
                                      aria-label={`${schedule.day} available`}
                                      value={schedule.isAvailable ? 'yes' : 'no'}
                                      onChange={(event) => updateSchedule(schedule.day, { isAvailable: event.target.value === 'yes' })}
                                    >
                                      <option value="yes">Yes</option>
                                      <option value="no">No</option>
                                    </select>
                                  ) : (
                                    <span className={`rounded-full px-2.5 py-0.5 text-label-sm font-semibold ${schedule.isAvailable ? 'bg-[#DCFCE7] text-[#15803D]' : 'bg-surface-container text-secondary'}`}>
                                      {schedule.isAvailable ? 'Yes' : 'No'}
                                    </span>
                                  )}
                                </td>
                                <td className="px-4 py-3 text-right">
                                  <button
                                    type="button"
                                    aria-label={editing ? `Done editing ${schedule.day}` : `Edit ${schedule.day}`}
                                    onClick={() => setEditingDay(editing ? '' : schedule.day)}
                                    className="inline-flex h-8 w-8 items-center justify-center rounded-lg bg-primary-container text-on-primary-container"
                                  >
                                    <span className="material-symbols-outlined text-[18px]">{editing ? 'check' : 'edit'}</span>
                                  </button>
                                </td>
                              </tr>
                            )
                          })}
                        </tbody>
                      </table>
                    </div>
                  </div>
                  <div className="flex flex-wrap items-center justify-between gap-4 border-t border-outline-variant/20 pt-4">
                    <button
                      type="button"
                      disabled={loading}
                      onClick={() => setConfirmOpen(true)}
                      className="flex h-11 items-center gap-2 rounded-lg bg-[#FEE2E2] px-5 text-label-md font-semibold text-[#B91C1C]"
                    >
                      <span className="material-symbols-outlined text-[18px]">power_settings_new</span>
                      Deactivate station
                    </button>
                    <button type="submit" disabled={loading} className="flex h-11 items-center gap-2 rounded-lg bg-primary-container px-6 text-label-md font-semibold text-on-primary-container">
                      <span className="material-symbols-outlined text-[18px]">save</span>
                      Save changes
                    </button>
                  </div>
                </form>
              </div>
          )}
        </div>
      </div>

      {confirmOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-on-surface/40 p-4 backdrop-blur-sm">
          <div className="flex w-full max-w-md flex-col gap-5 rounded-2xl border border-outline-variant/30 bg-surface-container-lowest p-6 shadow-xl">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-[#FEE2E2] text-[#B91C1C]">
              <span className="material-symbols-outlined text-[26px]">warning</span>
            </div>
            <div className="flex flex-col gap-2">
              <h3 className="font-headline-md text-headline-md font-semibold">
                Deactivate {editForm.name}?
              </h3>
              <p className="text-secondary">
                The station is deactivated only when it has no reserved bookings. If reservations exist, the request is rejected.
              </p>
            </div>
            <div className="flex justify-end gap-3">
              <button type="button" className="h-11 rounded-lg bg-surface-container px-5" onClick={() => setConfirmOpen(false)}>
                Keep station
              </button>
              <button type="button" className="flex h-11 items-center gap-2 rounded-lg bg-[#B91C1C] px-6 font-semibold text-white" onClick={confirmDeactivate}>
                <span className="material-symbols-outlined text-[18px]">power_off</span>
                Deactivate
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
