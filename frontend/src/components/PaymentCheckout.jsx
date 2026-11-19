import { useEffect, useState } from 'react';
import QRCode from 'qrcode';
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

// Pays the application fee, either by UPI (QR code / UPI ID, then the student enters the UTR) or
// through the card/netbanking gateway (Razorpay when configured, otherwise Docket's sandbox).
export default function PaymentCheckout({ applicationId, onPaid, onClose }) {
  const [order, setOrder] = useState(null);
  const [tab, setTab] = useState('upi');
  const [qr, setQr] = useState(null);
  const [utr, setUtr] = useState('');
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api(`/student/applications/${applicationId}/payment/order`, { method: 'POST' }).then(setOrder).catch(setError);
  }, [applicationId]);

  const amount = order ? (order.amountPaise / 100).toFixed(2) : '';
  // Standard UPI payment link; every UPI app understands it when scanned or tapped
  const upiLink = order && `upi://pay?${new URLSearchParams({
    pa: order.upiId, pn: order.upiName, am: amount, cu: 'INR', tn: `Docket ${order.referenceNumber}`,
  })}`;

  useEffect(() => {
    if (upiLink) QRCode.toDataURL(upiLink, { width: 220, margin: 1 }).then(setQr);
  }, [upiLink]);

  const run = async (fn) => {
    setBusy(true);
    setError(null);
    try {
      onPaid(await fn());
    } catch (e) {
      setError(e);
      setBusy(false);
    }
  };

  const payUpi = (e) => {
    e.preventDefault();
    run(() => api(`/student/applications/${applicationId}/payment/upi`, { method: 'POST', body: { utr } }));
  };

  const confirm = (orderId, paymentId, signature) => api(`/student/applications/${applicationId}/payment/confirm`,
    { method: 'POST', body: { orderId, paymentId, signature } });

  const payGateway = () => run(async () => {
    if (order.gateway === 'SANDBOX') {
      const p = await api(`/student/payments/sandbox/${order.orderId}/pay`, { method: 'POST' });
      return confirm(order.orderId, p.paymentId, p.signature);
    }
    await loadRazorpay();
    return new Promise((resolve, reject) => {
      new window.Razorpay({
        key: order.keyId, amount: order.amountPaise, currency: order.currency, order_id: order.orderId,
        name: 'Docket – TIET Patiala', description: `${order.serviceName} fee · ${order.referenceNumber}`,
        prefill: { name: order.name, email: order.email, contact: order.phone || '' },
        theme: { color: '#930002' },
        handler: (r) => confirm(r.razorpay_order_id, r.razorpay_payment_id, r.razorpay_signature).then(resolve, reject),
        modal: { ondismiss: () => reject(new Error('Payment window closed. Nothing was charged.')) },
      }).open();
    });
  });

  const copy = () => navigator.clipboard?.writeText(order.upiId).then(() => setCopied(true)).catch(() => {});

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="pay-title">
      <div className="modal">
        <div className="pay-head">
          <h2 id="pay-title">Pay application fee</h2>
          {order && <span className="pay-amount">{rupees(amount)}</span>}
        </div>
        {order && <p className="muted small">{order.serviceName} · {order.referenceNumber} · {order.name}</p>}
        <ErrorBox error={error} />
        {!order && !error && <p className="muted">Preparing your payment…</p>}

        {order && (
          <>
            <div className="pay-tabs" role="tablist">
              <button role="tab" aria-selected={tab === 'upi'} className={tab === 'upi' ? 'on' : ''}
                      onClick={() => setTab('upi')}>UPI (QR / UPI ID)</button>
              <button role="tab" aria-selected={tab === 'gateway'} className={tab === 'gateway' ? 'on' : ''}
                      onClick={() => setTab('gateway')}>Card / Netbanking</button>
            </div>

            {tab === 'upi' ? (
              <form onSubmit={payUpi}>
                {order.upiDemo && (
                  <p className="pay-warning">Demo UPI ID: UPI apps will refuse it, so no money can be sent. Set the
                    institute's UPI ID (DOCKET_UPI_ID) before real use.</p>
                )}
                <div className="upi-box">
                  {qr && <img src={qr} alt={`UPI QR code to pay ${rupees(amount)} to ${order.upiId}`} width="180" height="180" />}
                  <div>
                    <ol className="upi-steps">
                      <li>Scan with any UPI app (GPay, PhonePe, Paytm, BHIM) or pay to the UPI ID below.</li>
                      <li>Pay exactly <strong>{rupees(amount)}</strong>.</li>
                      <li>Enter the 12-digit UTR / reference number from your UPI app.</li>
                    </ol>
                    <div className="upi-id">
                      <code>{order.upiId}</code>
                      <button type="button" className="btn btn-small" onClick={copy}>{copied ? 'Copied' : 'Copy'}</button>
                    </div>
                    <a className="btn btn-small btn-outline upi-open" href={upiLink}>Open UPI app (on phone)</a>
                  </div>
                </div>
                <label className="field">
                  <span>UTR / UPI reference number<span className="req">*</span></span>
                  <input inputMode="numeric" pattern="\d{12}" maxLength={12} required value={utr}
                         onChange={(e) => setUtr(e.target.value.replace(/\D/g, ''))} placeholder="e.g. 432198765012" />
                </label>
                <p className="muted small">The Finance Officer checks this UTR against the bank statement before your
                  application is finally approved. A wrong UTR gets the application rejected.</p>
                <div className="form-actions">
                  <button type="button" className="btn btn-outline" onClick={onClose} disabled={busy}>Pay later</button>
                  <button className="btn btn-primary" disabled={busy || utr.length !== 12}>
                    {busy ? 'Submitting…' : 'I have paid, submit'}
                  </button>
                </div>
              </form>
            ) : (
              <>
                <p className="small">
                  {order.gateway === 'SANDBOX'
                    ? 'Test gateway (sandbox): no real money is charged and no card details are asked for.'
                    : 'You will be taken to Razorpay to pay by card, netbanking or wallet.'}
                </p>
                <div className="form-actions">
                  <button className="btn btn-outline" onClick={onClose} disabled={busy}>Pay later</button>
                  <button className="btn btn-primary" onClick={payGateway} disabled={busy}>
                    {busy ? 'Processing…' : `Pay ${rupees(amount)}`}
                  </button>
                </div>
              </>
            )}
          </>
        )}
        {!order && error && (
          <div className="form-actions"><button className="btn btn-outline" onClick={onClose}>Close</button></div>
        )}
      </div>
    </div>
  );
}
