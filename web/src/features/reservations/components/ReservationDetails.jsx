// ReservationDetails.jsx — Shows one booking and the actions to change, cancel or approve it.
// Author: M.T.C PEIRIS  it23201200

import { useState } from 'react';
import { formatDateTime, formatDuration, formatLongDate, formatTimeRange } from '../format';
import { useNow, useReservation } from '../hooks';
import { reservationsApi } from '../../../api.js';
import { modificationWindow } from '../reservationRules';
import {
  DetailList,
  ErrorNotice,
  HeaderStat,
  Icon,
  Notice,
  PageHeader,
  PanelHeader,
  ProsumerLabel,
  Reference,
  Spinner,
  StatusBadge,
  StepHeader,
  ValueStack,
} from './ui';

// Asks for confirmation before a booking is cancelled.
function CancelDialog({ reservation, stationName, onKeep, onConfirmed }) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  // Sends the confirmed cancel or reject request.
  async function handleConfirm() {
    setSubmitting(true);
    setError(null);

    try {
      const summary = await reservationsApi.cancel(reservation.id, { version: reservation.version });
      onConfirmed(summary);
    } catch (err) {
      setError(err);
      setSubmitting(false);
    }
  }

  return (
    <div className="rm-dialog-backdrop" role="presentation">
      <div className="rm-dialog" role="dialog" aria-modal="true" aria-labelledby="rm-cancel-title">
        <span className="rm-dialog__icon">
          <Icon name="event_busy" />
        </span>
        <h2 id="rm-cancel-title">Cancel this booking?</h2>
        <p className="rm-muted">
          {stationName} · {formatLongDate(reservation.slotStartTimeUtc)},{' '}
          {formatTimeRange(reservation.slotStartTimeUtc, reservation.slotEndTimeUtc)}
        </p>
        <ul className="rm-bullets">
          <li>The booking status changes to Cancelled.</li>
          <li>Its space in the slot is released for other prosumers.</li>
          <li>The record is kept in the booking history.</li>
        </ul>

        <ErrorNotice error={error} />

        <div className="rm-dialog__actions">
          <button type="button" className="rm-button rm-button--ghost" onClick={onKeep} disabled={submitting}>
            Keep booking
          </button>
          <button
            type="button"
            className="rm-button rm-button--danger"
            onClick={handleConfirm}
            disabled={submitting}
          >
            {submitting ? 'Cancelling…' : 'Cancel booking'}
          </button>
        </div>
      </div>
    </div>
  );
}

// Asks for confirmation before a pending booking is rejected.
function RejectDialog({ reservation, stationName, onKeep, onConfirmed }) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  // Sends the confirmed cancel or reject request.
  async function handleConfirm() {
    setSubmitting(true);
    setError(null);

    try {
      const summary = await reservationsApi.reject(reservation.id, { version: reservation.version });
      onConfirmed(summary);
    } catch (err) {
      setError(err);
      setSubmitting(false);
    }
  }

  return (
    <div className="rm-dialog-backdrop" role="presentation">
      <div className="rm-dialog" role="dialog" aria-modal="true" aria-labelledby="rm-reject-title">
        <span className="rm-dialog__icon">
          <Icon name="cancel" />
        </span>
        <h2 id="rm-reject-title">Reject this booking?</h2>
        <p className="rm-muted">
          {stationName} · {formatLongDate(reservation.slotStartTimeUtc)},{' '}
          {formatTimeRange(reservation.slotStartTimeUtc, reservation.slotEndTimeUtc)}
        </p>
        <ul className="rm-bullets">
          <li>The booking status changes to Rejected.</li>
          <li>Its space in the slot is released.</li>
          <li>The prosumer is not given this energy slot.</li>
        </ul>
        <ErrorNotice error={error} />
        <div className="rm-dialog__actions">
          <button type="button" className="rm-button rm-button--ghost" onClick={onKeep} disabled={submitting}>
            Keep pending
          </button>
          <button type="button" className="rm-button rm-button--danger" onClick={handleConfirm} disabled={submitting}>
            {submitting ? 'Rejecting…' : 'Reject booking'}
          </button>
        </div>
      </div>
    </div>
  );
}

