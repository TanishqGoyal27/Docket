import ApplicationTable from '../../components/ApplicationTable.jsx';
import { Empty, Loading, PageHeader, useApi } from '../../components/ui.jsx';

// Applications this officer has already decided on (FR23)
export default function StaffHistory() {
  const history = useApi('/staff/history');
  return (
    <>
      <PageHeader title="My decisions" subtitle="Applications you have approved, verified, returned or rejected." />
      <Loading state={history}>
        {history.data?.length === 0
          ? <Empty>You have not decided on any applications yet.</Empty>
          : <ApplicationTable rows={history.data || []} linkBase="/staff/applications" />}
      </Loading>
    </>
  );
}
