import { Icon, Wordmark } from './Chrome.jsx';

const FEATURES = [
  ['files', 'Apply online', 'ID cards, reimbursements, record corrections, document and scholarship verification.'],
  ['track', 'Track every stage', 'See who has your application, what they decided and why.'],
  ['shield', 'Pay fees securely', 'Pay online, get an instant receipt; Finance confirms every payment.'],
];

// Split screen used by login and sign-up: brand panel on the left, the form on the right
export default function AuthLayout({ children }) {
  return (
    <div className="split">
      <a href="#main" className="skip-link">Skip to main content</a>
      <section className="split-brand">
        <Wordmark size="large" light />
        <p className="split-tagline">Every campus request, one docket.</p>
        <p className="split-sub">Digital Campus Administration · Thapar Institute of Engineering &amp; Technology, Patiala</p>
        <ul className="split-features">
          {FEATURES.map(([icon, title, text]) => (
            <li key={title}>
              <span className="feature-icon"><Icon name={icon} /></span>
              <span><strong>{title}</strong><span>{text}</span></span>
            </li>
          ))}
        </ul>
        <div className="split-stats">
          <span><strong>5</strong> services</span>
          <span><strong>8</strong> roles</span>
          <span><strong>0</strong> office visits</span>
        </div>
      </section>
      <main id="main" className="split-form">{children}</main>
    </div>
  );
}
