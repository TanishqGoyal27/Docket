import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../../auth.jsx';
import { ROLE_LABELS, formatDate, rupees } from '../../labels.js';
import ApplicationTable from '../../components/ApplicationTable.jsx';
import { Alert, Empty, Loading, PageHeader, useApi } from '../../components/ui.jsx';

// Pending approval queue (UC-07.2, FR17): only applications at this officer's stage and department.
// The Finance Officer also sees paid fees waiting for verification, which run in parallel to the stages.
export default function StaffQueue() {
  const { user } = useAuth();
  const finance = user.role === 'FINANCE_OFFICER';
  return (
    <>
      <PageHeader title="Pending Queue"
                  subtitle={`${ROLE_LABELS[user.role]}${user.department ? ` · ${user.department}` : ''}`} />
      {finance ? <FinanceQueue /> : <StageQueue />}
    </>
  );
}

function StageQueue({ showStats = true }) {
  const location = useLocation();
  const queue = useApi('/staff/queue');
  const rows = queue.data || [];
  return (
    <>
      <Alert kind="success">{location.state?.message}</Alert>
      {showStats && (
        <div className="stats">
          <div className="stat card"><span className="stat-num">{rows.length}</span><span className="muted">waiting for you</span></div>
          <div className="stat card"><span className="stat-num">{rows.filter((a) => a.status === 'UNDER_REVIEW').length}</span>
            <span className="muted">resubmitted or forwarded</span></div>
        </div>
      )}
      <Loading state={queue}>
        {rows.length === 0
          ? <Empty>No application is waiting at your stage right now.</Empty>
          : <ApplicationTable rows={rows} linkBase="/staff/applications" showStage />}
      </Loading>
    </>
  );
}

function FinanceQueue() {
  const fees = useApi('/staff/fees');
  const stageQueue = useApi('/staff/queue');
  const pendingFees = (fees.data || []).filter((f) => f.verification === 'PENDING');

  return (
    <>
      <div className="stats">
        <Link to="/staff/fees" className="stat card">
          <span className="stat-num">{pendingFees.length}</span><span className="muted">fee payments to verify</span>
        </Link>
        <div className="stat card">
          <span className="stat-num">{(stageQueue.data || []).length}</span><span className="muted">bills / claims at your stage</span>
        </div>
      </div>

      <section className="section">
        <h2>Fee payments waiting for verification</h2>
        <Loading state={fees}>
          {pendingFees.length === 0 ? <Empty>No fee payment is waiting for verification.</Empty> : (
            <div className="table-wrap card">
              <table>
                <thead><tr><th>Application</th><th>Student</th><th>Service</th><th>Amount</th><th>Payment ID</th><th>Paid on</th><th /></tr></thead>
                <tbody>
                  {pendingFees.map((f) => (
                    <tr key={f.applicationId}>
                      <td><Link className="ref" to={`/staff/applications/${f.applicationId}`}>{f.referenceNumber}</Link></td>
                      <td>{f.studentName}</td>
                      <td>{f.serviceName}</td>
                      <td className="nowrap">{rupees(f.amount)}</td>
                      <td><code>{f.paymentId}</code></td>
                      <td className="nowrap">{formatDate(f.paidAt)}</td>
                      <td className="right"><Link className="btn btn-small btn-primary" to={`/staff/applications/${f.applicationId}`}>Verify</Link></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Loading>
      </section>

      <section className="section">
        <h2>Applications at your stage</h2>
        <StageQueue showStats={false} />
      </section>
    </>
  );
}
