import { useState } from 'react';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { ROLE_LABELS, ROLES } from '../../labels.js';
import { Alert, ErrorBox, Loading, PageHeader, useApi } from '../../components/ui.jsx';

const EMPTY = { name: '', email: '', password: '', role: 'STUDENT', department: '', rollNumber: '', course: '', phone: '' };

// Manage user accounts and roles (UC-09.1, UC-09.2, FR29, FR30)
export default function Users() {
  const users = useApi('/admin/users');
  const [editing, setEditing] = useState(null); // null, 'new' or a user object
  const [message, setMessage] = useState(null);
  const [filter, setFilter] = useState('');

  const done = (text) => { setEditing(null); setMessage(text); users.reload(); };
  const unlock = async (u) => {
    await api(`/admin/users/${u.id}/unlock`, { method: 'POST' });
    done(`${u.email} unlocked.`);
  };

  const rows = (users.data || []).filter((u) =>
    !filter || `${u.name} ${u.email} ${ROLE_LABELS[u.role]} ${u.department || ''}`.toLowerCase().includes(filter.toLowerCase()));

  return (
    <>
      <PageHeader title="Users" subtitle="Create accounts, assign roles and departments, unlock or deactivate users."
                  actions={<button className="btn btn-primary" onClick={() => { setMessage(null); setEditing('new'); }}>New user</button>} />
      <Alert kind="success">{message}</Alert>
      {editing && <UserForm user={editing === 'new' ? null : editing} onCancel={() => setEditing(null)} onSaved={done} />}
      <input className="search" placeholder="Search by name, email, role or department" value={filter}
             onChange={(e) => setFilter(e.target.value)} aria-label="Search users" />
      <Loading state={users}>
        <div className="table-wrap card">
          <table>
            <thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Department</th><th>Status</th><th /></tr></thead>
            <tbody>
              {rows.map((u) => (
                <tr key={u.id}>
                  <td>{u.name}</td>
                  <td>{u.email}</td>
                  <td>{ROLE_LABELS[u.role]}</td>
                  <td>{u.department || '—'}</td>
                  <td>
                    {!u.active ? <span className="badge status-rejected">Deactivated</span>
                      : u.locked ? <span className="badge status-awaiting_correction">Locked</span>
                        : <span className="badge status-approved">Active</span>}
                  </td>
                  <td className="right nowrap">
                    {u.locked && <button className="btn btn-small" onClick={() => unlock(u)}>Unlock</button>}{' '}
                    <button className="btn btn-small btn-ghost" onClick={() => { setMessage(null); setEditing(u); }}>Edit</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Loading>
    </>
  );
}

function UserForm({ user, onCancel, onSaved }) {
  const { user: me } = useAuth();
  const [v, setV] = useState(user ? { ...EMPTY, ...user, department: user.department || '', rollNumber: user.rollNumber || '',
    course: user.course || '', phone: user.phone || '', resetPassword: '' } : EMPTY);
  const [error, setError] = useState(null);
  const set = (k) => (e) => setV({ ...v, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });
  const self = user && user.id === me.id;

  const save = async (e) => {
    e.preventDefault();
    setError(null);
    try {
      if (user) {
        await api(`/admin/users/${user.id}`, { method: 'PUT', body: v });
        onSaved(`${user.email} updated.`);
      } else {
        const created = await api('/admin/users', { method: 'POST', body: v });
        onSaved(`${created.email} created.`);
      }
    } catch (err) {
      setError(err);
    }
  };

  return (
    <form className="card form inline-form" onSubmit={save}>
      <h2>{user ? `Edit ${user.email}` : 'New user'}</h2>
      <ErrorBox error={error} />
      <div className="row">
        <label className="field"><span>Full name<span className="req">*</span></span><input required value={v.name} onChange={set('name')} /></label>
        {!user && <label className="field"><span>Email<span className="req">*</span></span><input type="email" required value={v.email} onChange={set('email')} /></label>}
      </div>
      <div className="row">
        <label className="field"><span>Role<span className="req">*</span></span>
          <select value={v.role} onChange={set('role')} disabled={self}>
            {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
          </select>
        </label>
        <label className="field"><span>Department</span><input value={v.department} onChange={set('department')} placeholder="e.g. Computer Science" /></label>
      </div>
      {v.role === 'STUDENT' && (
        <div className="row">
          <label className="field"><span>Roll number</span><input value={v.rollNumber} onChange={set('rollNumber')} /></label>
          <label className="field"><span>Course</span><input value={v.course} onChange={set('course')} /></label>
        </div>
      )}
      <div className="row">
        <label className="field"><span>Phone</span><input value={v.phone} onChange={set('phone')} /></label>
        {user
          ? <label className="field"><span>Reset password (leave empty to keep)</span><input type="password" minLength={8} autoComplete="new-password" value={v.resetPassword} onChange={set('resetPassword')} /></label>
          : <label className="field"><span>Password (min 8 characters)<span className="req">*</span></span><input type="password" minLength={8} required autoComplete="new-password" value={v.password} onChange={set('password')} /></label>}
      </div>
      {user && !self && (
        <label className="check"><input type="checkbox" checked={v.active} onChange={set('active')} /><span>Account active</span></label>
      )}
      <div className="form-actions">
        <button type="button" className="btn btn-ghost" onClick={onCancel}>Cancel</button>
        <button className="btn btn-primary">Save</button>
      </div>
    </form>
  );
}
