import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api.js';
import { fieldLabel, formatDate } from '../labels.js';
import { Wordmark } from '../components/Chrome.jsx';
import { Alert, ErrorBox, Loading, useApi } from '../components/ui.jsx';

// Opened from the email sent to the supervising teacher. No login: the link itself is the key.
export default function TeacherApproval() {
  const { token } = useParams();
  const state = useApi(`/public/supervisor/${token}`);
  const [remark, setRemark] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const v = state.data;

  const decide = async (approve) => {
    if (!approve && !remark.trim()) {
      setError({ message: 'Please give a reason for rejecting.' });
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api(`/public/supervisor/${token}`, { method: 'POST', body: { approve, remark } });
      state.reload();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="teacher-page">
      <header className="teacher-head">
        <Wordmark />
        <span className="muted small">Supervisor approval · TIET Patiala</span>
      </header>
      <main id="main" className="teacher-main">
        <Loading state={state}>
          {v && (
            <>
              <h1>{v.serviceName} {v.referenceNumber}</h1>
              <p className="muted">Dear {v.teacherName}, {v.studentName} has named you as the supervising teacher for
                this claim. Please check it and approve or reject.</p>

              {v.status !== 'PENDING' && (
                <Alert kind={v.status === 'APPROVED' ? 'success' : 'warning'}>
                  You {v.status === 'APPROVED' ? 'approved' : 'rejected'} this request on {formatDate(v.decidedAt)}.
                  {v.status === 'APPROVED' ? ' It has been sent to the Head of Department.' : ''} Thank you.
                  {v.remark && <><br />Your remark: {v.remark}</>}
                </Alert>
              )}

              <div className="card">
                <h3>Student</h3>
                <dl className="facts">
                  <div><dt>Name</dt><dd>{v.studentName}</dd></div>
                  <div><dt>Roll number</dt><dd>{v.rollNumber || '—'}</dd></div>
                  <div><dt>Course</dt><dd>{v.course || '—'}</dd></div>
                </dl>
                <h3>Claim details</h3>
                <dl className="facts">
                  {Object.entries(v.formData).filter(([k]) => !k.startsWith('teacher')).map(([k, val]) => (
                    <div key={k}><dt>{fieldLabel(v.serviceName, k)}</dt>
                      <dd>{k === 'amount' ? `₹ ${Number(val).toLocaleString('en-IN')}` : val}</dd></div>
                  ))}
                </dl>
                <h3>Documents</h3>
                <ul className="doc-list">
                  {v.documents.map((doc) => (
                    <li key={doc.id}>
                      <div><strong>{doc.documentName}</strong><span className="muted small">{doc.fileName}</span></div>
                      <a className="btn btn-small" href={`/api/public/supervisor/${token}/documents/${doc.id}`}
                         target="_blank" rel="noreferrer">Open</a>
                    </li>
                  ))}
                </ul>
              </div>

              {v.status === 'PENDING' && (
                <div className="card action-card">
                  <h3>Your decision</h3>
                  <ErrorBox error={error} />
                  <label className="field"><span>Remark (required if you reject)</span>
                    <textarea rows={3} value={remark} onChange={(e) => setRemark(e.target.value)}
                              placeholder="e.g. Expenses are genuine and related to the project" /></label>
                  <div className="form-actions start">
                    <button className="btn btn-primary" disabled={busy} onClick={() => decide(true)}>Approve claim</button>
                    <button className="btn btn-danger" disabled={busy} onClick={() => decide(false)}>Reject</button>
                  </div>
                  <p className="muted small">If you do not know this student or this request, please reject it.</p>
                </div>
              )}
            </>
          )}
        </Loading>
      </main>
    </div>
  );
}
