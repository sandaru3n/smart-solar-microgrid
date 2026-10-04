import { Icon } from './ui';

export default function StationPicker({ stations, selectedId, onSelect }) {
  return (
    <div className="rm-station-grid" role="radiogroup" aria-label="Station">
      {stations.map((station) => {
        const selected = station.id === selectedId;

        let marker = <span className="rm-station__radio" aria-hidden="true" />;
        if (!station.isActive) marker = <Icon name="block" className="rm-station__blocked" />;
        else if (selected) marker = <Icon name="check_circle" className="rm-station__check" />;

        return (
          <button
            key={station.id}
            type="button"
            role="radio"
            aria-checked={selected}
            disabled={!station.isActive}
            className={`rm-station${selected ? ' is-selected' : ''}`}
            onClick={() => onSelect(station.id)}
          >
            <span className="rm-station__top">
              <span className={station.isActive ? 'rm-badge rm-badge--approved' : 'rm-pill rm-pill--danger'}>
                {station.isActive ? 'Active' : 'Inactive'}
              </span>
              {marker}
            </span>
            <span className="rm-station__name">{station.name}</span>
            <span className="rm-station__address">{station.address}</span>
            <span className="rm-station__foot">
              {station.isActive ? (
                <>
                  <span>Capacity</span>
                  <strong>{station.capacityKw} kW</strong>
                </>
              ) : (
                <>
                  <span>Bookings</span>
                  <strong className="rm-text-danger">Unavailable</strong>
                </>
              )}
            </span>
          </button>
        );
      })}
    </div>
  );
}
