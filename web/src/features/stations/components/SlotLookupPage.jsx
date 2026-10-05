// SlotLookupPage.jsx — looks up a single energy booking slot by id via /api/booking-slots.
import { useState } from 'react'
import { stationsApi } from '../../../api.js'

export default function SlotLookupPage() {
  const [slotId, setSlotId] = useState('')
  const [slot, setSlot] = useState(null)
  const [notice, setNotice] = useState(null)

  async function handleLookup(event) {
    event.preventDefault()
    if (!slotId.trim()) {
      setNotice({ type: 'error', text: 'Enter a booking slot ID.' })
      setSlot(null)
      return
    }
    try {
      setSlot(await stationsApi.getSlot(slotId.trim()))
      setNotice(null)
    } catch (error) {
      setSlot(null)
      setNotice({ type: 'error', text: error.message })
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <span className="rounded-full bg-primary-container px-2 py-0.5 text-label-sm font-semibold uppercase text-on-primary-container">
          BackOfficer Control
        </span>
        <h1 className="mt-2 font-headline-xl text-headline-xl font-bold">Slot lookup</h1>
        <p className="text-secondary">Retrieve one energy booking slot by ID.</p>
      </div>
      {notice && (
        <div className="rounded-xl border-l-4 border-[#B91C1C] bg-surface-container-lowest px-4 py-3 text-[#B91C1C] shadow-sm">
          {notice.text}
        </div>
      )}
      <section className="flex max-w-xl flex-col gap-5 rounded-2xl bg-surface-container-lowest p-6 shadow-sm">
        <form className="flex flex-col gap-4" onSubmit={handleLookup}>
          <label className="flex flex-col gap-1.5">
            <span className="text-label-md font-semibold">Slot ID</span>
            <input
              className="h-11 rounded-lg bg-surface-container-low px-3.5 outline-none focus:ring-2 focus:ring-primary-container"
              value={slotId}
              placeholder="MongoDB slot id"
              onChange={(event) => setSlotId(event.target.value)}
            />
          </label>
          <button className="h-11 w-fit rounded-lg bg-primary-container px-6 text-label-md font-semibold text-on-primary-container" type="submit">
            Get slot
          </button>
        </form>
        {slot && (
          <table className="w-full text-left">
            <tbody className="divide-y divide-outline-variant/20">
              <tr><th className="py-2 pr-4 text-secondary">Station</th><td>{slot.stationId}</td></tr>
              <tr><th className="py-2 pr-4 text-secondary">Start</th><td>{new Date(slot.startTimeUtc).toLocaleString()}</td></tr>
              <tr><th className="py-2 pr-4 text-secondary">End</th><td>{new Date(slot.endTimeUtc).toLocaleString()}</td></tr>
              <tr><th className="py-2 pr-4 text-secondary">Maximum</th><td>{slot.maximumBookings}</td></tr>
              <tr><th className="py-2 pr-4 text-secondary">Reserved</th><td>{slot.reservedBookings}</td></tr>
              <tr><th className="py-2 pr-4 text-secondary">Remaining</th><td>{slot.remainingBookings}</td></tr>
              <tr><th className="py-2 pr-4 text-secondary">Active</th><td>{slot.isActive ? 'Yes' : 'No'}</td></tr>
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}
