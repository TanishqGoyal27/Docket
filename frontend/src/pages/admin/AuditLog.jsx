import { formatDate } from '../../labels.js';
import { Empty, Loading, PageHeader, useApi } from '../../components/ui.jsx';

// Change log of user, configuration and student-record changes (FR40). Read-only by design (QR17).
export default function AuditLog() {
  const log = useApi('/admin/audit');
  return (
    <>
      <PageHeader title="Change log"
                  subtitle="Every user, configuration and student-record change. Entries cannot be edited or deleted. Application decisions are in each application's history." />
      <Loading state={log}>
        {log.data?.length === 0 ? <Empty>No changes recorded yet.</Empty> : (
          <div className="table-wrap card">
            <table>
              <thead><tr><th>When</th><th>Who</th><th>Action</th><th>Details</th></tr></thead>
              <tbody>
                {(log.data || []).map((e) => (
                  <tr key={e.id}>
                    <td className="nowrap">{formatDate(e.at)}</td>
                    <td>{e.actorName}</td>
                    <td><code>{e.action}</code></td>
                    <td>{e.details}</td>
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
