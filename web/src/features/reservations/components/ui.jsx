const STATUS_TEXT = {
  400: 'Bad request',
  401: 'Unauthorized',
  403: 'Forbidden',
  404: 'Not found',
  409: 'Conflict',
};

const NOTICE_ICONS = {
  info: 'info',
  warning: 'warning',
  error: 'error',
  success: 'check_circle',
};

export function Icon({ name, className }) {
  return (
    <span className={['material-symbols-outlined', className].filter(Boolean).join(' ')} aria-hidden="true">
      {name}
    </span>
  );
}

export function StatusBadge({ status, label }) {
  return <span className={`rm-badge rm-badge--${status.toLowerCase()}`}>{label ?? status}</span>;
}

export function Notice({ tone = 'info', title, children }) {
  return (
    <div className={`rm-notice rm-notice--${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <Icon name={NOTICE_ICONS[tone]} className="rm-notice__icon" />
      <div className="rm-notice__content">
        {title && <strong className="rm-notice__title">{title}</strong>}
        {children && <div>{children}</div>}
      </div>
    </div>
  );
}

export function ErrorNotice({ error }) {
  if (!error) return null;

  const label = error.status ? `${error.status} ${STATUS_TEXT[error.status] ?? 'Error'}` : 'Error';

  return (
    <Notice tone="error" title={label}>
      {error.message}
    </Notice>
  );
}

export function DetailList({ items, variant }) {
  return (
    <dl className={`rm-details${variant ? ` rm-details--${variant}` : ''}`}>
      {items.map(([label, value]) => (
        <div key={label} className="rm-details__row">
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

export function ValueStack({ main, sub }) {
  return (
    <span className="rm-value-stack">
      <span>{main}</span>
      {sub && <span className="rm-value-stack__sub">{sub}</span>}
    </span>
  );
}

export function PageHeader({ eyebrow, title, description, onBack, backLabel = 'Back', aside }) {
  return (
    <header className="rm-page-header">
      <div className="rm-page-header__main">
        {onBack ? (
          <div className="rm-breadcrumb">
            <button type="button" className="rm-breadcrumb__back" onClick={onBack}>
              <Icon name="arrow_back" />
              {backLabel}
            </button>
            {eyebrow && (
              <>
                <span className="rm-breadcrumb__sep">/</span>
                <span className="rm-tag">{eyebrow}</span>
              </>
            )}
          </div>
        ) : (
          eyebrow && <span className="rm-tag">{eyebrow}</span>
        )}
        <h1>{title}</h1>
        {description && <p className="rm-page-header__description">{description}</p>}
      </div>
      {aside}
    </header>
  );
}

export function HeaderStat({ icon, label, value, hint }) {
  return (
    <div className="rm-header-stat">
      <span className="rm-header-stat__icon">
        {typeof icon === 'string' ? <Icon name={icon} /> : icon}
      </span>
      <div>
        <span className="rm-header-stat__label">{label}</span>
        <span className="rm-header-stat__value">{value}</span>
        {hint && <span className="rm-header-stat__hint">{hint}</span>}
      </div>
    </div>
  );
}

export function StepHeader({ number, title, aside }) {
  return (
    <div className="rm-step-head">
      <div className="rm-step-head__title">
        {number != null && <span className="rm-step-number">{number}</span>}
        <h2>{title}</h2>
      </div>
      {aside && <div className="rm-step-head__aside">{aside}</div>}
    </div>
  );
}

export function PanelHeader({ eyebrow, title, icon }) {
  return (
    <div className="rm-panel-head">
      <div>
        {eyebrow && <span className="rm-panel-head__eyebrow">{eyebrow}</span>}
        <h3>{title}</h3>
      </div>
      {icon && (
        <span className="rm-panel-head__icon">
          <Icon name={icon} />
        </span>
      )}
    </div>
  );
}

export function Spinner({ label = 'Loading…' }) {
  return (
    <div className="rm-spinner" role="status">
      <span className="rm-spinner__dot" aria-hidden="true" />
      {label}
    </div>
  );
}

export function Reference({ id }) {
  return <code className="rm-reference">{id}</code>;
}

export function ProsumerLabel({ nic, prosumer }) {
  return <ValueStack main={prosumer?.name ?? 'Unknown prosumer'} sub={`NIC ${nic}`} />;
}
