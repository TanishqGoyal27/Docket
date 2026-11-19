import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { formatDate } from '../labels.js';
import { Empty, Loading, PageHeader, useApi } from '../components/ui.jsx';

// Notification list for every role (UC-08.1, FR4)
export default function Notifications() {
  const { user } = useAuth();
  const list = useApi('/notifications');
  const base = user.role === 'STUDENT' ? '/student/applications'
    : user.role === 'ADMINISTRATOR' ? '/admin/applications' : '/staff/applications';

  const markAll = () => api('/notifications/read-all', { method: 'POST' }).then(list.reload);
  const markOne = (id) => api(`/notifications/${id}/read`, { method: 'POST' }).then(list.reload);
  const unread = (list.data || []).filter((n) => !n.read).length;

  return (
    <>
      <PageHeader title="Notifications" subtitle={`${unread} unread`}
                  actions={unread > 0 && <button className="btn" onClick={markAll}>Mark all as read</button>} />
      <Loading state={list}>
        {list.data?.length === 0 ? <Empty>No notifications yet.</Empty> : (
          <ul className="card notif-list">
            {(list.data || []).map((n) => (
              <li key={n.id} className={n.read ? '' : 'unread'}>
                <div>
                  <p>{n.message}</p>
                  <span className="muted small">{formatDate(n.createdAt)}</span>
                </div>
                <div className="notif-actions">
                  {n.applicationId && (
                    <Link className="btn btn-small" to={`${base}/${n.applicationId}`}
                          onClick={() => !n.read && markOne(n.id)}>Open</Link>
                  )}
                  {!n.read && <button className="btn btn-small btn-ghost" onClick={() => markOne(n.id)}>Mark read</button>}
                </div>
              </li>
            ))}
          </ul>
        )}
      </Loading>
    </>
  );
}
