'use client';
import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { api, Booking, Msg, PaymentsConfig, Preferences, Viewing } from '../../lib/api';
import { AuthUser } from '../../lib/auth';
import MessageThread from '../MessageThread';
import PayPanel from '../PayPanel';
import { ActionButton, Badge, Empty, Flash, Notice, Stat, fmtDate, run } from '../ui';

export default function TenantDashboard({ user }: { user: AuthUser }) {
  const [viewings, setViewings] = useState<Viewing[]>([]);
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [messages, setMessages] = useState<Msg[]>([]);
  const [prefs, setPrefs] = useState<Preferences>({ budget: 0, area: '', roomType: '', furnished: false });
  const [flash, setFlash] = useState<Flash>(null);
  const [loading, setLoading] = useState(true);
  const [payConfig, setPayConfig] = useState<PaymentsConfig>({ provider: 'SIMULATED', methods: [], testMode: false });
  const [paying, setPaying] = useState<number | null>(null);

  const load = useCallback(async () => {
    try {
      const [v, b, m, p] = await Promise.all([api.tenant.viewings(), api.tenant.bookings(), api.tenant.messages(), api.tenant.preferences()]);
      setViewings(v); setBookings(b); setMessages(m); setPrefs(p);
    } catch (e) { setFlash({ kind: 'error', text: e instanceof Error ? e.message : 'Could not load your dashboard.' }); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { load(); api.paymentsConfig().then(setPayConfig).catch(() => {}); }, [load]);

  // Back from Paynow's web page (/dashboard?payment=ID): ask for the result, then tidy the address bar.
  useEffect(() => {
    const id = Number(new URLSearchParams(window.location.search).get('payment'));
    if (!id) return;
    window.history.replaceState(null, '', window.location.pathname);
    (async () => {
      try {
        const p = await api.tenant.checkPayment(id);
        setFlash(p.status === 'PAID' ? { kind: 'ok', text: 'Payment received. Your booking is confirmed.' }
          : p.status === 'FAILED' ? { kind: 'error', text: p.message || 'The payment did not go through. You can try again.' }
          : { kind: 'ok', text: 'Paynow is still processing your payment. Refresh in a minute to see the result.' });
        await load();
      } catch (e) { setFlash({ kind: 'error', text: e instanceof Error ? e.message : 'Could not check the payment.' }); }
    })();
  }, [load]);

  const pending = viewings.filter(v => v.status === 'PENDING').length;
  const approved = viewings.filter(v => v.status === 'APPROVED').length;
  const unpaid = bookings.filter(b => b.status === 'PENDING').length;

  return (
    <main className="container section">
      <div className="section-head"><div><span className="role-chip tenant">tenant</span><h1>Welcome, {user.fullName.split(' ')[0]}</h1>
        <p className="muted">Find a place, ask questions, arrange viewings and confirm your booking.</p></div>
        <Link className="btn" href="/properties">Find houses</Link></div>
      <Notice flash={flash} onClose={() => setFlash(null)} />
      <div className="dashboard">
        <Stat label="Viewing requests waiting" value={loading ? '…' : pending} />
        <Stat label="Approved viewings" value={loading ? '…' : approved} tone="good" />
        <Stat label="Bookings to pay" value={loading ? '…' : unpaid} tone={unpaid ? 'warn' : undefined} />
        <Stat label="Messages" value={loading ? '…' : messages.length} />
      </div>

      <section className="section"><h2>My viewing requests</h2>
        {!viewings.length ? <Empty>No viewing requests yet. Open a property and choose “Request viewing”.</Empty> : (
          <div className="table-wrap"><table className="table"><thead><tr><th>Property</th><th>Date and time</th><th>Status</th><th></th></tr></thead><tbody>
            {viewings.map(v => (
              <tr key={v.id}><td><Link href={`/properties/${v.propertyId}`}>{v.propertyTitle}</Link></td><td>{v.date} at {v.time.slice(0, 5)}</td><td><Badge status={v.status} /></td>
                <td className="actions">
                  {v.status === 'APPROVED' && <Link className="btn secondary small" href={`/properties/${v.propertyId}`}>Book or rate</Link>}
                  {(v.status === 'PENDING' || v.status === 'APPROVED') && <ActionButton className="secondary small" busyLabel="Cancelling…"
                    onClick={async () => { if (await run(() => api.tenant.cancelViewing(v.id), setFlash, 'Viewing request cancelled.')) await load(); }}>Cancel</ActionButton>}
                </td></tr>))}
          </tbody></table></div>)}
      </section>

      <section className="section"><h2>My bookings</h2>
        {!bookings.length ? <Empty>No bookings yet. After a landlord approves a viewing you can book from the property page.</Empty> : (
          <div className="table-wrap"><table className="table"><thead><tr><th>Property</th><th>Amount</th><th>Status</th><th>Reference</th><th></th></tr></thead><tbody>
            {bookings.map(b => (
              <tr key={b.id}><td>{b.propertyTitle}</td><td>US$ {b.amount}</td><td><Badge status={b.status} /></td><td>{b.paymentRef ?? '—'}</td>
                <td className="actions">{b.status === 'PENDING' && (payConfig.provider === 'PAYNOW'
                  ? <button className="btn orange small" onClick={() => setPaying(paying === b.id ? null : b.id)}>{paying === b.id ? 'Close' : 'Pay now'}</button>
                  : <ActionButton className="orange small" busyLabel="Processing…"
                    onClick={async () => { if (await run(() => api.tenant.pay(b.id), setFlash, 'Booking confirmed. This was a simulated payment; no money was charged.')) await load(); }}>Confirm (simulated payment)</ActionButton>)}</td></tr>))}
          </tbody></table></div>)}
        {paying != null && bookings.some(b => b.id === paying && b.status === 'PENDING') &&
          <PayPanel key={paying} booking={bookings.find(b => b.id === paying)!} testMode={payConfig.testMode}
            onClose={() => setPaying(null)}
            onDone={async text => { setFlash({ kind: 'ok', text }); setPaying(null); await load(); }} />}
        <p className="muted">{payConfig.provider === 'PAYNOW' ? 'Payments are processed securely by Paynow (EcoCash, OneMoney, cards).' : 'Payments in this system are simulated for demonstration. No real money moves.'}</p>
      </section>

      <section className="section two-col">
        <div className="detail"><h2>Messages with landlords</h2><MessageThread messages={messages} reply={api.tenant.reply} onChanged={load} /></div>
        <div className="detail"><h2>My saved preferences</h2>
          <p className="muted">Used to pre-fill your recommendations.</p>
          <div className="form">
            <label>Monthly budget (US$)<input className="input" type="number" min={0} value={prefs.budget || ''} onChange={e => setPrefs({ ...prefs, budget: Number(e.target.value) })} /></label>
            <label>Preferred area<input className="input" value={prefs.area} onChange={e => setPrefs({ ...prefs, area: e.target.value })} placeholder="e.g. Avondale" /></label>
            <label>Room type<select className="select" value={prefs.roomType} onChange={e => setPrefs({ ...prefs, roomType: e.target.value })}><option value="">Any</option><option>Single</option><option>Shared</option><option>Self-contained</option></select></label>
            <label className="check"><input type="checkbox" checked={prefs.furnished} onChange={e => setPrefs({ ...prefs, furnished: e.target.checked })} /> Furnished</label>
            <div className="row"><ActionButton busyLabel="Saving…" onClick={async () => { await run(() => api.tenant.savePreferences(prefs), setFlash, 'Preferences saved.'); }}>Save preferences</ActionButton>
              <Link className="btn orange" href="/recommendations">See recommendations</Link></div>
          </div></div>
      </section>
      <p className="muted">Signed in as {user.email} · last refreshed {fmtDate(new Date().toISOString())}</p>
    </main>
  );
}
