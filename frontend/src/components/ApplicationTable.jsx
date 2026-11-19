import { Link } from 'react-router-dom';
import { ROLE_LABELS, formatDate } from '../labels.js';
import { StatusBadge } from './ui.jsx';

// List of applications used by staff and administrator pages
export default function ApplicationTable({ rows, linkBase, showStage = true }) {
  return (
    <div className="table-wrap card">
      <table>
        <thead>
          <tr>
            <th>Reference</th><th>Service</th><th>Student</th><th>Status</th>
            {showStage && <th>Current stage</th>}<th>Submitted</th><th>Last update</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((a) => (
            <tr key={a.id}>
              <td><Link to={`${linkBase}/${a.id}`} className="ref">{a.referenceNumber}</Link></td>
              <td>{a.serviceName}</td>
              <td>{a.studentName}</td>
              <td><StatusBadge status={a.status} /></td>
              {showStage && <td>{a.currentStage
                ? <>{a.currentStage}<span className="muted small"> · {ROLE_LABELS[a.currentRole]}</span></>
                : <span className="muted">Finished</span>}</td>}
              <td>{formatDate(a.submittedAt, false)}</td>
              <td>{formatDate(a.updatedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
