// EditBooking.jsx — Web desk form that changes the station or slot of an existing booking.
// Author: M.T.C PEIRIS  it23201200

import { useState } from 'react';
import { formatDateTime, formatLongDate, formatMonth, formatTimeRange } from '../format';
import { useNow, useReservation, useStationSlots } from '../hooks';
import { reservationsApi } from '../../../api.js';
import { ensureStoredSlot } from '../storedSlot';
import { modificationWindow } from '../reservationRules';
import SlotPicker from './SlotPicker';
import StationPicker from './StationPicker';
import {
  DetailList,
  ErrorNotice,
  HeaderStat,
  Icon,
  Notice,
  PageHeader,
  PanelHeader,
  Spinner,
  StatusBadge,
  StepHeader,
  ValueStack,
} from './ui';

// Loads a reservation and lets staff move it to another slot.
export default function EditBooking({ reservationId, refData, onBack, onUpdated }) {
  const now = useNow();
  const { summary, error: loadError, loading } = useReservation(reservationId);

  const [chosenStationId, setChosenStationId] = useState(null);
  const [slotId, setSlotId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [slotRefresh, setSlotRefresh] = useState(0);

  const reservation = summary?.reservation;
  const stationId = chosenStationId ?? reservation?.stationId ?? '';
  const { slots, schedules, loading: slotsLoading, error: slotsError } = useStationSlots(stationId, slotRefresh);

  if (loading || loadError) {
    return (
      <div className="rm-page">
        <PageHeader onBack={onBack} backLabel="Reservation details" eyebrow="Change" title="Change booking" />
        {loading ? <Spinner /> : <ErrorNotice error={loadError} />}
      </div>
    );
  }

  const changeWindow = modificationWindow(reservation, now);
  const currentStation = refData.stationById.get(reservation.stationId);
  const station = refData.stationById.get(stationId);
  const newSlot = slots.find((item) => item.id === slotId);
  const canSubmit = changeWindow.allowed && newSlot && !submitting;

  // Selects a different station and clears the current slot choice.
  function handleStationChange(id) {
    setChosenStationId(id);
    setSlotId('');
    setError(null);
  }

  // Saves the new slot and opens the change summary.
  async function handleSubmit(event) {
    event.preventDefault();
    if (!canSubmit) return;

    setSubmitting(true);
    setError(null);

    try {
      const realSlotId = await ensureStoredSlot(stationId, newSlot);
      const result = await reservationsApi.update(reservation.id, {
        slotId: realSlotId,
        stationId,
        version: reservation.version,
      });
      onUpdated(result, summary);
    } catch (err) {
      setError(err);
      setSubmitting(false);

      if (err.status === 409) {
        setSlotId('');
        setSlotRefresh((value) => value + 1);
      }
    }
  }

  return (
    <form className="rm-page" onSubmit={handleSubmit}>
      <PageHeader
        onBack={onBack}
        backLabel="Reservation details"
        eyebrow="Change booking"
        title="Move to a different slot"
        description="Pick a new slot. If it can't be booked, the current booking stays exactly as it is."
        aside={
          changeWindow.deadline && (
            <HeaderStat
              icon="lock_clock"
              label="Change deadline"
              value={formatDateTime(changeWindow.deadline)}
            />
          )
        }
      />

      {!changeWindow.allowed && (
        <Notice tone="warning" title="This booking can no longer be changed">
          {changeWindow.reason}
        </Notice>
      )}

      <div className="rm-layout">
        <div className="rm-layout__main">
          <section className="rm-card rm-card--tint">
            <StepHeader title="Current booking" aside={<StatusBadge status={reservation.status} />} />
            <DetailList
              items={[
                [
                  'Station',
                  <ValueStack
                    key="s"
                    main={currentStation?.name ?? reservation.stationId}
                    sub={currentStation?.address}
                  />,
                ],
                ['Date', formatLongDate(reservation.slotStartTimeUtc)],
                ['Time', formatTimeRange(reservation.slotStartTimeUtc, reservation.slotEndTimeUtc)],
              ]}
            />
          </section>

          <section className="rm-card">
            <StepHeader number={1} title="Select station" />
            <StationPicker
              stations={refData.stations}
              selectedId={stationId}
              onSelect={handleStationChange}
            />
          </section>

          <section className="rm-card">
            <StepHeader
              number={2}
              title="New date & slot"
              aside={
                <span className="rm-muted rm-inline-icon">
                  <Icon name="calendar_month" />
                  {formatMonth(now)}
                </span>
              }
            />
            {slotsLoading ? (
              <Spinner label="Loading slots…" />
            ) : slotsError ? (
              <ErrorNotice error={slotsError} />
            ) : (
              <SlotPicker
                key={stationId}
                station={station}
                slots={slots}
                schedules={schedules}
                selectedSlotId={slotId}
                currentSlotId={reservation.slotId}
                onSelect={(item) => {
                  setSlotId(item.id);
                  setError(null);
                }}
              />
            )}
          </section>
        </div>

        <aside className="rm-layout__side">
          <div className="rm-panel">
            <PanelHeader eyebrow="Change booking" title="Change summary" icon="swap_horiz" />
            <DetailList
              variant="summary"
              items={[
                [
                  'From',
                  <ValueStack
                    key="from"
                    main={formatLongDate(reservation.slotStartTimeUtc)}
                    sub={formatTimeRange(reservation.slotStartTimeUtc, reservation.slotEndTimeUtc)}
                  />,
                ],
                [
                  'To',
                  newSlot ? (
                    <ValueStack
                      key="to"
                      main={formatLongDate(newSlot.startTimeUtc)}
                      sub={formatTimeRange(newSlot.startTimeUtc, newSlot.endTimeUtc)}
                    />
                  ) : (
                    '—'
                  ),
                ],
                ['Station', station?.name ?? '—'],
              ]}
            />

            <div className="rm-panel__status">
              <span className="rm-muted">Status after change</span>
              <StatusBadge status="Pending" label="Pending approval" />
            </div>

            {reservation.status === 'Approved' && (
              <Notice tone="warning" title="Approval will be cleared">
                After the change this booking returns to Pending and needs approval again. Its
                current QR code will no longer be valid.
              </Notice>
            )}

            <ErrorNotice error={error} />
            {error?.status === 409 && (
              <p className="rm-panel__policy">The original booking has not been changed.</p>
            )}

            <button
              type="submit"
              className="rm-button rm-button--primary rm-button--block rm-button--lg"
              disabled={!canSubmit}
            >
              <Icon name="check_circle" />
              {submitting ? 'Saving…' : 'Save change'}
            </button>
          </div>
        </aside>
      </div>
    </form>
  );
}
