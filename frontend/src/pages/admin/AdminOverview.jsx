import { Link } from 'react-router-dom';
import { STATUS_LABELS } from '../../labels.js';
import { Loading, PageHeader, useApi } from '../../components/ui.jsx';

export default function AdminOverview() {
  const stats = useApi('/admin/stats');
  const s = stats.data;
  return (
    <>
      <PageHeader title="Administration" subtitle="Users, workflows and the change log." />
      <Loading state={stats}>
        {s && (
          <>
            <div className="stats">
              <Link to="/admin/users" className="stat card"><span className="stat-num">{s.users}</span><span className="muted">users</span></Link>
              <Link to="/admin/services" className="stat card"><span className="stat-num">{s.services}</span><span className="muted">services</span></Link>
              <Link to="/admin/services" className="stat card"><span className="stat-num">{s.stages}</span><span className="muted">approval stages</span></Link>
              <Link to="/admin/applications" className="stat card"><span className="stat-num">{s.applications}</span><span className="muted">applications</span></Link>
            </div>
            <section className="section">
              <h2>Applications by status</h2>
              <div className="card status-bars">
                {Object.entries(s.byStatus).map(([status, count]) => (
                  <div key={status} className="status-bar">
                    <span>{STATUS_LABELS[status]}</span>
                    <div className="bar-track">
                      <div className={`bar-fill status-${status.toLowerCase()}`}
                           style={{ width: `${s.applications ? (count / s.applications) * 100 : 0}%` }} />
                    </div>
                    <span className="bar-num">{count}</span>
                  </div>
                ))}
              </div>
            </section>
          </>
        )}
      </Loading>
    </>
  );
}
