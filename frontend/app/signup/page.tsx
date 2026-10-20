'use client';
import Link from 'next/link';
import { FormEvent, useState } from 'react';
import { signupRequest } from '../../lib/auth';

export default function SignupPage() {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [role, setRole] = useState<'TENANT' | 'LANDLORD'>('TENANT');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [done, setDone] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault(); setError('');
    if (password.length < 8) { setError('Use a password with at least 8 characters.'); return; }
    if (password !== confirm) { setError('The passwords do not match.'); return; }
    if (!/^\+?[0-9]{9,15}$/.test(phone.trim())) { setError('Enter a phone number such as +263771234567. Sign-in codes are sent to it by text message.'); return; }
    setBusy(true);
    try { const r = await signupRequest({ fullName, email, phone: phone.trim(), password, role }); setDone(r.message); }
    catch (err) { setError(err instanceof Error ? err.message : 'Unable to send your request. Please try again.'); }
    finally { setBusy(false); }
  }

  if (done) return (
    <main className="auth-page">
      <div className="auth-side"><span className="auth-kicker">REQUEST RECEIVED</span><h1>Thank you. We will review your request.</h1><p>For the safety of tenants and landlords, every account is approved by an administrator before it can be used.</p></div>
      <section className="auth-panel"><div className="auth-card">
        <span className="pill">PENDING APPROVAL</span><h2>Request sent</h2>
        <div className="flash ok" role="status"><span>✓ {done}</span></div>
        <p className="muted">When you are approved, sign in with your email and password, then enter the 6-digit code we text to {phone}.</p>
        <Link className="btn auth-submit" style={{ display: 'block', textAlign: 'center', marginTop: 16 }} href="/login">Go to sign in</Link>
      </div></section>
    </main>
  );

  return (
    <main className="auth-page">
      <div className="auth-side">
        <span className="auth-kicker">FIND YOUR PLACE</span><h1>Make your next move with confidence.</h1>
        <p>Request an account as a tenant or a landlord. An administrator approves each request before sign-in is allowed.</p>
        <div className="auth-feature">✓ &nbsp; Tenants: search, enquire, book viewings and rate properties</div>
        <div className="auth-feature">✓ &nbsp; Landlords: list properties and answer requests</div>
      </div>
      <section className="auth-panel"><div className="auth-card">
        <span className="pill">JOIN DZIMBAHUB</span><h2>Request an account</h2><p className="muted">It takes about a minute.</p>
        {error && <div className="auth-error" role="alert">{error}</div>}
        <form className="form auth-form" onSubmit={submit}>
          <label>Full name<input className="input" required minLength={2} maxLength={100} value={fullName} onChange={e => setFullName(e.target.value)} placeholder="Your full name" autoComplete="name" /></label>
          <label>Email address<input className="input" type="email" required value={email} onChange={e => setEmail(e.target.value)} placeholder="you@example.com" autoComplete="email" /></label>
          <label>Mobile phone (for sign-in codes)<input className="input" type="tel" required value={phone} onChange={e => setPhone(e.target.value)} placeholder="+263771234567" autoComplete="tel" /></label>
          <label>I am joining as
            <select className="select" value={role} onChange={e => setRole(e.target.value as 'TENANT' | 'LANDLORD')}>
              <option value="TENANT">Tenant: looking for accommodation</option>
              <option value="LANDLORD">Landlord: managing properties</option>
            </select>
          </label>
          <label>Password<input className="input" type="password" required minLength={8} maxLength={72} value={password} onChange={e => setPassword(e.target.value)} placeholder="At least 8 characters" autoComplete="new-password" /></label>
          <label>Confirm password<input className="input" type="password" required value={confirm} onChange={e => setConfirm(e.target.value)} placeholder="Re-enter your password" autoComplete="new-password" /></label>
          <button className="btn auth-submit" disabled={busy}>{busy ? 'Sending request…' : 'Send request for approval'}</button>
        </form>
        <p className="auth-switch">Already approved? <Link href="/login">Log in</Link></p>
        <Link className="auth-back" href="/">← Back to home</Link>
      </div></section>
    </main>
  );
}
