'use client';
import { useEffect, useRef, useState } from 'react';
import { api, Booking, PayMethod, Payment } from '../lib/api';

const LABEL: Record<PayMethod, string> = { ECOCASH: 'EcoCash', ONEMONEY: 'OneMoney', WEB: 'Card or other (Paynow page)' };

/**
 * Pays one booking through Paynow.
 * EcoCash / OneMoney: Paynow sends a PIN prompt to the phone; we check every 5 seconds for up to 3 minutes.
 * Web: the tenant is sent to Paynow's page and comes back to /dashboard?payment=ID, where the dashboard checks again.
 */
export default function PayPanel({ booking, testMode, onDone, onClose }: {
  booking: Booking; testMode: boolean; onDone: (message: string) => void; onClose: () => void;
}) {
  const [method, setMethod] = useState<PayMethod>('ECOCASH');
  const [phone, setPhone] = useState('');
  const [payment, setPayment] = useState<Payment | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => () => { if (timer.current) clearInterval(timer.current); }, []);

  function watch(p: Payment) {
    if (timer.current) clearInterval(timer.current);
    let tries = 0;
    timer.current = setInterval(async () => {
      tries++;
      try {
        const latest = await api.tenant.checkPayment(p.id);
        setPayment(latest);
        if (latest.status !== 'PENDING' || tries >= 36) {
          if (timer.current) clearInterval(timer.current);
          if (latest.status === 'PAID') onDone(`Payment received (${latest.paynowReference ? 'Paynow ref ' + latest.paynowReference : latest.reference}). Your booking is confirmed.`);
        }
      } catch { /* keep trying quietly; the button below lets the tenant check by hand */ }
    }, 5000);
  }

  async function start() {
    setError(''); setBusy(true);
    try {
      const p = await api.tenant.startPayment(booking.id, method, method === 'WEB' ? undefined : phone);
      setPayment(p);
      if (p.status === 'FAILED') { setError(p.message || 'The payment could not be started.'); return; }
      if (method === 'WEB' && p.redirectUrl) { window.location.href = p.redirectUrl; return; }
      watch(p);
    } catch (e) { setError(e instanceof Error ? e.message : 'The payment could not be started.'); }
    finally { setBusy(false); }
  }

  async function checkNow() {
    if (!payment) return;
    setBusy(true);
    try {
      const latest = await api.tenant.checkPayment(payment.id);
      setPayment(latest);
      if (latest.status === 'PAID') onDone('Payment received. Your booking is confirmed.');
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not check the payment.'); }
    finally { setBusy(false); }
  }

  const waiting = payment?.status === 'PENDING';
  return (
    <div className="pay-panel">
      <div className="pay-head"><b>Pay US$ {booking.amount} for {booking.propertyTitle}</b>
        <button className="flash-x" onClick={onClose} aria-label="Close payment">×</button></div>

      {!waiting && <>
        <div className="pay-methods" role="radiogroup" aria-label="Payment method">
          {(['ECOCASH', 'ONEMONEY', 'WEB'] as PayMethod[]).map(m => (
            <label key={m} className={method === m ? 'pay-method active' : 'pay-method'}>
              <input type="radio" name={`pm${booking.id}`} checked={method === m} onChange={() => setMethod(m)} /> {LABEL[m]}</label>))}
        </div>
        {method !== 'WEB' && <label className="pay-phone">{LABEL[method]} number
          <input className="input" inputMode="tel" value={phone} onChange={e => setPhone(e.target.value)} placeholder="0771234567" /></label>}
        {testMode && <p className="muted small-note">Paynow test mode: use <b>0771111111</b> (paid after ~5 s), <b>0773333333</b> (cancelled) or <b>0774444444</b> (insufficient balance). No real money moves.</p>}
        <button className="btn orange" disabled={busy || (method !== 'WEB' && phone.trim().length < 9)} onClick={start}>
          {busy ? 'Starting…' : method === 'WEB' ? 'Continue to Paynow' : `Send ${LABEL[method]} prompt`}</button>
      </>}

      {waiting && <div className="pay-wait">
        <span className="spinner" aria-hidden="true" />
        <div><b>Check your phone</b> and enter your {LABEL[payment!.method]} PIN to approve US$ {payment!.amount}.
          {payment!.message && <div className="muted">{payment!.message}</div>}
          <div className="muted">Waiting for Paynow… ({payment!.providerStatus ?? 'Sent'})</div></div>
        <button className="btn secondary small" disabled={busy} onClick={checkNow}>Check now</button>
      </div>}

      {payment?.status === 'FAILED' && !error && <div className="auth-error" role="alert">{payment.message || 'The payment did not go through.'} You can try again.</div>}
      {error && <div className="auth-error" role="alert">{error}</div>}
      <p className="muted small-note">Payments are processed by Paynow. DzimbaHub never sees your PIN or card details.</p>
    </div>
  );
}
