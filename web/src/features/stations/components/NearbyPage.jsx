// NearbyPage.jsx — finds active stations within a radius of a chosen map coordinate.
import { useState } from 'react'
import { stationsApi } from '../../../api.js'

export default function NearbyPage() {
  const [form, setForm] = useState({
    latitude: '6.9271',
    longitude: '79.8612',
    radiusKm: '10',
  })
  const [results, setResults] = useState([])
  const [notice, setNotice] = useState(null)
  const [searched, setSearched] = useState(false)

  async function handleSearch(event) {
    event.preventDefault()
    const latitude = Number(form.latitude)
    const longitude = Number(form.longitude)
    const radiusKm = Number(form.radiusKm)
    if (Number.isNaN(latitude) || latitude < -90 || latitude > 90) {
      setNotice({ type: 'error', text: 'Latitude must be between -90 and 90.' })
      return
    }
    if (Number.isNaN(longitude) || longitude < -180 || longitude > 180) {
      setNotice({ type: 'error', text: 'Longitude must be between -180 and 180.' })
      return
    }
    if (!(radiusKm > 0)) {
      setNotice({ type: 'error', text: 'Radius must be greater than zero.' })
      return
    }
    try {
      setResults(await stationsApi.nearby({ latitude, longitude, radiusKm }))
      setSearched(true)
      setNotice(null)
    } catch (error) {
      setNotice({ type: 'error', text: error.message })
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <span className="rounded-full bg-primary-container px-2 py-0.5 text-label-sm font-semibold uppercase text-on-primary-container">
          BackOfficer Control
        </span>
        <h1 className="mt-2 font-headline-xl text-headline-xl font-bold">Nearby stations</h1>
        <p className="text-secondary">Find active stations within a radius of a location.</p>
      </div>
      {notice && (
        <div className="rounded-xl border-l-4 border-[#B91C1C] bg-surface-container-lowest px-4 py-3 text-[#B91C1C] shadow-sm">
          {notice.text}
        </div>
      )}
      <section className="flex flex-col gap-5 rounded-2xl bg-surface-container-lowest p-6 shadow-sm">
        <form className="grid grid-cols-1 gap-4 md:grid-cols-3" onSubmit={handleSearch}>
          <label className="flex flex-col gap-1.5">
            <span className="text-label-md font-semibold">Latitude</span>
            <input className="h-11 rounded-lg bg-surface-container-low px-3.5 outline-none focus:ring-2 focus:ring-primary-container" type="number" step="any" value={form.latitude} onChange={(event) => setForm({ ...form, latitude: event.target.value })} />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-label-md font-semibold">Longitude</span>
            <input className="h-11 rounded-lg bg-surface-container-low px-3.5 outline-none focus:ring-2 focus:ring-primary-container" type="number" step="any" value={form.longitude} onChange={(event) => setForm({ ...form, longitude: event.target.value })} />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-label-md font-semibold">Radius (km)</span>
            <input className="h-11 rounded-lg bg-surface-container-low px-3.5 outline-none focus:ring-2 focus:ring-primary-container" type="number" step="any" value={form.radiusKm} onChange={(event) => setForm({ ...form, radiusKm: event.target.value })} />
          </label>
          <div>
            <button className="h-11 rounded-lg bg-primary-container px-6 text-label-md font-semibold text-on-primary-container" type="submit">
              Search
            </button>
          </div>
        </form>
        {searched && results.length === 0 && <p className="text-secondary">No active stations inside that radius.</p>}
        {results.length > 0 && (
          <div className="overflow-x-auto rounded-xl bg-surface-container-low">
            <table className="w-full text-left">
              <thead>
                <tr className="bg-surface-container text-label-sm uppercase text-secondary">
                  <th className="px-4 py-3">Station</th>
                  <th className="px-4 py-3">Address</th>
                  <th className="px-4 py-3">Distance (km)</th>
                  <th className="px-4 py-3">Capacity (kW)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/10">
                {results.map((item) => (
                  <tr key={item.station.id}>
                    <td className="px-4 py-3 font-medium">{item.station.name}</td>
                    <td className="px-4 py-3">{item.station.address}</td>
                    <td className="px-4 py-3">{Number(item.distanceKm).toFixed(2)}</td>
                    <td className="px-4 py-3">{item.station.capacityKw}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
