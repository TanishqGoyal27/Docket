import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../../auth.jsx';
import { ROLE_LABELS, formatDate, rupees } from '../../labels.js';
import { Alert, Empty, Loading, PageHeader, StatusBadge, useApi } from '../../components/ui.jsx';
import { Icon } from '../../components/Chrome.jsx';

const SERVICE_ICONS = {
  'ID Card': 'idcard',
  Reimbursement: 'rupee',
  'Details Correction': 'edit',
  'Document Verification': 'files',
  'Scholarship Verification': 'award',
};

const plural = (n, word) => `${n} ${word}${n === 1 ? '' : 's'}`;

// Student home (UC-07.1, FR14): every application on one page, plus the services to apply for
export default function StudentDashboard() {
  const { user } = useAuth();
  const location = useLocation();
  const services = useApi('/student/services');
  const applications = useApi('/student/applications');

  const needsAction = (applications.data || []).filter((a) => a.status === 'AWAITING_CORRECTION');
  const unpaid = (applications.data || []).filter((a) => a.status === 'PAYMENT_PENDING');

  return (
    <>
      <PageHeader title="Docket: Dashboard" kicker={`Welcome, ${user.name}`}
                  subtitle={[user.rollNumber, user.course].filter(Boolean).join(' · ') || ROLE_LABELS[user.role]} />
      <Alert kind="success">{location.state?.message}</Alert>
      {needsAction.length > 0 && (
        <Alert kind="warning">
          {needsAction.length === 1 ? 'One application needs' : `${needsAction.length} applications need`} your
          correction: {needsAction.map((a, i) => (
            <span key={a.id}>{i > 0 && ', '}<Link to={`/student/applications/${a.id}`}>{a.referenceNumber}</Link></span>
          ))}
        </Alert>
      )}

      {unpaid.length > 0 && (
        <Alert kind="warning">
          Fee not paid, so these applications are <strong>not submitted</strong> yet: {unpaid.map((a, i) => (
            <span key={a.id}>{i > 0 && ', '}<Link to={`/student/applications/${a.id}`}>{a.referenceNumber} (pay now)</Link></span>
          ))}
        </Alert>
      )}

      <section className="section">
        <h2>Apply for a service</h2>
        <Loading state={services}>
          <div className="tile-grid">
            {(services.data || []).map((s) => (
              <Link key={s.id} to={`/student/apply/${s.id}`} className="service-tile">
                <span className="tile-icon"><Icon name={SERVICE_ICONS[s.name] || 'layers'} /></span>
                <div className="tile-body">
                  <h3>{s.name}</h3>
                  <p>{s.description}</p>
                  <div className="chips">
                    <span className="chip">{plural(s.stages.filter((st) => !st.optional).length, 'step')}</span>
                    <span className="chip">{plural(s.documents.filter((d) => d.mandatory).length, 'document')}</span>
                    <span className={`chip ${s.feeAmount > 0 ? 'chip-fee' : ''}`}>{s.feeAmount > 0 ? `Fee ${rupees(s.feeAmount)}` : 'Free'}</span>
                  </div>
                </div>
                <span className="tile-go">Apply <Icon name="arrow" size={16} /></span>
              </Link>
            ))}
          </div>
        </Loading>
      </section>

      <section className="section">
        <h2>My applications</h2>
        <Loading state={applications}>
          {applications.data?.length === 0 ? (
            <Empty>You have not submitted any applications yet. Choose a service above to start.</Empty>
          ) : (
            <div className="table-wrap card">
              <table>
                <thead>
                  <tr><th>Reference</th><th>Service</th><th>Status</th><th>Current stage</th><th>Submitted</th></tr>
                </thead>
                <tbody>
                  {(applications.data || []).map((a) => (
                    <tr key={a.id}>
                      <td><Link to={`/student/applications/${a.id}`} className="ref">{a.referenceNumber}</Link></td>
                      <td>{a.serviceName}</td>
                      <td><StatusBadge status={a.status} /></td>
                      <td>{a.currentStage
                        ? <>{a.currentStage}<span className="muted small"> · {ROLE_LABELS[a.currentRole]}{a.department ? `, ${a.department}` : ''}</span></>
                        : <span className="muted">Finished</span>}</td>
                      <td>{formatDate(a.submittedAt, false)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Loading>
      </section>
    </>
  );
}
