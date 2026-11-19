import { useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { DECISION_LABELS, ROLE_LABELS, fieldLabel, formatDate, homePath, roleLabel, rupees } from '../labels.js';
import FileInput from '../components/FileInput.jsx';
import PaymentCheckout from '../components/PaymentCheckout.jsx';
import { Alert, ErrorBox, Loading, PageHeader, StatusBadge, useApi } from '../components/ui.jsx';

// Detail page for students, officers and the Administrator (FR15, FR18, FR23).
// What each viewer can do comes from the server in detail.actions.
export default function ApplicationPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const location = useLocation();
  const state = useApi(`/applications/${id}`);
  const d = state.data;
  const back = user.role === 'ADMINISTRATOR' ? '/admin/applications' : homePath(user.role);

  return (
    <Loading state={state}>
      {d && (
        <>
          <PageHeader title={d.summary.referenceNumber}
                      subtitle={[d.summary.serviceName, user.role !== 'STUDENT' && d.summary.studentName,
                        `${d.summary.status === 'PAYMENT_PENDING' ? 'saved' : 'submitted'} ${formatDate(d.summary.submittedAt)}`].filter(Boolean).join(' · ')}
                      actions={<Link to={back} className="btn btn-ghost">Back</Link>} />
          <Alert kind="success">{location.state?.message}</Alert>
          <Alert kind="warning">{location.state?.warning}</Alert>

          <div className="detail-grid">
            <div className="detail-main">
              <StatusPanel d={d} />
              {d.actions.canResubmit && <ResubmitPanel d={d} onDone={state.reload} />}
              {d.actions.canBookVisit && <BookingPanel d={d} onDone={state.reload} />}
              {d.actions.canPay && <PayPanel d={d} onDone={state.reload} />}
              {d.actions.canResendSupervisor && <WaitingForTeacher d={d} onDone={state.reload} />}
              {d.actions.canVerifyFee && <FeeCheckPanel d={d} onDone={state.reload} />}
              {d.actions.canAct && <DecisionPanel d={d} />}
              <Answers d={d} />
              <Documents d={d} />
              <Timeline d={d} />
            </div>
            <aside className="detail-side">
              <Progress d={d} />
              {d.supervisor && <SupervisorCard s={d.supervisor} />}
              {d.feeAmount > 0 && <FeeCard d={d} />}
              {d.payment && (
                <div className="card">
                  <h3>Payment record</h3>
                  <dl className="facts single">
                    <div><dt>Reference</dt><dd>{d.payment.reference}</dd></div>
                    <div><dt>Date</dt><dd>{formatDate(d.payment.date, false)}</dd></div>
                    <div><dt>Recorded by</dt><dd>{d.payment.recordedBy}</dd></div>
                  </dl>
                  <p className="muted small">Payments are made outside Docket; this is the record only.</p>
                </div>
              )}
              {d.appointment && (
                <div className="card">
                  <h3>In-person visit</h3>
                  <p className="big">{formatDate(d.appointment.slot)}</p>
                  <p className="muted small">Bring your original documents to the Administration office.</p>
                </div>
              )}
            </aside>
          </div>
        </>
      )}
    </Loading>
  );
}

function StatusPanel({ d }) {
  const s = d.summary;
  return (
    <div className="card status-panel">
      <div className="status-line">
        <StatusBadge status={s.status} />
        {s.currentStage
          ? <span>Now at <strong>{s.currentStage}</strong> · {ROLE_LABELS[s.currentRole]}{s.department ? `, ${s.department}` : ''}</span>
          : s.status === 'PAYMENT_PENDING'
            ? <span>Waiting for the fee payment. <strong>Not submitted yet.</strong></span>
            : s.status === 'SUPERVISOR_PENDING'
            ? <span>Waiting for <strong>{d.supervisor?.teacherName}</strong> to approve by email. It goes to the HOD after that.</span>
            : <span className="muted">This application is finished.</span>}
      </div>
      {s.status === 'AWAITING_CORRECTION' && d.pendingMessage && (
        <Alert kind="warning"><strong>Action needed:</strong> {d.pendingMessage}</Alert>
      )}
      {s.status === 'VERIFIED' && (
        <p className="muted small">Verified means the documents were accepted. It is not an approval or a payment.</p>
      )}
      {s.status === 'REJECTED' && (
        <p className="muted small">See the timeline below for the reason.</p>
      )}
    </div>
  );
}

