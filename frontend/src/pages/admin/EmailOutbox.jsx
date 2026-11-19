import { formatDate } from '../../labels.js';
import { Alert, Empty, Loading, PageHeader, useApi } from '../../components/ui.jsx';

const STATUS = {
  SENT: ['Sent', 'status-approved'],
  NOT_SENT_NO_SMTP: ['Not sent (no mail server)', 'status-awaiting_correction'],
  FAILED: ['Failed', 'status-rejected'],
};

// Shows the clickable link only for emails that were never delivered (demo mode);
// a delivered link belongs to the teacher alone, so it is hidden
function Body({ email }) {
  if (email.status === 'SENT') {
    return <pre className="mail-body">{email.body.replace(/\/supervisor\/[0-9a-f]{64}/g, '/supervisor/••••••')}</pre>;
  }
  const parts = email.body.split(/(https?:\/\/\S+)/g);
  return (
    <pre className="mail-body">
      {parts.map((p, i) => (/^https?:\/\//.test(p)
        ? <a key={i} href={p.replace(/^https?:\/\/[^/]+/, '')} target="_blank" rel="noreferrer">{p}</a>
        : p))}
    </pre>
  );
}

export default function EmailOutbox() {
  const state = useApi('/admin/emails');
  const d = state.data;
  return (
    <>
      <PageHeader title="Email Outbox" subtitle="Every email Docket sends, such as approval requests to supervising teachers." />
      <Loading state={state}>
        {d && (
          <>
            {!d.smtpConfigured && (
              <Alert kind="warning">No mail server is configured, so emails are saved here instead of being sent.
                For a demo, open the approval link below as the teacher would. To send real email, set the
                SPRING_MAIL_* settings described in the README.</Alert>
            )}
            {d.emails.length === 0 ? <Empty>No emails yet.</Empty> : d.emails.map((e) => (
              <div key={e.id} className="card mail">
                <div className="row-between">
                  <div>
                    <strong>{e.subject}</strong>
                    <div className="muted small">To {e.recipient} · {formatDate(e.createdAt)}</div>
                  </div>
                  <span className={`badge ${STATUS[e.status][1]}`}>{STATUS[e.status][0]}</span>
                </div>
                {e.error && <p className="small" style={{ color: 'var(--bad)' }}>{e.error}</p>}
                <Body email={e} />
              </div>
            ))}
          </>
        )}
      </Loading>
    </>
  );
}
