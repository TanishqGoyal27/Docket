import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { ROLE_LABELS } from '../labels.js';
import { AppFooter, Icon, Wordmark } from './Chrome.jsx';

// Sidebar entries per role: [path, label, icon]
function navFor(role) {
  if (role === 'STUDENT') {
    return [['/student', 'Dashboard', 'dashboard'], ['/notifications', 'Notifications', 'bell']];
  }
  if (role === 'ADMINISTRATOR') {
    return [['/admin', 'Dashboard', 'dashboard'], ['/admin/users', 'Users', 'users'],
      ['/admin/services', 'Services & Workflows', 'layers'], ['/admin/applications', 'All Applications', 'files'],
      ['/admin/audit', 'Change Log', 'log'], ['/admin/emails', 'Email Outbox', 'inbox'],
      ['/notifications', 'Notifications', 'bell']];
  }
  const links = [['/staff', 'Pending Queue', 'inbox']];
  if (role === 'FINANCE_OFFICER') links.push(['/staff/fees', 'Fee Verification', 'rupee']);
  links.push(['/staff/history', 'My Decisions', 'history']);
  if (role === 'ADMIN_OFFICER') links.push(['/staff/appointments', 'Visits', 'calendar']);
  links.push(['/notifications', 'Notifications', 'bell']);
  return links;
}

function initials(name) {
  return name.split(' ').filter(Boolean).slice(0, 2).map((w) => w[0]).join('').toUpperCase();
}

export default function Layout({ children }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [unread, setUnread] = useState(0);
  const [open, setOpen] = useState(false);       // sidebar labels shown
  const [menu, setMenu] = useState(false);       // user dropdown

  // Refresh the unread count on every page change and every 30 seconds (QR6)
  useEffect(() => {
    const load = () => api('/notifications/unread-count').then((d) => setUnread(d.count)).catch(() => {});
    load();
    setMenu(false);
    const timer = setInterval(load, 30000);
    return () => clearInterval(timer);
  }, [location.pathname]);

  const signOut = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className={`app ${open ? 'nav-open' : ''}`}>
      <a href="#main" className="skip-link">Skip to main content</a>
      <aside className="sidebar" aria-label="Main menu">
        <button className="side-toggle" onClick={() => setOpen(!open)} aria-expanded={open}
                aria-label={open ? 'Collapse menu' : 'Expand menu'}>
          <Icon name="menu" />
        </button>
        <nav>
          {navFor(user.role).map(([to, label, icon]) => (
            <NavLink key={to} to={to} end title={label} className={({ isActive }) => (isActive ? 'active' : '')}>
              <Icon name={icon} />
              <span className="side-label">{label}</span>
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="app-body">
        <header className="topbar">
          <Link to="/" className="topbar-brand"><Wordmark /></Link>
          <div className="topbar-right">
            <Link to="/notifications" className="bell" aria-label={`Notifications, ${unread} unread`}>
              <Icon name="bell" />
              {unread > 0 && <span className="bell-count">{unread}</span>}
            </Link>
            <div className="user-menu">
              <button className="avatar-btn" onClick={() => setMenu(!menu)} aria-expanded={menu} aria-haspopup="menu">
                <span className="avatar">{initials(user.name)}</span>
                <span className="caret" aria-hidden="true">▾</span>
              </button>
              {menu && (
                <div className="dropdown" role="menu">
                  <div className="dropdown-head">
                    <strong>{user.name}</strong>
                    <span>{ROLE_LABELS[user.role]}{user.department ? ` · ${user.department}` : ''}</span>
                    <span>{user.email}</span>
                  </div>
                  <button role="menuitem" onClick={signOut}><Icon name="logout" size={18} /> Log out</button>
                </div>
              )}
            </div>
          </div>
        </header>
        <main id="main" className="main" tabIndex={-1}>{children}</main>
        <AppFooter />
      </div>
    </div>
  );
}
