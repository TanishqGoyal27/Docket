import { useState } from 'react';
import { Link } from 'react-router-dom';
import { formatDate, rupees } from '../../labels.js';
import { Empty, Loading, PageHeader, useApi } from '../../components/ui.jsx';

const LABEL = { PENDING: 'Pending', CONFIRMED: 'Confirmed', NOT_FOUND: 'Not found' };
const CLS = { PENDING: 'status-awaiting_correction', CONFIRMED: 'status-approved', NOT_FOUND: 'status-rejected' };

// Finance Officer: every online fee payment from the payment records, to cross-check with the gateway
// settlement or bank statement. Runs in parallel with the Administrative Officer's document check.
export default function FeeVerification() {
  const fees = useApi('/staff/fees');
  const [filter, setFilter] = useState('PENDING');
  const rows = (fees.data || []).filter((f) => !filter || f.verification === filter);
  const pending = (fees.data || []).filter((f) => f.verification === 'PENDING').length;

  return (
    <>
      <PageHeader title="Fee Verification"
                  subtitle="Online application-fee payments recorded by the gateway. Confirm each against the settlement or bank statement."
                  actions={
                    <select aria-label="Filter" value={filter} onChange={(e) => setFilter(e.target.value)}>
                      <option value="PENDING">Pending ({pending})</option>
                      <option value="CONFIRMED">Confirmed</option>
                      <option value="NOT_FOUND">Not found</option>
                      <option value="">All payments</option>
                    </select>} />
      <Loading state={fees}>
        {rows.length === 0 ? <Empty>No payments in this list.</Empty> : (
          <div className="table-wrap card">
            <table>
              <thead>
                <tr><th>Application</th><th>Student</th><th>Service</th><th>Amount</th><th>Method</th><th>Payment ID / UTR</th>
                  <th>Receipt</th><th>Paid on</th><th>Verification</th></tr>
              </thead>
              <tbody>
                {rows.map((f) => (
                  <tr key={f.applicationId}>
                    <td><Link className="ref" to={`/staff/applications/${f.applicationId}`}>{f.referenceNumber}</Link></td>
                    <td>{f.studentName}</td>
                    <td>{f.serviceName}</td>
                    <td className="nowrap">{rupees(f.amount)}</td>
                    <td>{f.gateway === 'UPI' ? 'UPI' : f.gateway === 'SANDBOX' ? 'Sandbox' : 'Razorpay'}</td>
                    <td><code>{f.paymentId}</code></td>
                    <td className="nowrap">{f.receiptNumber}</td>
                    <td className="nowrap">{formatDate(f.paidAt)}</td>
                    <td><span className={`badge ${CLS[f.verification]}`}>{LABEL[f.verification]}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Loading>
    </>
  );
}
