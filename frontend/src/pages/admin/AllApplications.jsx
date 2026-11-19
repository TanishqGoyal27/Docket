import { useState } from 'react';
import { STATUS_LABELS } from '../../labels.js';
import ApplicationTable from '../../components/ApplicationTable.jsx';
import { Empty, Loading, PageHeader, useApi } from '../../components/ui.jsx';

// Every application in the system, so the Administrator can spot stuck ones (QR11)
export default function AllApplications() {
  const apps = useApi('/admin/applications');
  const [status, setStatus] = useState('');
  const rows = (apps.data || []).filter((a) => !status || a.status === status);
  return (
    <>
      <PageHeader title="All applications" subtitle="Read-only view across every service."
                  actions={
                    <select aria-label="Filter by status" value={status} onChange={(e) => setStatus(e.target.value)}>
                      <option value="">All statuses</option>
                      {Object.entries(STATUS_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                    </select>} />
      <Loading state={apps}>
        {rows.length === 0 ? <Empty>No applications match.</Empty>
          : <ApplicationTable rows={rows} linkBase="/admin/applications" />}
      </Loading>
    </>
  );
}
