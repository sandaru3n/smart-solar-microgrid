import { useMemo } from 'react';
import { useLocation } from 'react-router-dom';

export const BASE_PATH = '/reservations';

const SUMMARY_ACTIONS = ['created', 'updated', 'cancelled'];

let routerNavigate = null;

export function bindRouter(navigate) {
  routerNavigate = navigate;
}

export const paths = {
  home: () => BASE_PATH,
  create: () => `${BASE_PATH}/new`,
  pending: () => `${BASE_PATH}/pending`,
  details: (id) => `${BASE_PATH}/${encodeURIComponent(id)}`,
  edit: (id) => `${BASE_PATH}/${encodeURIComponent(id)}/edit`,
  summary: (id, action) => `${BASE_PATH}/${encodeURIComponent(id)}/${action}`,
};

export function navigate(path, { state = null, replace = false } = {}) {
  if (routerNavigate) {
    routerNavigate(path, { state, replace });
  } else if (replace) {
    window.history.replaceState(state, '', path);
  } else {
    window.history.pushState(state, '', path);
  }

  window.scrollTo({ top: 0 });
}

export function isReservationsPath(pathname) {
  const path = pathname.replace(/\/+$/, '') || '/';
  return path === BASE_PATH || path.startsWith(`${BASE_PATH}/`);
}

export function matchRoute(pathname, state) {
  const path = pathname.replace(/\/+$/, '') || '/';

  if (!isReservationsPath(path)) return { name: 'outside' };

  const parts = path
    .slice(BASE_PATH.length)
    .split('/')
    .filter(Boolean)
    .map(decodeURIComponent);

  if (parts.length === 0) return { name: 'home' };
  if (parts.length === 1 && parts[0] === 'new') return { name: 'create' };
  if (parts.length === 1 && parts[0] === 'pending') return { name: 'pending' };
  if (parts.length === 1 && parts[0] === 'all') return { name: 'outside' };

  const [id, sub] = parts;

  if (parts.length === 1) return { name: 'details', id };
  if (parts.length === 2 && sub === 'edit') return { name: 'edit', id };
  if (parts.length === 2 && SUMMARY_ACTIONS.includes(sub)) {
    return {
      name: 'summary',
      id,
      action: sub,
      summary: state?.summary ?? null,
      previous: state?.previous ?? null,
    };
  }

  return { name: 'notFound' };
}

export function useRoute() {
  const location = useLocation();

  return useMemo(
    () => matchRoute(location.pathname, location.state),
    [location.pathname, location.state, location.key],
  );
}
