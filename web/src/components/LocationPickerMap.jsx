// LocationPickerMap.jsx — Google Maps picker used when creating or editing a station location.
import { useEffect, useRef, useState } from 'react'

const MAPS_KEY = import.meta.env.VITE_MAPS_API_KEY || ''
const COLOMBO = { lat: 6.9271, lng: 79.8612 }

let mapsPromise = null

function loadGoogleMaps() {
  if (window.google?.maps?.places) return Promise.resolve(window.google.maps)
  if (window.google?.maps?.importLibrary) {
    return window.google.maps.importLibrary('places').then(() => window.google.maps)
  }
  if (!mapsPromise) {
    mapsPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(MAPS_KEY)}&v=weekly&libraries=places`
      script.async = true
      script.onload = () => resolve(window.google.maps)
      script.onerror = () => {
        mapsPromise = null
        reject(new Error('Failed to load Google Maps.'))
      }
      document.head.appendChild(script)
    })
  }
  return mapsPromise
}

function parsePoint(latitude, longitude) {
  if (latitude === '' || latitude == null || longitude === '' || longitude == null) return null
  const lat = Number(latitude)
  const lng = Number(longitude)
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null
  return { lat, lng }
}

const fieldClass =
  'h-11 w-full rounded-lg bg-surface-container-low px-3.5 text-body-sm text-on-surface shadow-sm outline-none focus:ring-2 focus:ring-primary-container'

/**
 * Google Map location picker. Click the map (or drag the marker) to choose a
 * point; the chosen coordinates are reported through onChange(lat, lng) and
 * the nearest address through onAddressChange. The address field stays
 * editable and suggests places while typing.
 */
export default function LocationPickerMap({ latitude, longitude, address, onChange, onAddressChange }) {
  const containerRef = useRef(null)
  const mapRef = useRef(null)
  const markerRef = useRef(null)
  const geocoderRef = useRef(null)
  const suggestTimer = useRef(null)
  const requestId = useRef(0)
  const onChangeRef = useRef(onChange)
  const onAddressChangeRef = useRef(onAddressChange)
  const [suggestions, setSuggestions] = useState([])
  const [error, setError] = useState(MAPS_KEY ? null : 'Google Maps key missing. Set VITE_MAPS_API_KEY in web/.env.local.')

  onChangeRef.current = onChange
  onAddressChangeRef.current = onAddressChange

  useEffect(() => {
    if (!MAPS_KEY) return undefined
    let cancelled = false
    loadGoogleMaps()
      .then((maps) => {
        if (cancelled || !containerRef.current || mapRef.current) return
        const point = parsePoint(latitude, longitude)
        const map = new maps.Map(containerRef.current, {
          center: point || COLOMBO,
          zoom: point ? 15 : 11,
          zoomControl: false,
          streetViewControl: false,
          mapTypeControl: false,
          fullscreenControl: false,
          clickableIcons: false,
        })
        const marker = new maps.Marker({
          map,
          position: point,
          draggable: true,
        })
        geocoderRef.current = new maps.Geocoder()
        const place = (latLng) => {
          marker.setPosition(latLng)
          onChangeRef.current?.(
            Number(latLng.lat().toFixed(6)),
            Number(latLng.lng().toFixed(6)),
          )
          const token = ++requestId.current
          geocoderRef.current.geocode({ location: latLng }).then((response) => {
            if (token !== requestId.current) return
            const formatted = response.results?.[0]?.formatted_address
            if (formatted) onAddressChangeRef.current?.(formatted)
          }).catch(() => {})
        }
        map.addListener('click', (event) => place(event.latLng))
        marker.addListener('dragend', () => place(marker.getPosition()))
        mapRef.current = map
        markerRef.current = marker
      })
      .catch((loadError) => {
        if (!cancelled) setError(loadError.message)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Keep the marker in sync when latitude/longitude are edited in the inputs.
  useEffect(() => {
    const map = mapRef.current
    const marker = markerRef.current
    if (!map || !marker) return
    const point = parsePoint(latitude, longitude)
    if (!point) return
    const current = marker.getPosition()
    if (current && Math.abs(current.lat() - point.lat) < 1e-9 && Math.abs(current.lng() - point.lng) < 1e-9) {
      return
    }
    marker.setPosition(point)
    map.panTo(point)
  }, [latitude, longitude])

  function zoomBy(delta) {
    const map = mapRef.current
    if (!map) return
    const current = map.getZoom() ?? 11
    map.setZoom(Math.min(21, Math.max(2, current + delta)))
  }

  function onAddressInput(value) {
    requestId.current += 1
    onAddressChangeRef.current?.(value)
    window.clearTimeout(suggestTimer.current)
    const query = value.trim()
    if (query.length < 3 || !window.google?.maps?.places) {
      setSuggestions([])
      return
    }
    suggestTimer.current = window.setTimeout(() => searchAddresses(query), 250)
  }

  async function searchAddresses(query) {
    const token = ++requestId.current
    try {
      const { AutocompleteSuggestion } = await window.google.maps.importLibrary('places')
      const { suggestions: found } = await AutocompleteSuggestion.fetchAutocompleteSuggestions({
        input: query,
        includedRegionCodes: ['lk'],
      })
      if (token !== requestId.current) return
      setSuggestions((found || []).slice(0, 5).map((item) => ({
        id: item.placePrediction.placeId,
        label: item.placePrediction.text.toString(),
        place: item.placePrediction,
      })))
    } catch {
      if (token === requestId.current) setSuggestions([])
    }
  }

  async function chooseSuggestion(item) {
    setSuggestions([])
    const token = ++requestId.current
    try {
      const place = item.place.toPlace()
      await place.fetchFields({ fields: ['formattedAddress', 'location'] })
      if (token !== requestId.current || !place.location) return
      const lat = Number(place.location.lat().toFixed(6))
      const lng = Number(place.location.lng().toFixed(6))
      onAddressChangeRef.current?.(place.formattedAddress || item.label)
      onChangeRef.current?.(lat, lng)
      mapRef.current?.panTo({ lat, lng })
      mapRef.current?.setZoom(16)
    } catch {
      onAddressChangeRef.current?.(item.label)
    }
  }

  if (error) {
    return (
      <div className="flex h-[21.6rem] w-full items-center justify-center rounded-xl bg-surface-container-low px-4 text-center text-body-sm text-secondary">
        {error}
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <label className="flex flex-col gap-1.5">
        <span className="text-label-md font-semibold">Address</span>
        <div className="relative">
        <input
          className={fieldClass}
          value={address}
          autoComplete="off"
          placeholder="Type an address, or pick a point on the map"
          onChange={(event) => onAddressInput(event.target.value)}
          onBlur={() => window.setTimeout(() => setSuggestions([]), 150)}
        />
        {suggestions.length > 0 && (
          <ul className="absolute top-full z-20 mt-1 w-full overflow-hidden rounded-lg bg-white shadow-md">
            {suggestions.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  className="w-full px-3.5 py-2.5 text-left text-body-sm text-on-surface hover:bg-surface-container"
                  onMouseDown={(event) => {
                    event.preventDefault()
                    chooseSuggestion(item)
                  }}
                >
                  {item.label}
                </button>
              </li>
            ))}
          </ul>
        )}
        </div>
        <p className="text-label-sm text-secondary">
          Suggestions appear as you type. Edit the address if the picked one is not correct.
        </p>
      </label>
      <div className="relative h-[21.6rem] w-full">
        <div ref={containerRef} className="h-full w-full rounded-xl bg-surface-container-low shadow-sm" />
        <div className="absolute top-3 right-3 flex flex-col overflow-hidden rounded-lg bg-white shadow-md">
          <button
            type="button"
            aria-label="Zoom in"
            onClick={() => zoomBy(1)}
            className="flex h-9 w-9 items-center justify-center text-lg font-semibold text-on-surface hover:bg-surface-container"
          >
            +
          </button>
          <div className="h-px bg-outline-variant/40" />
          <button
            type="button"
            aria-label="Zoom out"
            onClick={() => zoomBy(-1)}
            className="flex h-9 w-9 items-center justify-center text-lg font-semibold text-on-surface hover:bg-surface-container"
          >
            −
          </button>
        </div>
      </div>
      <p className="text-label-sm text-secondary">
        Click the map or drag the marker to set the address, latitude, and longitude.
      </p>
    </div>
  )
}
