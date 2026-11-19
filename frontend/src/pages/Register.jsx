import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth.jsx';
import { homePath } from '../labels.js';
import { ErrorBox } from '../components/ui.jsx';
import AuthLayout from '../components/AuthLayout.jsx';

// Student sign-up. Staff accounts are created by the Administrator, not here.
export default function Register() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const [v, setV] = useState({ name: '', email: '', rollNumber: '', course: '', phone: '', password: '', confirm: '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={homePath(user.role)} replace />;

  const set = (k) => (e) => setV({ ...v, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    if (v.password !== v.confirm) {
      setError({ message: 'The two passwords do not match.' });
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const { confirm, ...details } = v;
      await register(details);
      navigate('/student', { state: { message: 'Registration successful. Your student account is ready.' } });
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout>
        <form className="form-card form-wide" onSubmit={submit}>
          <div>
            <h1>Create your account</h1>
            <p className="muted small">Fields marked <span className="req">*</span> are mandatory.
              Officers and staff do not register here; their accounts are created by the Administrator.</p>
            <ErrorBox error={error} />
            <div className="row">
              <label className="field"><span>Full name (as per college records)<span className="req">*</span></span>
                <input required autoComplete="name" value={v.name} onChange={set('name')} /></label>
              <label className="field"><span>Roll number<span className="req">*</span></span>
                <input required value={v.rollNumber} onChange={set('rollNumber')} /></label>
            </div>
            <div className="row">
              <label className="field"><span>Email ID<span className="req">*</span></span>
                <input type="email" required autoComplete="email" value={v.email} onChange={set('email')} /></label>
              <label className="field"><span>Mobile number</span>
                <input type="tel" autoComplete="tel" value={v.phone} onChange={set('phone')} /></label>
            </div>
            <label className="field"><span>Course</span>
              <input value={v.course} onChange={set('course')} placeholder="e.g. BE Computer Engineering" /></label>
            <div className="row">
              <label className="field"><span>Password (minimum 8 characters)<span className="req">*</span></span>
                <input type="password" required minLength={8} autoComplete="new-password" value={v.password} onChange={set('password')} /></label>
              <label className="field"><span>Confirm password<span className="req">*</span></span>
                <input type="password" required minLength={8} autoComplete="new-password" value={v.confirm} onChange={set('confirm')} /></label>
            </div>
            <div className="form-actions">
              <Link to="/login" className="btn btn-outline">Back to log in</Link>
              <button className="btn btn-primary" disabled={busy}>{busy ? 'Creating…' : 'Create my new account'}</button>
            </div>
          </div>
        </form>
    </AuthLayout>
  );
}
