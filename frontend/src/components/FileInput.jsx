import { ALLOWED_TYPES, MAX_UPLOAD_MB } from '../labels.js';

// File picker that checks type and size in the browser before upload (QR4).
// The server checks again, so this is only for a faster message.
export default function FileInput({ doc, file, onChange, optionalHint }) {
  const pick = (e) => {
    const f = e.target.files[0];
    if (!f) return onChange(null, null);
    if (!ALLOWED_TYPES.includes(f.type)) {
      e.target.value = '';
      return onChange(null, `${doc.documentName}: only PDF, JPEG or PNG files are allowed.`);
    }
    if (f.size > MAX_UPLOAD_MB * 1024 * 1024) {
      e.target.value = '';
      return onChange(null, `${doc.documentName}: the file is larger than ${MAX_UPLOAD_MB} MB.`);
    }
    onChange(f, null);
  };

  return (
    <label className="file-row">
      <span className="file-label">
        {doc.documentName}
        {doc.mandatory && !optionalHint
          ? <span className="req" aria-label="required">*</span>
          : <span className="muted small"> (optional)</span>}
      </span>
      <input type="file" accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png" onChange={pick} />
      {file && <span className="muted small">{file.name} · {(file.size / 1024).toFixed(0)} KB</span>}
    </label>
  );
}
