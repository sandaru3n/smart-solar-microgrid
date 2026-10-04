const WIDTHS = ['w-24', 'w-32', 'w-40', 'w-20', 'w-28', 'w-16']

export default function TableSkeleton({ rows = 5, columns = 6, label = 'Loading reservations' }) {
  return (
    <div role="status" aria-live="polite" aria-busy="true">
      <span className="sr-only">{label}…</span>

      <div className="hidden lg:block" aria-hidden="true">
        <div className="flex gap-6 bg-surface-container px-6 py-4">
          {Array.from({ length: columns }, (_, i) => (
            <div key={i} className="h-3 w-16 rounded bg-outline-variant/50" />
          ))}
        </div>
        {Array.from({ length: rows }, (_, row) => (
          <div key={row} className="flex items-center gap-6 border-t border-[#FFDD19]/30 bg-[#FFDD19]/15 px-6 py-4">
            {Array.from({ length: columns }, (_, col) => (
              <div
                key={col}
                className={`h-4 animate-pulse rounded bg-surface-container-high motion-reduce:animate-none ${WIDTHS[(row + col) % WIDTHS.length]}`}
              />
            ))}
          </div>
        ))}
      </div>

      <div className="flex flex-col gap-3 p-4 lg:hidden" aria-hidden="true">
        {Array.from({ length: Math.min(rows, 3) }, (_, i) => (
          <div key={i} className="flex flex-col gap-3 rounded-xl border border-outline-variant/30 p-4">
            <div className="h-4 w-32 animate-pulse rounded bg-surface-container-high motion-reduce:animate-none" />
            <div className="h-3 w-48 animate-pulse rounded bg-surface-container-high motion-reduce:animate-none" />
            <div className="h-3 w-40 animate-pulse rounded bg-surface-container-high motion-reduce:animate-none" />
          </div>
        ))}
      </div>
    </div>
  )
}
