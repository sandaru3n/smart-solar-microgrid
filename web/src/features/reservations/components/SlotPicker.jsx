// SlotPicker.jsx — Day and slot picker for the seven-day booking window.
// Author: M.T.C PEIRIS  it23201200

import { useMemo, useState } from 'react';
import { TIME_ZONE, dayChipLabel, dayKey, formatTimeRange } from '../format';
import { useNow } from '../hooks';
import { HOUR_MS, slotAvailability } from '../reservationRules';
import { Icon } from './ui';

const DAYS_SHOWN = 8;

// Builds the days that can be booked, starting from today.
function buildDays(now) {
  const start = new Date(now);
  start.setHours(0, 0, 0, 0);

  return Array.from({ length: DAYS_SHOWN }, (_, index) => {
    const day = new Date(start);
    day.setDate(start.getDate() + index);
    return day;
  });
}

// Returns the short label shown on a slot card.
function slotTag(entry, remaining) {
  if (entry.isCurrent) return { label: 'Current booking', tone: 'primary', icon: 'event_available' };
  if (!entry.selectable) return { label: entry.reason, tone: 'danger', icon: 'block' };
  if (remaining === 1) return { label: 'Last space', tone: 'primary', icon: 'bolt' };

  const hours = (new Date(entry.slot.endTimeUtc) - new Date(entry.slot.startTimeUtc)) / HOUR_MS;
  return { label: `${hours} hr window`, tone: 'neutral', icon: null };
}

// Returns the weekday name for a booking day.
function weekdayName(date) {
  return date.toLocaleDateString('en-US', { weekday: 'long' });
}

// Finds the station schedule that matches one weekday.
function scheduleFor(schedules, day) {
  return schedules.find((item) => item.day === weekdayName(day)) ?? null;
}

// Lets the user pick an open slot inside the booking window.
export default function SlotPicker({ station, slots, schedules = [], selectedSlotId, onSelect, currentSlotId }) {
  const now = useNow();
  const [chosenDay, setChosenDay] = useState(null);

  const days = useMemo(() => buildDays(now), [now]);

  const slotsByDay = useMemo(() => {
    const groups = new Map();

    for (const slot of slots) {
      const isCurrent = slot.id === currentSlotId;
      const availability = slotAvailability(slot, station, now);
      const entry = {
        slot,
        isCurrent,
        selectable: availability.bookable && !isCurrent,
        reason: availability.reason,
      };

      const key = dayKey(slot.startTimeUtc);
      groups.set(key, [...(groups.get(key) ?? []), entry]);
    }

    return groups;
  }, [slots, station, now, currentSlotId]);

  const firstOpenDay = days.find((day) =>
    slotsByDay.get(dayKey(day))?.some((entry) => entry.selectable),
  );

  const activeDay = chosenDay ?? dayKey(firstOpenDay ?? days[0]);
  const entries = slotsByDay.get(activeDay) ?? [];

  return (
    <div className="rm-slot-picker">
      <div>
        <p className="rm-field-label">Select date</p>
        <div className="rm-day-grid" role="tablist" aria-label="Choose a day">
          {days.map((day, index) => {
            const key = dayKey(day);
            const dayEntries = slotsByDay.get(key) ?? [];
            const openCount = dayEntries.filter((entry) => entry.selectable).length;
            const label = dayChipLabel(day, index);
            const closed = scheduleFor(schedules, day)?.isAvailable === false;

            let countLabel = `${openCount} open`;
            if (closed && dayEntries.length === 0) countLabel = 'Closed';
            else if (dayEntries.length === 0) countLabel = 'No slots';
            else if (openCount === 0) countLabel = 'None open';

            return (
              <button
                key={key}
                type="button"
                role="tab"
                aria-selected={key === activeDay}
                className={`rm-day${key === activeDay ? ' is-active' : ''}`}
                onClick={() => setChosenDay(key)}
              >
                <span className="rm-day__weekday">{label.weekday}</span>
                <span className="rm-day__number">{label.day}</span>
                <span className={`rm-day__count${openCount === 0 ? ' is-empty' : ''}`}>
                  {countLabel}
                </span>
              </button>
            );
          })}
        </div>
      </div>

      <div className="rm-slot-section">
        <div className="rm-slot-section__head">
          <p className="rm-field-label">Available slots</p>
          <span className="rm-time-zone">
            <Icon name="schedule" />
            {TIME_ZONE}
          </span>
        </div>

        {entries.length === 0 ? (
          <p className="rm-empty">
            {days.some((day) => dayKey(day) === activeDay && scheduleFor(schedules, day)?.isAvailable === false)
              ? 'This station is closed on this day.'
              : 'No energy slots scheduled for this day.'}
          </p>
        ) : (
          <div className="rm-slot-list" role="radiogroup" aria-label="Energy slot">
            {entries.map((entry) => {
              const { slot, isCurrent, selectable } = entry;
              const remaining = Math.max(0, slot.maximumBookings - slot.reservedBookings);
              const selected = slot.id === selectedSlotId;
              const tag = slotTag(entry, remaining);

              return (
                <button
                  key={slot.id}
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  disabled={!selectable}
                  className={[
                    'rm-slot',
                    selected && 'is-selected',
                    isCurrent && 'is-current',
                    !selectable && 'is-disabled',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                  onClick={() => onSelect(slot)}
                >
                  <span className="rm-slot__radio" aria-hidden="true" />
                  <span className="rm-slot__main">
                    <span className="rm-slot__title">
                      <span className="rm-slot__time">
                        {formatTimeRange(slot.startTimeUtc, slot.endTimeUtc)}
                      </span>
                      <span className={`rm-pill rm-pill--${tag.tone}`}>
                        {tag.icon && <Icon name={tag.icon} />}
                        {tag.label}
                      </span>
                    </span>
                    <span className="rm-slot__meta">
                      {slot.maximumBookings} booking spaces · {slot.reservedBookings} reserved
                    </span>
                  </span>
                  <span className="rm-slot__side">
                    <span className={`rm-slot__left${selectable && remaining <= 1 ? ' is-low' : ''}`}>
                      {remaining === 1 ? '1 space left' : `${remaining} spaces left`}
                    </span>
                    <span className="rm-slot__of">of {slot.maximumBookings}</span>
                  </span>
                </button>
              );
            })}
          </div>
        )}
      </div>

      <div className="rm-info-box">
        <Icon name="info" />
        <p>
          Slots can be booked up to 7 days ahead. Changes and cancellations close 12 hours before
          the slot starts.
        </p>
      </div>
    </div>
  );
}
