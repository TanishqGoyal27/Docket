import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { api } from '../../api.js';
import { OFFICER_ROLES, ROLE_LABELS } from '../../labels.js';
import { Alert, ErrorBox, Loading, PageHeader, useApi } from '../../components/ui.jsx';

// Configure one service: settings, approval sequence and required documents (UC-09.3 to UC-09.6)
export default function ServiceDetail() {
  const { id } = useParams();
  const location = useLocation();
  const services = useApi('/admin/services');
  const [message, setMessage] = useState(location.state?.message || null);
  const [error, setError] = useState(null);
  const service = services.data?.find((s) => String(s.id) === id);

  const run = async (fn, text) => {
    setError(null);
    try {
      await fn();
      setMessage(text);
      services.reload();
    } catch (err) {
      setError(err);
    }
  };

  return (
    <Loading state={services}>
      {service && (
        <>
          <PageHeader title={service.name}
                      subtitle={`Outcome: ${service.finalStatus === 'VERIFIED' ? 'Verified' : 'Approved'}${service.requiresVisit ? ' · in-person visit after approval' : ''}${service.updatesStudentRecord ? ' · updates the student record' : ''}`}
                      actions={<Link to="/admin/services" className="btn btn-ghost">All services</Link>} />
          <Alert kind="success">{message}</Alert>
          <ErrorBox error={error} />
          <Settings service={service} run={run} />
          <Stages service={service} run={run} />
          <Documents service={service} run={run} />
          <p className="muted small">Changes apply to new applications and to the next step of applications in progress.
            Stages are never deleted so that applications already in a stage are not lost. All changes are recorded in the change log.</p>
        </>
      )}
    </Loading>
  );
}

function Settings({ service, run }) {
  const [description, setDescription] = useState(service.description || '');
  const [fee, setFee] = useState(String(service.feeAmount));
  useEffect(() => setDescription(service.description || ''), [service.description]);
  useEffect(() => setFee(String(service.feeAmount)), [service.feeAmount]);
  const save = (active) => run(() => api(`/admin/services/${service.id}`,
    { method: 'PUT', body: { description, active, feeAmount: Number(fee) || 0 } }),
  active === service.active ? 'Settings saved.' : active ? 'Service activated.' : 'Service deactivated.');
  return (
    <section className="card section-card">
      <div className="row-between">
        <h2>Settings</h2>
        <span className={`badge ${service.active ? 'status-approved' : 'status-closed'}`}>{service.active ? 'Active' : 'Inactive'}</span>
      </div>
      <label className="field"><span>Description shown to students</span>
        <textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} /></label>
      <label className="field fee-field"><span>Application fee in ₹ (0 = free; paid online before submission)</span>
        <input type="number" min={0} max={100000} value={fee} onChange={(e) => setFee(e.target.value)} /></label>
      <div className="form-actions start">
        <button className="btn" onClick={() => save(service.active)}>Save settings</button>
        <button className={`btn ${service.active ? 'btn-danger' : 'btn-primary'}`} onClick={() => save(!service.active)}
                disabled={!service.active && service.stages.length === 0}
                title={!service.active && service.stages.length === 0 ? 'Add at least one stage first' : undefined}>
          {service.active ? 'Deactivate service' : 'Activate service'}
        </button>
      </div>
    </section>
  );
}

const NEW_STAGE = { name: '', handlerRole: 'ADMIN_OFFICER', department: '', optional: false };