// Loads one reservation and shows its details and allowed actions.
export default function ReservationDetails({ reservationId, refData, onBack, onEdit, onCancelled }) {
  const now = useNow();
  const [confirming, setConfirming] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [deciding, setDeciding] = useState(false);
  const [decisionError, setDecisionError] = useState(null);
  const [decisionMessage, setDecisionMessage] = useState(null);
  const { summary, error, loading } = useReservation(reservationId, reloadKey);

  if (loading || error) {
    return (
      <div className="rm-page">
        <PageHeader onBack={onBack} backLabel="Reservations" eyebrow="Details" title="Reservation details" />
        {loading ? <Spinner /> : <ErrorNotice error={error} />}
      </div>
    );
  }

  const reservation = summary.reservation;
  const pending = reservation.status === 'Pending';
  const station = refData.stationById.get(reservation.stationId);

  // Approves a pending reservation from the details screen.
  async function handleApprove() {
    setDeciding(true);
    setDecisionError(null);
    setDecisionMessage(null);

    try {
      const result = await reservationsApi.approve(reservation.id, { version: reservation.version });
      setDecisionMessage(result.message || 'Reservation approved.');
      setReloadKey((value) => value + 1);
    } catch (err) {
      setDecisionError(err);
    } finally {
      setDeciding(false);
    }
  }
  const prosumer = refData.prosumerByNic.get(reservation.prosumerId);
  const changeWindow = modificationWindow(reservation, now);
  const untilStart = new Date(reservation.slotStartTimeUtc).getTime() - now;

  return (
    <div className="rm-page">
      <PageHeader
        onBack={onBack}
        backLabel="Reservations"
        eyebrow="Details"
        title={station?.name ?? 'Reservation'}
        description={`${formatLongDate(reservation.slotStartTimeUtc)} · ${formatTimeRange(
          reservation.slotStartTimeUtc,
          reservation.slotEndTimeUtc,
        )}`}
        aside={
          <HeaderStat
            icon="timer"
            label="Starts in"
            value={untilStart > 0 ? formatDuration(untilStart) : 'Already started'}
          />
        }
      />

      <div className="rm-layout">
        <div className="rm-layout__main">
          <section className="rm-card">
            <StepHeader title="Booking" aside={<StatusBadge status={reservation.status} />} />
            <DetailList
              items={[
                ['Reference', <Reference key="ref" id={reservation.id} />],
                ['Prosumer', <ProsumerLabel key="p" nic={reservation.prosumerId} prosumer={prosumer} />],
                [
                  'Station',
                  <ValueStack key="s" main={station?.name ?? reservation.stationId} sub={station?.address} />,
                ],
                ['Date', formatLongDate(reservation.slotStartTimeUtc)],
                ['Time', formatTimeRange(reservation.slotStartTimeUtc, reservation.slotEndTimeUtc)],
                ['Created', formatDateTime(reservation.createdAtUtc)],
                ['Last updated', formatDateTime(reservation.updatedAtUtc)],
                ...(reservation.cancelledAtUtc
                  ? [['Cancelled', formatDateTime(reservation.cancelledAtUtc)]]
                  : []),
                ['Version', reservation.version],
              ]}
            />
          </section>
        </div>

        <aside className="rm-layout__side">
          <div className="rm-panel">
            <PanelHeader eyebrow="Manage" title="Actions" icon="tune" />

            {pending && (
              <Notice tone="warning" title="Waiting for approval">
                This booking came from the prosumer app. Approve it or reject it and release the slot.
              </Notice>
            )}
            {decisionMessage && (
              <Notice tone="success" title="Updated">
                {decisionMessage}
              </Notice>
            )}
            <ErrorNotice error={decisionError} />

            {pending && (
              <>
                <button
                  type="button"
                  className="rm-button rm-button--primary rm-button--block rm-button--lg"
                  disabled={deciding}
                  onClick={handleApprove}
                >
                  <Icon name="check" />
                  {deciding ? 'Approving…' : 'Approve booking'}
                </button>
                <button
                  type="button"
                  className="rm-button rm-button--danger-outline rm-button--block"
                  disabled={deciding}
                  onClick={() => setRejecting(true)}
                >
                  <Icon name="close" />
                  Reject booking
                </button>
              </>
            )}

            {changeWindow.allowed ? (
              <Notice tone="info" title="Changes allowed">
                Can be changed or cancelled until {formatDateTime(changeWindow.deadline)} (
                {formatDuration(changeWindow.deadline - now)} from now).
              </Notice>
            ) : (
              <Notice tone="warning" title="Changes closed">
                {changeWindow.reason}
                {changeWindow.deadline && ` The deadline was ${formatDateTime(changeWindow.deadline)}.`}
              </Notice>
            )}

            {reservation.status === 'Approved' && changeWindow.allowed && (
              <p className="rm-panel__policy">
                Changing an approved booking returns it to Pending and it needs approval again.
              </p>
            )}

            <button
              type="button"
              className="rm-button rm-button--primary rm-button--block rm-button--lg"
              disabled={!changeWindow.allowed}
              onClick={() => onEdit(reservation.id)}
            >
              <Icon name="edit_calendar" />
              Change slot
            </button>
            <button
              type="button"
              className="rm-button rm-button--danger-outline rm-button--block"
              disabled={!changeWindow.allowed}
              onClick={() => setConfirming(true)}
            >
              <Icon name="event_busy" />
              Cancel booking
            </button>
          </div>
        </aside>
      </div>

      {confirming && (
        <CancelDialog
          reservation={reservation}
          stationName={station?.name ?? 'Station'}
          onKeep={() => setConfirming(false)}
          onConfirmed={(result) => onCancelled(result, summary)}
        />
      )}

      {rejecting && (
        <RejectDialog
          reservation={reservation}
          stationName={station?.name ?? 'Station'}
          onKeep={() => setRejecting(false)}
          onConfirmed={(result) => {
            setRejecting(false);
            setDecisionError(null);
            setDecisionMessage(result.message || 'Reservation rejected.');
            setReloadKey((value) => value + 1);
          }}
        />
      )}
    </div>
  );
}
