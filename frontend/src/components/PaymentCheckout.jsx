import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { rupees } from '../labels.js';
import { ErrorBox } from './ui.jsx';

const RAZORPAY_SCRIPT = 'https://checkout.razorpay.com/v1/checkout.js';

function loadRazorpay() {
  if (window.Razorpay) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = RAZORPAY_SCRIPT;
    s.onload = resolve;
    s.onerror = () => reject(new Error('Could not load Razorpay Checkout. Check your internet connection.'));
    document.body.appendChild(s);
  });
}

// Pays the application fee. With Razorpay keys configured on the server this opens Razorpay
// Checkout; otherwise Docket's sandbox gateway (test mode) is shown. Either way the server
// verifies the gateway's signature before the application is submitted.
export default function PaymentCheckout({ applicationId, onPaid, onClose }) {
  const [order, setOrder] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [method, setMethod] = useState('UPI');

  const confirm = async (orderId, paymentId, signature) => {
    const summary = await api(`/student/applications/${applicationId}/payment/confirm`,
      { method: 'POST', body: { orderId, paymentId, signature } });
    onPaid(summary);
  };

  useEffect(() => {
    let cancelled = false;
    api(`/student/applications/${applicationId}/payment/order`, { method: 'POST' })
      .then(async (o) => {
        if (cancelled) return;
        setOrder(o);
        if (o.gateway === 'RAZORPAY') {
          await loadRazorpay();
          const rzp = new window.Razorpay({
            key: o.keyId,
            amount: o.amountPaise,
            currency: o.currency,
            order_id: o.orderId,
            name: 'Docket – TIET Patiala',
            description: `${o.serviceName} fee · ${o.referenceNumber}`,
            prefill: { name: o.name, email: o.email, contact: o.phone || '' },
            theme: { color: '#0b3d91' },
            handler: (r) => confirm(r.razorpay_order_id, r.razorpay_payment_id, r.razorpay_signature)
              .catch(setError),
            modal: { ondismiss: onClose },
          });
          rzp.open();
        }
      })
      .catch((e) => !cancelled && setError(e));
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applicationId]);

  const paySandbox = async () => {
    setBusy(true);
    setError(null);
    try {
      const p = await api(`/student/payments/sandbox/${order.orderId}/pay`, { method: 'POST' });
      await confirm(order.orderId, p.paymentId, p.signature);
    } catch (e) {
      setError(e);
      setBusy(false);
    }
  };

  // Razorpay shows its own window; only errors are shown here
  if (order?.gateway === 'RAZORPAY' && !error) return null;

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="pay-title">
      <div className="modal">
        <div className="pay-head">
          <h2 id="pay-title">Docket Pay</h2>
          <span className="badge status-awaiting_correction">Sandbox · Test mode</span>
        </div>
        <ErrorBox error={error} />
        {!order && !error && <p className="muted">Creating your payment order…</p>}
        {order && (
          <>
            <dl className="facts single pay-summary">
              <div><dt>Application</dt><dd>{order.referenceNumber} · {order.serviceName}</dd></div>
              <div><dt>Paid by</dt><dd>{order.name} ({order.email})</dd></div>
              <div><dt>Order ID</dt><dd><code>{order.orderId}</code></dd></div>
              <div><dt>Amount payable</dt><dd className="pay-amount">{rupees(order.amountPaise / 100)}</dd></div>
            </dl>
            <fieldset className="pay-methods">
              <legend>Payment method</legend>
              {['UPI', 'Debit / Credit card', 'Net banking'].map((m) => (
                <label key={m} className={`pay-method ${method === m ? 'on' : ''}`}>
                  <input type="radio" name="method" checked={method === m} onChange={() => setMethod(m)} />
                  {m}
                </label>
              ))}
            </fieldset>
            <p className="small muted">This is a test gateway: no real money is charged and no card or UPI details
              are asked for. Do not enter real payment details anywhere in this test.</p>
          </>
        )}
        <div className="form-actions">
          <button className="btn btn-outline" onClick={onClose} disabled={busy}>Cancel</button>
          {order && (
            <button className="btn btn-primary" onClick={paySandbox} disabled={busy}>
              {busy ? 'Processing…' : `Pay ${rupees(order.amountPaise / 100)}`}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