function Stages({ service, run }) {
  const [editId, setEditId] = useState(null);
  const [form, setForm] = useState(NEW_STAGE);
  const [adding, setAdding] = useState(NEW_STAGE);

  return (
    <section className="card section-card">
      <h2>Approval sequence</h2>
      <div className="table-wrap">
        <table>
          <thead><tr><th>Step</th><th>Stage</th><th>Handled by</th><th>Department</th><th>Optional</th><th /></tr></thead>
          <tbody>
            {service.stages.map((st) => editId === st.id ? (
              <tr key={st.id}>
                <td>{st.stepOrder}</td>
                <td><input aria-label="Stage name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></td>
                <td><RoleSelect value={form.handlerRole} onChange={(r) => setForm({ ...form, handlerRole: r })} /></td>
                <td><input aria-label="Department" value={form.department} onChange={(e) => setForm({ ...form, department: e.target.value })} placeholder="Any" /></td>
                <td><input type="checkbox" aria-label="Optional" checked={form.optional} onChange={(e) => setForm({ ...form, optional: e.target.checked })} /></td>
                <td className="right nowrap">
                  <button className="btn btn-small btn-primary" onClick={() => run(
                    () => api(`/admin/stages/${st.id}`, { method: 'PUT', body: form }), 'Stage updated.').then(() => setEditId(null))}>Save</button>{' '}
                  <button className="btn btn-small btn-ghost" onClick={() => setEditId(null)}>Cancel</button>
                </td>
              </tr>
            ) : (
              <tr key={st.id}>
                <td>{st.stepOrder}</td>
                <td>{st.name}</td>
                <td>{ROLE_LABELS[st.handlerRole]}</td>
                <td>{st.department || <span className="muted">Any</span>}</td>
                <td>{st.optional ? 'Only if forwarded' : '—'}</td>
                <td className="right">
                  <button className="btn btn-small btn-ghost" onClick={() => {
                    setEditId(st.id);
                    setForm({ name: st.name, handlerRole: st.handlerRole, department: st.department || '', optional: st.optional });
                  }}>Edit</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <form className="add-row" onSubmit={(e) => {
        e.preventDefault();
        run(() => api(`/admin/services/${service.id}/stages`, { method: 'POST', body: adding }), 'Stage added at the end.')
          .then(() => setAdding(NEW_STAGE));
      }}>
        <input required placeholder="New stage name" aria-label="New stage name" value={adding.name}
               onChange={(e) => setAdding({ ...adding, name: e.target.value })} />
        <RoleSelect value={adding.handlerRole} onChange={(r) => setAdding({ ...adding, handlerRole: r })} />
        <input placeholder="Department (optional)" aria-label="Department" value={adding.department}
               onChange={(e) => setAdding({ ...adding, department: e.target.value })} />
        <label className="check"><input type="checkbox" checked={adding.optional}
                                        onChange={(e) => setAdding({ ...adding, optional: e.target.checked })} /><span>Optional</span></label>
        <button className="btn btn-primary">Add stage</button>
      </form>
    </section>
  );
}

function RoleSelect({ value, onChange }) {
  return (
    <select aria-label="Handled by" value={value} onChange={(e) => onChange(e.target.value)}>
      {OFFICER_ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
    </select>
  );
}

function Documents({ service, run }) {
  const [name, setName] = useState('');
  const [mandatory, setMandatory] = useState(true);
  return (
    <section className="card section-card">
      <h2>Required documents</h2>
      {service.documents.length === 0 && <p className="muted">No documents configured.</p>}
      <ul className="doc-list">
        {service.documents.map((d) => (
          <li key={d.id}>
            <div><strong>{d.documentName}</strong> <span className={`badge ${d.mandatory ? 'status-submitted' : 'status-closed'}`}>{d.mandatory ? 'Mandatory' : 'Optional'}</span></div>
            <button className="btn btn-small btn-ghost" onClick={() => run(
              () => api(`/admin/documents/${d.id}/toggle`, { method: 'POST' }), `${d.documentName} updated.`)}>
              Make {d.mandatory ? 'optional' : 'mandatory'}
            </button>
          </li>
        ))}
      </ul>
      <form className="add-row" onSubmit={(e) => {
        e.preventDefault();
        run(() => api(`/admin/services/${service.id}/documents`, { method: 'POST', body: { documentName: name, mandatory } }),
          'Document added.').then(() => setName(''));
      }}>
        <input required placeholder="Document name" aria-label="Document name" value={name} onChange={(e) => setName(e.target.value)} />
        <label className="check"><input type="checkbox" checked={mandatory} onChange={(e) => setMandatory(e.target.checked)} /><span>Mandatory</span></label>
        <button className="btn btn-primary">Add document</button>
      </form>
    </section>
  );
}
