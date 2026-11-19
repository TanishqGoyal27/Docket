import { useCallback, useEffect, useState } from 'react';
import { api } from '../api.js';
import { STATUS_LABELS } from '../labels.js';

// Loads data from the API and exposes { data, error, loading, reload }
export function useApi(path) {
  const [state, setState] = useState({ data: null, error: null, loading: true });
  const load = useCallback(() => {
    setState((s) => ({ ...s, loading: true }));
    api(path)
      .then((data) => setState({ data, error: null, loading: false }))
      .catch((error) => setState({ data: null, error, loading: false }));
  }, [path]);
  useEffect(load, [load]);
  return { ...state, reload: load };
}

export function StatusBadge({ status }) {
  return <span className={`badge status-${status?.toLowerCase()}`}>{STATUS_LABELS[status] || status}</span>;
}

export function Alert({ kind = 'error', children }) {
  if (!children) return null;
  return <div className={`alert alert-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>{children}</div>;
}

// Shows an API error, including the list of validation problems
export function ErrorBox({ error }) {
  if (!error) return null;
  return (
    <Alert>
      <strong>{error.message}</strong>
      {error.errors?.length > 0 && (
        <ul>{error.errors.map((e) => <li key={e}>{e}</li>)}</ul>
      )}
    </Alert>
  );
}

export function Loading({ state, children }) {
  if (state.loading && !state.data) return <div className="muted pad">Loading…</div>;
  if (state.error) return <ErrorBox error={state.error} />;
  return children;
}

export function Empty({ children }) {
  return <div className="empty">{children}</div>;
}

// Header card like the LMS "TIET-LMS: Dashboard" box
export function PageHeader({ title, subtitle, actions, kicker }) {
  return (
    <div className="page-header">
      <div>
        {kicker && <span className="kicker">{kicker}</span>}
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </div>
  );
}