function Progress({ d }) {
  const s = d.summary;
  const current = d.stages.find((st) => st.name === s.currentStage && st.handlerRole === s.currentRole);
  const finished = !s.currentStage;
  const decidedStages = new Set(d.history.filter((h) => h.actorRole !== 'STUDENT').map((h) => h.stageName));
  return (
    <div className="card">
      <h3>Approval route</h3>
      <ol className="progress">
        {d.stages.map((st) => {
          let cls = 'todo';
          if (current && st.id === current.id) cls = 'current';
          else if (current ? st.stepOrder < current.stepOrder : finished && decidedStages.has(st.name)) cls = 'done';
          else if (finished || (current && st.stepOrder < current.stepOrder)) cls = 'skipped';
          return (
            <li key={st.id} className={cls}>
              <strong>{st.name}</strong>
              <span className="muted small">{ROLE_LABELS[st.handlerRole]}{st.optional ? ' · if required' : ''}</span>
            </li>
          );
        })}
      </ol>
    </div>
  );
}

function Answers({ d }) {
  const entries = Object.entries(d.formData);
  if (entries.length === 0) return null;
  return (
    <div className="card">
      <h3>Application details</h3>
      <dl className="facts">
        {entries.map(([k, v]) => (
          <div key={k}><dt>{fieldLabel(d.summary.serviceName, k)}</dt><dd>{k === 'amount' || k === 'familyIncome' ? `₹ ${Number(v).toLocaleString('en-IN')}` : v}</dd></div>
        ))}
      </dl>
    </div>
  );
}

