import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../../api.js';
import { ROLE_LABELS } from '../../labels.js';
import { ErrorBox, Loading, PageHeader, useApi } from '../../components/ui.jsx';

// All services and their workflows. New services are added by configuration only (QR18).
export default function Services() {
  const services = useApi('/admin/services');
  const [creating, setCreating] = useState(false);

  return (
    <>
      <PageHeader title="Services & workflows"
                  subtitle="Each service has its own approval stages and required documents."
                  actions={<button className="btn btn-primary" onClick={() => setCreating(true)}>New service</button>} />
      {creating && <NewService onCancel={() => setCreating(false)} />}
      <Loading state={services}>
        <div className="service-grid">
          {(services.data || []).map((s) => (
            <Link key={s.id} to={`/admin/services/${s.id}`} className="card service-card">
              <div className="row-between">
                <h3>{s.name}</h3>
                <span className={`badge ${s.active ? 'status-approved' : 'status-closed'}`}>{s.active ? 'Active' : 'Inactive'}</span>
              </div>
              <p className="muted">{s.description}</p>
              <ol className="mini-route">
                {s.stages.map((st) => <li key={st.id}>{ROLE_LABELS[st.handlerRole]}{st.optional ? ' (if required)' : ''}</li>)}
              </ol>
              <span className="service-meta">Outcome: {s.finalStatus === 'VERIFIED' ? 'Verified' : 'Approved'} · {s.documents.length} documents · {s.feeAmount > 0 ? `Fee ₹ ${s.feeAmount}` : 'No fee'}</span>
            </Link>
          ))}
        </div>
      </Loading>
    </>
  );
}

function NewService({ onCancel }) {
  const navigate = useNavigate();
  const [v, setV] = useState({ name: '', description: '', finalStatus: 'APPROVED', requiresVisit: false });
  const [error, setError] = useState(null);

  const save = async (e) => {
    e.preventDefault();
    try {
      const created = await api('/admin/services', { method: 'POST', body: v });
      navigate(`/admin/services/${created.id}`, { state: { message: 'Service created. Add its stages and documents, then activate it.' } });
    } catch (err) {
      setError(err);
    }
  };

  return (
    <form className="card form inline-form" onSubmit={save}>
      <h2>New service</h2>
      <p className="muted small">The service starts inactive. Students see it once you add stages and switch it on.</p>
      <ErrorBox error={error} />
      <label className="field"><span>Name<span className="req">*</span></span>
        <input required value={v.name} onChange={(e) => setV({ ...v, name: e.target.value })} placeholder="e.g. Hostel Clearance" /></label>
      <label className="field"><span>Description shown to students</span>
        <textarea rows={2} value={v.description} onChange={(e) => setV({ ...v, description: e.target.value })} /></label>
      <div className="row">
        <label className="field"><span>Final outcome</span>
          <select value={v.finalStatus} onChange={(e) => setV({ ...v, finalStatus: e.target.value })}>
            <option value="APPROVED">Approved</option>
            <option value="VERIFIED">Verified (verification only)</option>
          </select>
        </label>
        <label className="check"><input type="checkbox" checked={v.requiresVisit}
                                        onChange={(e) => setV({ ...v, requiresVisit: e.target.checked })} />
          <span>Approved students book an in-person visit</span></label>
      </div>
      <div className="form-actions">
        <button type="button" className="btn btn-ghost" onClick={onCancel}>Cancel</button>
        <button className="btn btn-primary">Create service</button>
      </div>
    </form>
  );
}
