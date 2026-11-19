// Small wrapper around fetch for the Spring Boot REST API.
// Spring Security keeps the login in a session cookie and expects the CSRF token
// (from the XSRF-TOKEN cookie) back in the X-XSRF-TOKEN header on every change.

function cookie(name) {
  const match = document.cookie.split('; ').find((c) => c.startsWith(name + '='));
  return match ? decodeURIComponent(match.split('=')[1]) : null;
}

async function csrfToken() {
  if (!cookie('XSRF-TOKEN')) {
    await fetch('/api/auth/csrf', { credentials: 'same-origin' });
  }
  return cookie('XSRF-TOKEN');
}

export async function api(path, { method = 'GET', body, form } = {}) {
  const headers = {};
  if (method !== 'GET') {
    headers['X-XSRF-TOKEN'] = await csrfToken();
  }
  let payload;
  if (form) {
    payload = form; // browser sets the multipart boundary itself
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }

  const res = await fetch('/api' + path, { method, headers, body: payload, credentials: 'same-origin' });
  if (res.status === 401 && !path.startsWith('/auth')) {
    window.dispatchEvent(new Event('docket:session-expired'));
  }
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = null;
  }
  if (!res.ok) {
    const err = new Error(data?.error || `Request failed (${res.status})`);
    err.errors = data?.errors || [];
    err.status = res.status;
    throw err;
  }
  return data;
}
