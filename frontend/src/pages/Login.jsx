import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth.jsx';
import { homePath, ROLE_LABELS } from '../labels.js';
import { Alert } from '../components/ui.jsx';
import AuthLayout from '../components/AuthLayout.jsx';

// Seeded test accounts (QR19), all with password "password123"
const DEMO_ACCOUNTS = [
  ['student@docket.com', 'STUDENT'],
  ['adminofficer@docket.com', 'ADMIN_OFFICER'],
  ['hod@docket.com', 'HOD'],
  ['scholarship@docket.com', 'SCHOLARSHIP_OFFICER'],
  ['finance@docket.com', 'FINANCE_OFFICER'],
  ['accounts@docket.com', 'ACCOUNTS_OFFICER'],
  ['admin@docket.com', 'ADMINISTRATOR'],
];

// Login: brand panel on the left, sign-in form on the right
export default function Login() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={homePath(user.role)} replace />;

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const me = await login(email, password);
      navigate(homePath(me.role));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout>
      <div className="form-card">
        <h1>Sign in</h1>
        <p className="muted">Welcome back. Use your Docket email and password.</p>
        <form onSubmit={submit}>
          <Alert>{error}</Alert>
          <label className="field">
            <span>Email</span>
            <input type="email" autoComplete="username" required value={email}
                   onChange={(e) => setEmail(e.target.value)} placeholder="you@thapar.edu" />
          </label>
          <label className="field">
            <span>Password</span>
            <input type="password" autoComplete="current-password" required value={password}
                   onChange={(e) => setPassword(e.target.value)} />
          </label>
          <button className="btn btn-primary btn-block btn-large" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        </form>
        <p className="form-foot">New student? <Link to="/register">Create your account</Link></p>
        <p className="muted small">Officers get their account from the Administrator, who can also reset a forgotten password.</p>

        <details className="demo">
          <summary>Test accounts (password: password123)</summary>
          <div className="demo-list">
            {DEMO_ACCOUNTS.map(([mail, role]) => (
              <button key={mail} type="button" className="demo-item"
                      onClick={() => { setEmail(mail); setPassword('password123'); }}>
                <span>{ROLE_LABELS[role]}</span>
                <span className="small">{mail}</span>
              </button>
            ))}
          </div>
        </details>
      </div>
    </AuthLayout>
  );
}
