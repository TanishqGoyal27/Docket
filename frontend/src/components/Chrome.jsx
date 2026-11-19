// Shared look: maroon accents, Poppins, Docket logo.

// Docket logo: a badge holding a filed document with a tick, then the name.
// `light` is for dark backgrounds (white badge, white text).
export function Wordmark({ size = 'normal', light = false }) {
  const badge = light ? '#ffffff' : '#930002';
  const ink = light ? '#930002' : '#ffffff';
  return (
    <span className={`logo logo-${size} ${light ? 'logo-light' : ''}`} aria-label="Docket">
      <svg className="logo-mark" viewBox="0 0 48 48" aria-hidden="true">
        <rect width="48" height="48" rx="12" fill={badge} />
        <path d="M15 10h13l7 7v21a2 2 0 0 1-2 2H15a2 2 0 0 1-2-2V12a2 2 0 0 1 2-2z" fill={ink} />
        <path d="M28 10v7h7" fill="none" stroke={badge} strokeWidth="2" strokeLinejoin="round" />
        <path d="M18.5 28.5l4 4 7.5-8" fill="none" stroke={badge} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <span className="logo-text">Docket</span>
    </span>
  );
}

const PATHS = {
  dashboard: 'M12 3a9 9 0 1 0 9 9M12 12l5-5M3 12h2M12 3v2',
  home: 'M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z',
  bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 0 1-3.4 0',
  inbox: 'M22 12h-6l-2 3h-4l-2-3H2M5.5 5h13L22 12v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-6z',
  history: 'M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5M12 7v5l3 3',
  calendar: 'M4 5h16v16H4zM4 10h16M9 3v4M15 3v4',
  rupee: 'M6 4h12M6 9h12M6 4h3a5 5 0 0 1 0 10H6l9 7',
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M22 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8',
  layers: 'M12 2 2 7l10 5 10-5zM2 17l10 5 10-5M2 12l10 5 10-5',
  files: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zM14 2v6h6M8 13h8M8 17h8',
  log: 'M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2M9 5a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2 2 2 0 0 0-2-2h-2a2 2 0 0 0-2 2M9 13h6M9 17h4',
  menu: 'M3 6h18M3 12h18M3 18h18',
  idcard: 'M3 5h18v14H3zM7 10a2 2 0 1 0 4 0 2 2 0 0 0-4 0M6 16c.5-1.5 1.8-2 3-2s2.5.5 3 2M14 9h4M14 13h4',
  edit: 'M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z',
  award: 'M12 15a6 6 0 1 0 0-12 6 6 0 0 0 0 12M8.2 13.9 7 23l5-3 5 3-1.2-9.1',
  shield: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10zM9 12l2 2 4-4',
  track: 'M22 12h-4l-3 9L9 3l-3 9H2',
  arrow: 'M5 12h14M13 6l6 6-6 6',
  logout: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9',
};

export function Icon({ name, size = 22 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
         strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={PATHS[name]} />
    </svg>
  );
}

export function AppFooter() {
  return (
    <footer className="app-footer">
      Docket – Digital Campus Administration Portal · Thapar Institute of Engineering &amp; Technology, Patiala
      · UCS503 Group 3C35
    </footer>
  );
}
