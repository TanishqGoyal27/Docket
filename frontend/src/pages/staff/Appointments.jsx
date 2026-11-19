import { Link } from 'react-router-dom';
import { formatDate } from '../../labels.js';
import { Empty, Loading, PageHeader, useApi } from '../../components/ui.jsx';

// Upcoming in-person visits booked by students (UC-01.6)
export default function Appointments() {
  const visits = useApi('/staff/appointments');
  return (
    <>
      <PageHeader title="Upcoming visits" subtitle="Students coming to collect their ID card or for physical verification." />
      <Loading state={visits}>
        {visits.data?.length === 0 ? <Empty>No visits are booked.</Empty> : (
          <div className="table-wrap card">
            <table>
              <thead><tr><th>When</th><th>Student</th><th>Application</th></tr></thead>
              <tbody>
                {(visits.data || []).map((v) => (
                  <tr key={v.id}>
                    <td>{formatDate(v.slot)}</td>
                    <td>{v.studentName}</td>
                    <td><Link className="ref" to={`/staff/applications/${v.applicationId}`}>{v.referenceNumber}</Link></td>
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
