// Display text shared by every page

export const ROLE_LABELS = {
  STUDENT: 'Student',
  ADMIN_OFFICER: 'Administrative Officer',
  PROFESSOR: 'Professor / Supervisor',
  HOD: 'Head of Department',
  SCHOLARSHIP_OFFICER: 'Scholarship Officer',
  FINANCE_OFFICER: 'Finance Officer',
  ACCOUNTS_OFFICER: 'Accounts Officer',
  ADMINISTRATOR: 'Administrator',
};

export const ROLES = Object.keys(ROLE_LABELS);
export const OFFICER_ROLES = ROLES.filter((r) => r !== 'STUDENT' && r !== 'ADMINISTRATOR');

export const STATUS_LABELS = {
  PAYMENT_PENDING: 'Payment pending',
  SUPERVISOR_PENDING: 'Awaiting teacher approval',
  SUBMITTED: 'Submitted',
  UNDER_REVIEW: 'Under review',
  AWAITING_CORRECTION: 'Awaiting correction',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  PROCESSED: 'Approved (processed)',
  VERIFIED: 'Verified',
  CLOSED: 'Closed',
};

export const DECISION_LABELS = {
  SUBMITTED: 'Submitted',
  RESUBMITTED: 'Resubmitted',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CORRECTION_REQUESTED: 'Correction requested',
  VERIFIED: 'Verified',
  ISSUE_FOUND: 'Issue found',
  PROCESSED: 'Payment recorded',
};

// Service-specific form questions. A service the Administrator adds later
// falls back to a single "Details" box, so it works without code changes.
export const SERVICE_FIELDS = {
  'ID Card': [
    { key: 'requestType', label: 'Request type', type: 'select',
      options: ['New card', 'Replacement (lost)', 'Replacement (damaged)'] },
    { key: 'reason', label: 'Reason for the request', type: 'textarea' },
  ],
  Reimbursement: [
    { key: 'projectTitle', label: 'Project / competition title' },
    { key: 'teacherName', label: 'Supervising teacher name' },
    { key: 'teacherDepartment', label: "Teacher's department", type: 'select',
      options: ['Computer Science and Engineering', 'Electronics and Communication', 'Electrical and Instrumentation',
        'Mechanical Engineering', 'Civil Engineering', 'Chemical Engineering', 'Biotechnology',
        'Mathematics', 'Physics', 'Chemistry', 'Humanities and Social Sciences', 'Other'] },
    { key: 'teacherEmail', label: "Teacher's email ID", type: 'email',
      hint: 'An approval link is emailed here. Your claim reaches the HOD only after the teacher approves.' },
    { key: 'amount', label: 'Claimed amount (₹)', type: 'number' },
    { key: 'description', label: 'Expense details', type: 'textarea' },
  ],
  'Details Correction': [
    { key: 'field', label: 'Field to correct', type: 'select',
      options: [['name', 'Name'], ['phone', 'Phone'], ['course', 'Course'], ['rollNumber', 'Roll number']] },
    { key: 'newValue', label: 'Correct value' },
    { key: 'reason', label: 'Reason', type: 'textarea' },
  ],
  'Document Verification': [
    { key: 'documentType', label: 'Document type', type: 'select',
      options: ['Degree certificate', 'Transcript', 'Bonafide certificate', 'Migration certificate', 'Other'] },
    { key: 'purpose', label: 'Purpose of verification', type: 'textarea' },
  ],
  'Scholarship Verification': [
    { key: 'scholarshipName', label: 'Scholarship name' },
    { key: 'familyIncome', label: 'Annual family income (₹)', type: 'number' },
    { key: 'category', label: 'Category (if applicable)' },
  ],
};

export const DEFAULT_FIELDS = [{ key: 'details', label: 'Details', type: 'textarea' }];

export function fieldsFor(serviceName) {
  return SERVICE_FIELDS[serviceName] || DEFAULT_FIELDS;
}

export function fieldLabel(serviceName, key) {
  const field = fieldsFor(serviceName).find((f) => f.key === key);
  if (field) return field.label;
  const spaced = key.replace(/([A-Z])/g, ' $1').toLowerCase();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

export function formatDate(value, withTime = true) {
  if (!value) return '—';
  const d = new Date(value);
  return d.toLocaleString('en-IN', withTime
    ? { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }
    : { day: '2-digit', month: 'short', year: 'numeric' });
}

// Role label, including the teacher who approves from an emailed link
export function roleLabel(role) {
  return ROLE_LABELS[role] || (role === 'SUPERVISOR' ? 'Supervising teacher (by email)' : role);
}

export function homePath(role) {
  if (role === 'STUDENT') return '/student';
  if (role === 'ADMINISTRATOR') return '/admin';
  return '/staff';
}

export const rupees = (n) => `₹ ${Number(n).toLocaleString('en-IN')}`;

export const MAX_UPLOAD_MB = 10;
export const ALLOWED_TYPES = ['application/pdf', 'image/jpeg', 'image/png'];
