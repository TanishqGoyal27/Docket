import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth.jsx';
import { homePath, OFFICER_ROLES } from './labels.js';
import Layout from './components/Layout.jsx';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import Notifications from './pages/Notifications.jsx';
import ApplicationPage from './pages/ApplicationPage.jsx';
import StudentDashboard from './pages/student/StudentDashboard.jsx';
import Apply from './pages/student/Apply.jsx';
import StaffQueue from './pages/staff/StaffQueue.jsx';
import StaffHistory from './pages/staff/StaffHistory.jsx';
import Appointments from './pages/staff/Appointments.jsx';
import FeeVerification from './pages/staff/FeeVerification.jsx';
import AdminOverview from './pages/admin/AdminOverview.jsx';
import Users from './pages/admin/Users.jsx';
import Services from './pages/admin/Services.jsx';
import ServiceDetail from './pages/admin/ServiceDetail.jsx';
import AllApplications from './pages/admin/AllApplications.jsx';
import AuditLog from './pages/admin/AuditLog.jsx';
import EmailOutbox from './pages/admin/EmailOutbox.jsx';
import TeacherApproval from './pages/TeacherApproval.jsx';

// Shows the page only to the given roles (the server enforces the same rule)
function RequireRole({ roles, children }) {
  const { user } = useAuth();
  if (user === undefined) return <div className="page-loading">Loading…</div>;
  if (!user) return <Navigate to="/login" replace />;
  if (roles && !roles.includes(user.role)) return <Navigate to={homePath(user.role)} replace />;
  return <Layout>{children}</Layout>;
}

export default function App() {
  const { user } = useAuth();
  const student = ['STUDENT'];
  const admin = ['ADMINISTRATOR'];

  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/supervisor/:token" element={<TeacherApproval />} />
      <Route path="/" element={
        user === undefined ? <div className="page-loading">Loading…</div>
          : <Navigate to={user ? homePath(user.role) : '/login'} replace />
      } />

      <Route path="/student" element={<RequireRole roles={student}><StudentDashboard /></RequireRole>} />
      <Route path="/student/apply/:serviceId" element={<RequireRole roles={student}><Apply /></RequireRole>} />
      <Route path="/student/applications/:id" element={<RequireRole roles={student}><ApplicationPage /></RequireRole>} />

      <Route path="/staff" element={<RequireRole roles={OFFICER_ROLES}><StaffQueue /></RequireRole>} />
      <Route path="/staff/history" element={<RequireRole roles={OFFICER_ROLES}><StaffHistory /></RequireRole>} />
 <Route path="/staff/fees" element={<RequireRole roles={['FINANCE_OFFICER']}><FeeVerification /></RequireRole>} />
      <Route path="/staff/appointments" element={<RequireRole roles={['ADMIN_OFFICER']}><Appointments /></RequireRole>} />
      <Route path="/staff/applications/:id" element={<RequireRole roles={OFFICER_ROLES}><ApplicationPage /></RequireRole>} />

      <Route path="/admin" element={<RequireRole roles={admin}><AdminOverview /></RequireRole>} />
      <Route path="/admin/users" element={<RequireRole roles={admin}><Users /></RequireRole>} />
      <Route path="/admin/services" element={<RequireRole roles={admin}><Services /></RequireRole>} />
      <Route path="/admin/services/:id" element={<RequireRole roles={admin}><ServiceDetail /></RequireRole>} />
      <Route path="/admin/applications" element={<RequireRole roles={admin}><AllApplications /></RequireRole>} />
      <Route path="/admin/applications/:id" element={<RequireRole roles={admin}><ApplicationPage /></RequireRole>} />
      <Route path="/admin/emails" element={<RequireRole roles={admin}><EmailOutbox /></RequireRole>} />
      <Route path="/admin/audit" element={<RequireRole roles={admin}><AuditLog /></RequireRole>} />

      <Route path="/notifications" element={<RequireRole><Notifications /></RequireRole>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
