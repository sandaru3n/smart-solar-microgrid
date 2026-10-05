// WeeklySchedulePage.jsx — Backoffice UI to view and update a station's weekly operating schedule.
import { useEffect, useState } from 'react'
import { stationsApi } from '../../../api.js'

const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']

const emptySchedule = {
  day: 'Monday',
  openingTime: '08:00:00',
  closingTime: '17:00:00',
  isAvailable: true,
}

function clock(value) {
  if (!value) return '00:00:00'
  const text = String(value)
  if (text.length >= 8) return text.slice(0, 8)
  return `${text}:00`.slice(0, 8)
}

function fieldClass() {
  return 'h-11 w-full rounded-lg bg-surface-container-lowest px-3.5 text-body-sm text-on-surface shadow-sm outline-none focus:ring-2 focus:ring-primary-container'
}

export default function WeeklySchedulePage() {
  const [stations, setStations] = useState([])
  const [selectedId, setSelectedId] = useState('')
  const [schedules, setSchedules] = useState([])
  const [scheduleForm, setScheduleForm] = useState(emptySchedule)
  const [editingScheduleId, setEditingScheduleId] = useState('')
  const [notice, setNotice] = useState(null)
  const [loading, setLoading] = useState(false)

  const selected = stations.find((station) => station.id === selectedId)

  function toast(type, text) {
    setNotice({ type, text })
  }

  async function loadSchedules(stationId) {
    setSchedules(await stationsApi.schedules(stationId))
  }

  useEffect(() => {
    stationsApi
      .list()
      .then(async (data) => {
        setStations(data)
        const id = data[0]?.id || ''
        setSelectedId(id)
        if (id) await loadSchedules(id)
      })
      .catch((error) => toast('error', error.message))
  }, [])

  async function changeStation(id) {
    setSelectedId(id)
    setEditingScheduleId('')
    setScheduleForm(emptySchedule)
    if (!id) {
      setSchedules([])
      return
    }
    try {
      await loadSchedules(id)
    } catch (error) {
      toast('error', error.message)
    }
  }

  async function handleSchedule(event) {
    event.preventDefault()
    if (!selectedId) {
      toast('error', 'Select a station first.')
      return
    }
    if (scheduleForm.closingTime <= scheduleForm.openingTime) {
      toast('error', 'Closing time must be after opening time.')
      return
    }
    setLoading(true)
    try {
      const body = {
        day: scheduleForm.day,
        openingTime: clock(scheduleForm.openingTime),
        closingTime: clock(scheduleForm.closingTime),
        isAvailable: scheduleForm.isAvailable,
      }
      if (editingScheduleId) {
        await stationsApi.updateSchedule(selectedId, editingScheduleId, body)
        toast('ok', `Operating hours for ${scheduleForm.day} updated.`)
      } else {
        await stationsApi.createSchedule(selectedId, body)
        toast('ok', `Schedule added for ${scheduleForm.day}.`)
      }
      await loadSchedules(selectedId)
      setEditingScheduleId('')
      setScheduleForm(emptySchedule)
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
          <p className="font-medium text-on-surface">{notice.text}</p>
          <button type="button" className="rounded-lg p-1.5 text-secondary" onClick={() => setNotice(null)}>
            <span className="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>
      )}

      <div className="flex flex-col gap-1 border-b border-outline-variant/30 pb-2">
        <span className="w-fit rounded-full bg-primary-container px-2 py-0.5 text-label-sm font-semibold uppercase tracking-wide text-on-primary-container">
          BackOfficer Control
        </span>
        <h1 className="font-headline-xl text-headline-xl font-bold tracking-tight text-on-surface">Schedule</h1>
        <p className="text-secondary">Set opening and closing hours for a station.</p>
      </div>

      <div className="flex flex-col gap-6 rounded-2xl bg-surface-container-lowest p-6 shadow-sm">
        <label className="flex max-w-md flex-col gap-1.5">
          <span className="text-label-md font-semibold">Station</span>
          <select className={fieldClass()} value={selectedId} onChange={(event) => changeStation(event.target.value)}>
            {stations.length === 0 && <option value="">No stations yet</option>}
            {stations.map((station) => (
              <option key={station.id} value={station.id}>
                {station.name}
              </option>
            ))}
          </select>
        </label>

        <div>
          <h2 className="flex items-center gap-2 font-headline-md text-headline-md font-semibold">
            <span className="material-symbols-outlined text-[22px] text-secondary">calendar_today</span>
            Hours
          </h2>
          <p className="text-secondary">
            {selected ? `Opening and closing hours for ${selected.name}.` : 'Select a station to view its hours.'}
          </p>
        </div>

        <div className="overflow-x-auto rounded-xl bg-surface-container-low">
          <table className="w-full text-left">
            <thead>
              <tr className="bg-surface-container text-label-sm uppercase tracking-wider text-secondary">
                <th className="px-4 py-3">Day</th>
                <th className="px-4 py-3">Opening</th>
                <th className="px-4 py-3">Closing</th>
                <th className="px-4 py-3">Available</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant/10">
              {schedules.length === 0 && (
                <tr>
                  <td className="px-4 py-3 text-secondary" colSpan={5}>
                    No schedules for this station.
                  </td>
                </tr>
              )}
              {schedules.map((schedule) => (
                <tr key={schedule.id}>
                  <td className="px-4 py-3 font-medium">{schedule.day}</td>
                  <td className="px-4 py-3">{clock(schedule.openingTime)}</td>
                  <td className="px-4 py-3">{clock(schedule.closingTime)}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`rounded-full px-2.5 py-0.5 text-label-sm font-semibold ${
                        schedule.isAvailable ? 'bg-[#DCFCE7] text-[#15803D]' : 'bg-surface-container text-secondary'
                      }`}
                    >
                      {schedule.isAvailable ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      type="button"
                      className="text-label-md font-semibold text-primary"
                      onClick={() => {
                        setEditingScheduleId(schedule.id)
                        setScheduleForm({
                          day: schedule.day,
                          openingTime: clock(schedule.openingTime),
                          closingTime: clock(schedule.closingTime),
                          isAvailable: schedule.isAvailable,
                        })
                      }}
                    >
                      Edit
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <form className="grid grid-cols-1 items-end gap-4 rounded-xl bg-surface-container-low p-5 sm:grid-cols-2 lg:grid-cols-4" onSubmit={handleSchedule}>
          <div className="flex items-center justify-between sm:col-span-2 lg:col-span-4">
            <span className="text-label-md font-bold">
              {editingScheduleId ? `Editing ${scheduleForm.day}` : 'Add day schedule'}
            </span>
            {editingScheduleId && (
              <button
                type="button"
                className="text-label-sm text-[#B91C1C]"
                onClick={() => {
                  setEditingScheduleId('')
                  setScheduleForm(emptySchedule)
                }}
              >
                Cancel edit
              </button>
            )}
          </div>
          <label className="flex flex-col gap-1.5">
            <span className="text-label-sm font-semibold uppercase text-secondary">Day</span>
            <select className={fieldClass()} value={scheduleForm.day} onChange={(event) => setScheduleForm({ ...scheduleForm, day: event.target.value })}>
              {days.map((day) => (
                <option key={day}>{day}</option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-label-sm font-semibold uppercase text-secondary">Opening</span>
            <input className={fieldClass()} value={scheduleForm.openingTime} onChange={(event) => setScheduleForm({ ...scheduleForm, openingTime: event.target.value })} />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-label-sm font-semibold uppercase text-secondary">Closing</span>
            <input className={fieldClass()} value={scheduleForm.closingTime} onChange={(event) => setScheduleForm({ ...scheduleForm, closingTime: event.target.value })} />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-label-sm font-semibold uppercase text-secondary">Available</span>
            <select
              className={fieldClass()}
              value={scheduleForm.isAvailable ? 'yes' : 'no'}
              onChange={(event) => setScheduleForm({ ...scheduleForm, isAvailable: event.target.value === 'yes' })}
            >
              <option value="yes">Yes</option>
              <option value="no">No</option>
            </select>
          </label>
          <div className="flex items-center justify-between gap-4 sm:col-span-2 lg:col-span-4">
            <p className="text-label-sm text-secondary">Closing time must be after opening time.</p>
            <button type="submit" disabled={loading || !selectedId} className="flex h-11 items-center gap-2 rounded-lg bg-primary-container px-6 text-label-md font-semibold text-on-primary-container">
              <span className="material-symbols-outlined text-[18px]">more_time</span>
              {editingScheduleId ? 'Update schedule' : 'Add schedule'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
