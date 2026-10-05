// ReservationHome.jsx — Reservation list used to open, create, change or cancel a booking.
// Author: M.T.C PEIRIS  it23201200

import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { reservationsApi } from '../../../api.js';
import { useAuth } from '../../../auth/AuthContext';
import { canApprove } from '../../bookings/permissions';
import { formatDate, formatTimeRange, initials } from '../format';
import { ErrorNotice, Icon, Notice, PageHeader, Spinner, StatusBadge, StepHeader } from './ui';

// Builds the station, prosumer and slot text for one list row.
function bookingLabel(item, refData) {
  const name = refData.prosumerByNic.get(item.prosumerId)?.name ?? item.prosumerId;
  const stationName = item.stationName ?? refData.stationById.get(item.stationId)?.name ?? item.stationId;
  const when = item.slotStartTimeUtc
    ? `${formatDate(item.slotStartTimeUtc)} · ${formatTimeRange(item.slotStartTimeUtc, item.slotEndTimeUtc)}`
    : 'Slot time unavailable';

  return { name, stationName, when };
}

const STATUSES = ['Pending', 'Approved', 'Cancelled', 'Rejected', 'Completed'];

// Shows reservations and the actions that open create, details and decisions.
export default function ReservationHome({ refData, onCreate, onOpen }) {
  const { user } = useAuth();
  const [reference, setReference] = useState('');
  const [lookupError, setLookupError] = useState(null);
  const [looking, setLooking] = useState(false);
  const [bookings, setBookings] = useState(null);
  const [listError, setListError] = useState(null);
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [stationFilter, setStationFilter] = useState('');

  useEffect(() => {
    let active = true;

    reservationsApi
      .list()
      .then((page) => {
        if (active) setBookings(page?.items ?? []);
      })
      .catch((error) => {
        if (!active) return;
        setListError(error);
        setBookings([]);
      });

    return () => {
      active = false;
    };
  }, []);

  async function handleLookup(event) {
    event.preventDefault();

    const id = reference.trim();
    if (!id) return;

    setLooking(true);
    setLookupError(null);

    try {
      await reservationsApi.get(id);
      onOpen(id);
    } catch (error) {
      setLookupError(error);
      setLooking(false);
    }
  }

  const pendingCount = (bookings ?? []).filter((item) => item.status === 'Pending').length;
  const showApprovalLink = canApprove(user?.role) && pendingCount > 0;
  const searchText = query.trim().toLowerCase();
  const filtersActive = Boolean(searchText || statusFilter || stationFilter);
  const visibleBookings = (bookings ?? []).filter((item) => {
    if (statusFilter && item.status !== statusFilter) return false;
    if (stationFilter && item.stationId !== stationFilter) return false;
    if (!searchText) return true;

    const label = bookingLabel(item, refData);
    return [label.name, item.prosumerId, label.stationName, item.id, label.when]
      .join(' ')
      .toLowerCase()
      .includes(searchText);
  });

  return (
    <div className="rm-page">
      <PageHeader
        eyebrow="Reservation management"
        title="All reservations"
        description="Every booking, including ones you created and ones waiting for approval. Bookings you create are approved immediately."
      />

      {showApprovalLink && (
        <Notice tone="warning" title={`${pendingCount} ${pendingCount === 1 ? 'booking is' : 'bookings are'} waiting for approval`}>
          Approve or reject prosumer requests in{' '}
          <Link className="rm-text-button" to="/bookings/pending">
            Booking Management → Pending approvals
          </Link>
          .
        </Notice>
      )}

      <div className="rm-home-grid">
        <section className="rm-card rm-card--feature">
          <span className="rm-feature-icon">
            <Icon name="bolt" />
          </span>
          <h2>New booking</h2>
          <p className="rm-muted">
            Book an energy slot for a prosumer at an active station within the next seven days.
          </p>
          <button type="button" className="rm-button rm-button--primary" onClick={onCreate}>
            <Icon name="add" />
            Create booking
          </button>
        </section>

        <section className="rm-card">
          <span className="rm-feature-icon rm-feature-icon--soft">
            <Icon name="search" />
          </span>
          <h2>Find a reservation</h2>
          <p className="rm-muted">Open a booking by its reference to view, change or cancel it.</p>
          <form className="rm-input-row" onSubmit={handleLookup}>
            <label className="rm-visually-hidden" htmlFor="rm-reference">
              Reservation reference
            </label>
            <div className="rm-input-icon">
              <Icon name="confirmation_number" />
              <input
                id="rm-reference"
                className="rm-input rm-input--mono"
                placeholder="e.g. 6710a1f28a1b2c3d4e5f6b01"
                value={reference}
                onChange={(event) => setReference(event.target.value)}
              />
            </div>
            <button type="submit" className="rm-button rm-button--soft" disabled={!reference.trim() || looking}>
              {looking ? 'Opening…' : 'Open'}
            </button>
          </form>
          <ErrorNotice error={lookupError} />
        </section>
      </div>

      <section className="rm-card">
        <StepHeader
          title="Reservations"
          aside={
            bookings && refData.ready ? (
              <span className="rm-chip">
                {visibleBookings.length} of {bookings.length}
              </span>
            ) : null
          }
        />

        <div className="rm-filters">
          <label className="rm-input-icon">
            <span className="rm-visually-hidden">Search reservations</span>
            <Icon name="search" />
            <input
              className="rm-input"
              placeholder="Search name, NIC, station or reference"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
          </label>
          <label>
            <span className="rm-visually-hidden">Status</span>
            <select
              className="rm-input"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value)}
            >
              <option value="">All statuses</option>
              {STATUSES.map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="rm-visually-hidden">Station</span>
            <select
              className="rm-input"
              value={stationFilter}
              onChange={(event) => setStationFilter(event.target.value)}
            >
              <option value="">All stations</option>
              {(refData.stations ?? []).map((station) => (
                <option key={station.id} value={station.id}>
                  {station.name}
                </option>
              ))}
            </select>
          </label>
        </div>
        {filtersActive && (
          <button
            type="button"
            className="rm-text-button"
            onClick={() => {
              setQuery('');
              setStatusFilter('');
              setStationFilter('');
            }}
          >
            Clear search and filters
          </button>
        )}

        {listError && <ErrorNotice error={listError} />}
        {bookings === null || !refData.ready ? (
          <Spinner />
        ) : bookings.length === 0 ? (
          <p className="rm-empty">No reservations yet.</p>
        ) : visibleBookings.length === 0 ? (
          <p className="rm-empty">No reservations match this search.</p>
        ) : (
          <ul className="rm-sample-list">
            {visibleBookings.map((item) => {
              const label = bookingLabel(item, refData);

              return (
                <li key={item.id}>
                  <button type="button" className="rm-sample" onClick={() => onOpen(item.id)}>
                    <span className="rm-sample__who">
                      <span className="rm-avatar rm-avatar--sm">{initials(label.name)}</span>
                      <span className="rm-value-stack">
                        <span className="rm-sample__name">{label.name}</span>
                        <span className="rm-value-stack__sub rm-mono">{item.id}</span>
                      </span>
                    </span>
                    <span className="rm-value-stack">
                      <span>{label.stationName}</span>
                      <span className="rm-value-stack__sub">{label.when}</span>
                    </span>
                    <StatusBadge status={item.status} />
                    <Icon name="chevron_right" className="rm-sample__chevron" />
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}