function Documents({ d }) {
  return (
    <div className="card">
      <h3>Documents</h3>
      {d.documents.length === 0 ? (
        <p className="muted">No documents to show. Files are visible only to the student and the officer
          currently handling the application.</p>
      ) : (
        <ul className="doc-list">
          {d.documents.map((doc) => (
            <li key={doc.id}>
              <div>
                <strong>{doc.documentName}</strong>
                <span className="muted small">{doc.fileName} · {(doc.fileSize / 1024).toFixed(0)} KB · {formatDate(doc.uploadedAt)}</span>
              </div>
              <a className="btn btn-small" href={`/api/documents/${doc.id}`} target="_blank" rel="noreferrer">Open</a>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Timeline({ d }) {
  return (
    <div className="card">
      <h3>Decision history</h3>
      <ol className="timeline">
        {d.history.map((h) => (
          <li key={h.id} className={`tl-${h.decision.toLowerCase()}`}>
            <div className="tl-head">
              <strong>{DECISION_LABELS[h.decision] || h.decision}</strong>
              <span className="muted small">{formatDate(h.at)}</span>
            </div>
            <div className="muted small">
              {h.actorName} · {roleLabel(h.actorRole)}{h.stageName ? ` · ${h.stageName}` : ''}
            </div>
            {h.reason && <p className="tl-reason">{h.reason}</p>}
          </li>
        ))}
      </ol>
    </div>
  );
}

// Student: fix what was asked and send it back (FR11, FR12)
function ResubmitPanel({ d, onDone }) {
  const [note, setNote] = useState('');
  const [files, setFiles] = useState({});
  const [fileError, setFileError] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const feeIssue = d.history.at(-1)?.decision === 'ISSUE_FOUND';

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const form = new FormData();
    if (note) form.append('note', note);
    Object.entries(files).forEach(([docId, f]) => f && form.append(`doc_${docId}`, f));
    try {
      await api(`/student/applications/${d.summary.id}/resubmit`, { method: 'POST', form });
      onDone();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="card action-card" onSubmit={submit}>
      <h3>Correct and resubmit</h3>
      <p className="muted small">Upload replacement files where needed and explain what you changed.
        Review continues at the same stage.</p>
      <ErrorBox error={error} />
      <label className="field">
        <span>{feeIssue ? 'Fee payment reference and what you changed' : 'What did you change?'}</span>
        <textarea rows={3} value={note} onChange={(e) => setNote(e.target.value)}
                  placeholder={feeIssue ? 'e.g. Paid on 20 Nov, receipt no. 45821' : 'e.g. Uploaded a clearer photo'} />
      </label>
      <Alert>{fileError}</Alert>
      {d.requiredDocuments.map((doc) => (
        <FileInput key={doc.id} doc={doc} file={files[doc.id]} optionalHint
                   onChange={(f, err) => { setFileError(err); setFiles({ ...files, [doc.id]: f }); }} />
      ))}
      <div className="form-actions">
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Sending…' : 'Resubmit application'}</button>
      </div>
    </form>
  );
}

// Student: book a slot to collect the ID card (FR13)
function BookingPanel({ d, onDone }) {
  const slots = useApi('/student/slots');
  const [slot, setSlot] = useState('');
  const [error, setError] = useState(null);

  const byDay = {};
  (slots.data || []).forEach((s) => {
    const day = s.slice(0, 10);
    (byDay[day] = byDay[day] || []).push(s);
  });

  const book = async (e) => {
    e.preventDefault();
    setError(null);
    try {
      await api(`/student/applications/${d.summary.id}/appointment`, { method: 'POST', body: { slot } });
      onDone();
    } catch (err) {
      setError(err);
    }
  };

  return (
    <form className="card action-card" onSubmit={book}>
      <h3>Book your visit</h3>
      <p className="muted small">Your application is approved. Choose a time to collect it at the Administration office.</p>
      <ErrorBox error={error} />
      <Loading state={slots}>
        <div className="slot-days">
          {Object.entries(byDay).map(([day, list]) => (
            <div key={day} className="slot-day">
              <span className="slot-date">{formatDate(day, false)}</span>
              <div className="slot-list">
                {list.map((s) => (
                  <button type="button" key={s} className={`slot ${slot === s ? 'chosen' : ''}`}
                          aria-pressed={slot === s} onClick={() => setSlot(s)}>
                    {s.slice(11, 16)}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      </Loading>
      <div className="form-actions">
        <button className="btn btn-primary" disabled={!slot}>Confirm visit</button>
      </div>
    </form>
  );
}

// Officer: approve / reject / return, or record the payment at the Accounts stage (FR19-FR27)
function DecisionPanel({ d }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [reason, setReason] = useState('');
  const [forward, setForward] = useState(true);
  const [reference, setReference] = useState('');
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10));
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const a = d.actions;
  const finance = user.role === 'FINANCE_OFFICER';
  const verifying = finance || ['Document Verification', 'Scholarship Verification'].includes(d.summary.serviceName);

  const act = async (action, body) => {
    setBusy(true);
    setError(null);
    try {
      const result = await api(`/staff/applications/${d.summary.id}/${action}`, { method: 'POST', body });
      navigate('/staff', { state: { message: `${result.referenceNumber}: ${messageFor(action, result)}` } });
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  };

  const needReason = (action) => {
    if (!reason.trim()) {
      setError({ message: action === 'reject'
        ? 'A reason is required to reject an application.'
        : 'Describe what the student must correct.' });
      return;
    }
    act(action, { reason });
  };

  if (a.paymentStage) {
    return (
      <form className="card action-card" onSubmit={(e) => { e.preventDefault(); act('payment', { reference, date }); }}>
        <h3>Record reimbursement payment</h3>
        <p className="muted small">Enter the reference of the payment made outside Docket. The claim will be marked processed.</p>
        <ErrorBox error={error} />
        <div className="row">
          <label className="field"><span>Payment reference (UTR / cheque no.)<span className="req">*</span></span>
            <input required value={reference} onChange={(e) => setReference(e.target.value)} /></label>
          <label className="field"><span>Payment date<span className="req">*</span></span>
            <input type="date" required value={date} onChange={(e) => setDate(e.target.value)} /></label>
        </div>
        <div className="form-actions">
          <button className="btn btn-primary" disabled={busy}>Record payment and mark processed</button>
        </div>
      </form>
    );
  }

  const waitingForFee = !a.nextStageName && d.feeAmount > 0 && d.fee?.verification !== 'CONFIRMED';

  const approveLabel = a.nextStageName && (!a.nextStageOptional || forward)
    ? `${verifying ? 'Verify' : 'Approve'} and send to ${a.nextStageName}`
    : verifying ? 'Mark as verified' : 'Approve (final decision)';

  return (
    <div className="card action-card">
      <h3>Your decision</h3>
      <ErrorBox error={error} />
      {waitingForFee && (
        <Alert kind="warning">The Finance Officer is still verifying the fee payment. You can check the documents
          now; the final approval unlocks once Finance confirms the payment.</Alert>
      )}
      {a.nextStageOptional && (
        <label className="check">
          <input type="checkbox" checked={forward} onChange={(e) => setForward(e.target.checked)} />
          <span>Forward to <strong>{a.nextStageName}</strong> (only when required)</span>
        </label>
      )}
      <div className="form-actions start">
        <button className="btn btn-primary" disabled={busy || waitingForFee} onClick={() => act('approve', { forward })}>{approveLabel}</button>
      </div>
      <label className="field">
        <span>Reason or what needs correcting</span>
        <textarea rows={3} value={reason} onChange={(e) => setReason(e.target.value)}
                  placeholder={finance ? 'e.g. Fee receipt not found in records' : 'e.g. Photo is not clear'} />
      </label>
      <div className="form-actions start">
        <button className="btn" disabled={busy} onClick={() => needReason('correction')}>
          {finance ? 'Issue found: return to student' : 'Return to student for correction'}
        </button>
        <button className="btn btn-danger" disabled={busy} onClick={() => needReason('reject')}>Reject</button>
      </div>
    </div>
  );
}

function messageFor(action, result) {
  if (action === 'reject') return 'rejected.';
  if (action === 'correction') return 'returned to the student.';
  if (action === 'payment') return 'payment recorded, claim processed.';
  return result.currentStage ? `moved to ${result.currentStage}.` : `completed (${result.status.toLowerCase()}).`;
}

const VERIFICATION = {
  PENDING: ['Awaiting Finance verification', 'status-awaiting_correction'],
  CONFIRMED: ['Confirmed by Finance', 'status-approved'],
  NOT_FOUND: ['Not found by Finance', 'status-rejected'],
};

// Application fee summary (everyone who can see the application; the receipt file itself is restricted)
function FeeCard({ d }) {
  const f = d.fee;
  const [label, cls] = f ? VERIFICATION[f.verification] : ['Not paid', 'status-submitted'];
  return (
    <div className="card">
      <h3>Application fee</h3>
      <p className="big">{rupees(d.feeAmount)}</p>
      <span className={`badge ${cls}`}>{label}</span>
      {f && (
        <dl className="facts single fee-facts">
          <div><dt>Receipt no.</dt><dd>{f.receiptNumber}</dd></div>
          <div><dt>Payment ID</dt><dd><code>{f.paymentId}</code></dd></div>
          <div><dt>Paid on</dt><dd>{formatDate(f.paidAt)}</dd></div>
          <div><dt>Gateway</dt><dd>{f.gateway === 'SANDBOX' ? 'Docket Sandbox (test)' : 'Razorpay'}</dd></div>
          {f.verifiedBy && <div><dt>Checked by</dt><dd>{f.verifiedBy}, {formatDate(f.verifiedAt)}</dd></div>}
          {f.remark && <div><dt>Finance remark</dt><dd>{f.remark}</dd></div>}
        </dl>
      )}
    </div>
  );
}

// Student: application saved but unpaid
function PayPanel({ d, onDone }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [paying, setPaying] = useState(false);
  return (
    <div className="card action-card">
      <h3>Pay the application fee</h3>
      <p>Your form and documents are saved. The application is <strong>not submitted</strong> until you pay
        {' '}{rupees(d.feeAmount)}.</p>
      <div className="form-actions start">
        <button className="btn btn-primary" onClick={() => setPaying(true)}>Pay {rupees(d.feeAmount)} now</button>
      </div>
      {paying && (
        <PaymentCheckout applicationId={d.summary.id}
                         onPaid={(s) => {
                           setPaying(false);
                           // Replace the "not submitted" warning with the success message
                           navigate(location.pathname, { replace: true,
                             state: { message: `Payment successful. Application ${s.referenceNumber} submitted; your fee receipt is attached.` } });
                           onDone();
                         }}
                         onClose={() => setPaying(false)} />
      )}
    </div>
  );
}

// Finance Officer: cross-check the payment against the records, in parallel with document review
function FeeCheckPanel({ d, onDone }) {
  const [remark, setRemark] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const check = async (found) => {
    if (!found && !remark.trim()) {
      setError({ message: 'Say why the payment could not be found.' });
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api(`/staff/applications/${d.summary.id}/fee-check`, { method: 'POST', body: { found, remark } });
      onDone();
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  };
  return (
    <div className="card action-card">
      <h3>Verify fee payment</h3>
      <p className="muted small">Match this record with the gateway settlement / bank statement. Confirming lets the
        Administrative Officer give the final approval; "Not found" rejects the application.</p>
      <ErrorBox error={error} />
      <dl className="facts">
        <div><dt>Amount</dt><dd>{rupees(d.fee.amount)}</dd></div>
        <div><dt>Payment ID</dt><dd><code>{d.fee.paymentId}</code></dd></div>
        <div><dt>Order ID</dt><dd><code>{d.fee.orderId}</code></dd></div>
        <div><dt>Receipt no.</dt><dd>{d.fee.receiptNumber}</dd></div>
      </dl>
      <label className="field"><span>Remark (required if not found)</span>
        <textarea rows={2} value={remark} onChange={(e) => setRemark(e.target.value)} /></label>
      <div className="form-actions start">
        <button className="btn btn-primary" disabled={busy} onClick={() => check(true)}>Payment confirmed</button>
        <button className="btn btn-danger" disabled={busy} onClick={() => check(false)}>Payment not found</button>
      </div>
    </div>
  );
}

const TEACHER = {
  PENDING: ['Waiting for approval', 'status-awaiting_correction'],
  APPROVED: ['Approved by teacher', 'status-approved'],
  REJECTED: ['Rejected by teacher', 'status-rejected'],
};

// The supervising teacher's email approval, shown to the student and to the HOD before deciding
function SupervisorCard({ s }) {
  const [label, cls] = TEACHER[s.status];
  return (
    <div className="card">
      <h3>Supervising teacher</h3>
      <span className={`badge ${cls}`}>{label}</span>
      <dl className="facts single fee-facts">
        <div><dt>Name</dt><dd>{s.teacherName}</dd></div>
        <div><dt>Department</dt><dd>{s.teacherDepartment}</dd></div>
        <div><dt>Email</dt><dd>{s.teacherEmail}</dd></div>
        <div><dt>Approval email sent</dt><dd>{formatDate(s.emailSentAt)}</dd></div>
        {s.decidedAt && <div><dt>Responded</dt><dd>{formatDate(s.decidedAt)}</dd></div>}
        {s.remark && <div><dt>Teacher's remark</dt><dd>{s.remark}</dd></div>}
      </dl>
    </div>
  );
}

// Student: the claim is with the teacher; the approval email can be sent again
function WaitingForTeacher({ d, onDone }) {
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);
  const resend = async () => {
    setError(null);
    try {
      await api(`/student/applications/${d.summary.id}/supervisor/resend`, { method: 'POST' });
      setMessage(`A new approval link was emailed to ${d.supervisor.teacherEmail}. The old link no longer works.`);
      onDone();
    } catch (err) {
      setError(err);
    }
  };
  return (
    <div className="card action-card">
      <h3>Waiting for your teacher</h3>
      <p>An approval link was emailed to <strong>{d.supervisor.teacherName}</strong> ({d.supervisor.teacherEmail}).
        Your claim goes to the HOD once they approve. If they did not get the email, send it again.</p>
      <Alert kind="success">{message}</Alert>
      <ErrorBox error={error} />
      <div className="form-actions start">
        <button className="btn btn-outline" onClick={resend}>Resend approval email</button>
      </div>
    </div>
  );
}
