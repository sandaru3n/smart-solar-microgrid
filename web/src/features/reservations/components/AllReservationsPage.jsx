// AllReservationsPage.jsx — Reservations menu page that lists pending and approved bookings.
// Author: M.T.C PEIRIS  it23201200

import BookingsProvider from '../../bookings/BookingsProvider'
import ReservationListView from '../../bookings/pages/ReservationListView'

// Shows pending and approved reservations for the Grid Operator menu.
export default function AllReservationsPage() {
  return (
    <BookingsProvider>
      <div className="flex flex-col gap-6">
        <div className="flex flex-col gap-2">
          <span className="inline-flex w-fit rounded-full bg-[#FFDD19] px-3 py-1 text-[11px] font-bold uppercase tracking-wider text-[#1C1914]">
            Reservations
          </span>
          <h1 className="text-2xl font-bold text-[#1C1914]">All reservations</h1>
        </div>
        <ReservationListView mode="open" />
      </div>
    </BookingsProvider>
  )
}
