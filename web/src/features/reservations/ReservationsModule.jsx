// ReservationsModule.jsx — Routes the web reservation screens for create, edit, cancel and summary.
// Author: M.T.C PEIRIS  it23201200

import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import ActionSummary from './components/ActionSummary';
import CreateBooking from './components/CreateBooking';
import EditBooking from './components/EditBooking';
import ReservationDetails from './components/ReservationDetails';
import ReservationHome from './components/ReservationHome';
import { Icon, PageHeader } from './components/ui';
import { useReferenceData } from './hooks';
import { bindRouter, navigate, paths, useRoute } from './router';
import './reservations.css';

const APP_NAME = 'Smart Solar Microgrid';

const PAGE_TITLES = {
  home: 'All reservations',
  create: 'Create reservations',
  details: 'Reservation details',
  edit: 'Change booking',
  summary: 'Booking summary',
  notFound: 'Page not found',
};

// Shows the reservation screen that matches the current address.
export default function ReservationsModule() {
  const refData = useReferenceData();
  const route = useRoute();
  const routerNavigate = useNavigate();

  useEffect(() => {
    bindRouter(routerNavigate);
  }, [routerNavigate]);

  const missingSummary = route.name === 'summary' && !route.summary;

  useEffect(() => {
    if (missingSummary) navigate(paths.details(route.id), { replace: true });
  }, [missingSummary, route.id]);

  useEffect(() => {
    const title = PAGE_TITLES[route.name];
    document.title = title ? `${title} · ${APP_NAME}` : APP_NAME;
  }, [route.name]);

  // Returns to the reservation list.
  const goHome = () => navigate(paths.home());
  // Opens the create-booking form.
  const goCreate = () => navigate(paths.create());
  // Opens the details screen for one reservation.
  const goDetails = (id) => navigate(paths.details(id));
  // Opens the summary screen after create, update or cancel.
  const showSummary = (action) => (summary, previous = null) =>
    navigate(paths.summary(summary.reservationId, action), { state: { summary, previous } });

  return (
    <div className="rm-module">
      {route.name === 'home' && (
        <ReservationHome refData={refData} onCreate={goCreate} onOpen={goDetails} />
      )}

      {route.name === 'create' && (
        <CreateBooking onBack={goHome} onCreated={showSummary('created')} />
      )}

      {route.name === 'details' && (
        <ReservationDetails
          key={route.id}
          reservationId={route.id}
          refData={refData}
          onBack={goHome}
          onEdit={(id) => navigate(paths.edit(id))}
          onCancelled={showSummary('cancelled')}
        />
      )}

      {route.name === 'edit' && (
        <EditBooking
          key={route.id}
          reservationId={route.id}
          refData={refData}
          onBack={() => goDetails(route.id)}
          onUpdated={showSummary('updated')}
        />
      )}

      {route.name === 'summary' && !missingSummary && (
        <ActionSummary
          action={route.action}
          summary={route.summary}
          previous={route.previous}
          refData={refData}
          onViewDetails={goDetails}
          onNewBooking={goCreate}
          onHome={goHome}
        />
      )}

      {route.name === 'notFound' && (
        <div className="rm-page rm-page--narrow">
          <PageHeader onBack={goHome} backLabel="Reservations" eyebrow="404" title="Page not found" />
          <section className="rm-card">
            <p className="rm-muted">
              There is no reservation page at <code className="rm-reference">{window.location.pathname}</code>.
            </p>
            <button type="button" className="rm-button rm-button--primary" onClick={goHome}>
              <Icon name="home" />
              Go to reservations
            </button>
          </section>
        </div>
      )}
    </div>
  );
}
