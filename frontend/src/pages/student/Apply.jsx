import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { ROLE_LABELS, fieldsFor, rupees } from '../../labels.js';
import FileInput from '../../components/FileInput.jsx';
import PaymentCheckout from '../../components/PaymentCheckout.jsx';
import { Alert, ErrorBox, Loading, PageHeader, useApi } from '../../components/ui.jsx';

// One form for every service (FR5-FR9): questions + the configured document checklist (FR10, FR32)
export default function Apply() {
  const { serviceId } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const services = useApi('/student/services');
  const [values, setValues] = useState({});
  const [files, setFiles] = useState({});
  const [fileError, setFileError] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [draft, setDraft] = useState(null);

  const service = services.data?.find((s) => String(s.id) === serviceId);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const form = new FormData();
    form.append('serviceId', service.id);
    form.append('fields', JSON.stringify(values));
    Object.entries(files).forEach(([docId, file]) => file && form.append(`doc_${docId}`, file));
    try {
      const created = await api('/student/applications', { method: 'POST', form });
      if (created.status === 'PAYMENT_PENDING') {
        setDraft(created);   // documents saved; the application is submitted once the fee is paid
      } else {
        navigate(`/student/applications/${created.id}`,
          { state: { message: `Application ${created.referenceNumber} submitted.` } });
      }
    } catch (err) {
      setError(err);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Loading state={services}>
      {!service ? (
        <Alert>This service is not available. <Link to="/student">Back to my applications</Link></Alert>
      ) : (
        <>
          <PageHeader title={service.name} subtitle={service.description}
                      actions={<Link to="/student" className="btn btn-ghost">Cancel</Link>} />

          <div className="route card">
            <span className="muted small">Approval route</span>
            <ol className="route-steps">
              {service.stages.map((st) => (
                <li key={st.id} className={st.optional ? 'optional' : ''}>
                  <strong>{st.name}</strong>
                  <span className="muted small">{ROLE_LABELS[st.handlerRole]}{st.optional ? ' · only if required' : ''}</span>
                </li>
              ))}
            </ol>
          </div>

          <ErrorBox error={error} />
          <form className="card form" onSubmit={submit}>
            <h2>Your details</h2>
            <dl className="facts">
              <div><dt>Name</dt><dd>{user.name}</dd></div>
              <div><dt>Roll number</dt><dd>{user.rollNumber || '—'}</dd></div>
              <div><dt>Course</dt><dd>{user.course || '—'}</dd></div>
              <div><dt>Phone</dt><dd>{user.phone || '—'}</dd></div>
            </dl>

            {fieldsFor(service.name).map((f) => (
              <Field key={f.key} field={f} required={service.requiredFields.includes(f.key)}
                     value={values[f.key] || ''} onChange={(v) => setValues({ ...values, [f.key]: v })}
                     hint={service.name === 'Details Correction' && f.key === 'newValue' && values.field
                       ? `Current value: ${user[values.field] || '—'}` : f.hint} />
            ))}

            <h2>Documents</h2>
            <p className="muted small">PDF, JPEG or PNG, up to 10 MB each.</p>
            <Alert>{fileError}</Alert>
            {service.documents.length === 0 && <p className="muted">No documents are needed for this service.</p>}
            {service.documents.map((d) => (
              <FileInput key={d.id} doc={values.requestType === 'Replacement (lost)' && d.documentName.startsWith('FIR')
                ? { ...d, mandatory: true } : d} file={files[d.id]}
                         onChange={(file, err) => { setFileError(err); setFiles({ ...files, [d.id]: file }); }} />
            ))}

            {service.feeAmount > 0 && (
              <>
                <h2>Application fee</h2>
                <div className="fee-box">
                  <span>Fee for {service.name}</span>
                  <strong>{rupees(service.feeAmount)}</strong>
                </div>
                <p className="muted small">Pay online in the next step. A fee receipt is generated automatically and
                  attached to your application, which is submitted only after the payment succeeds.</p>
              </>
            )}

            <div className="form-actions">
              <button className="btn btn-primary" disabled={busy}>
                {busy ? 'Saving…' : service.feeAmount > 0 ? `Save and pay ${rupees(service.feeAmount)}` : 'Submit application'}
              </button>
            </div>
          </form>

          {draft && (
            <PaymentCheckout applicationId={draft.id}
                             onPaid={(s) => navigate(`/student/applications/${s.id}`,
                               { state: { message: `Payment successful. Application ${s.referenceNumber} submitted; your fee receipt is attached.` } })}
                             onClose={() => navigate(`/student/applications/${draft.id}`,
                               { state: { warning: `Application ${draft.referenceNumber} is saved but NOT submitted. Pay the fee to submit it.` } })} />
          )}
        </>
      )}
    </Loading>
  );
}

export function Field({ field, required, value, onChange, hint }) {
  const common = { value, required, onChange: (e) => onChange(e.target.value) };
  return (
    <label className="field">
      <span>{field.label}{required && <span className="req" aria-label="required">*</span>}</span>
      {field.type === 'textarea' ? <textarea rows={3} {...common} />
        : field.type === 'select' ? (
          <select {...common}>
            <option value="">Choose…</option>
            {field.options.map((o) => {
              const [val, text] = Array.isArray(o) ? o : [o, o];
              return <option key={val} value={val}>{text}</option>;
            })}
          </select>
        ) : <input type={field.type || 'text'} min={field.type === 'number' ? 1 : undefined} {...common} />}
      {hint && <span className="muted small">{hint}</span>}
    </label>
  );
}
