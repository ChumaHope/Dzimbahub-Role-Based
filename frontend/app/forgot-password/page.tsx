'use client';
import Link from 'next/link';
import { FormEvent, useState } from 'react';
import { Challenge, forgotPassword, resetPassword, sendFallbackCode } from '../../lib/auth';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [challenge, setChallenge] = useState<Challenge | null>(null);
  const [code, setCode] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [done, setDone] = useState('');
  const [busy, setBusy] = useState(false);

  const hint = (c: Challenge) => c.method === 'TOTP'
    ? 'Open your authenticator app and type the 6-digit code shown for DzimbaHub.'
    : `If the email belongs to an approved account, a 6-digit code was sent${c.channel === 'WHATSAPP' ? ' by WhatsApp' : ''} to ${c.phoneHint ?? 'your phone'}.`;

  async function start(e: FormEvent) {
    e.preventDefault(); setError(''); setBusy(true);
    try { setChallenge(await forgotPassword(email.trim())); setCode(''); }
    catch (err) { setError(err instanceof Error ? err.message : 'Could not start the reset.'); }
    finally { setBusy(false); }
  }

  async function other() {
    if (!challenge) return;
    setError(''); setBusy(true);
    try { setChallenge(await sendFallbackCode(challenge.challengeId)); setCode(''); }
    catch (err) { setError(err instanceof Error ? err.message : 'Could not send a code.'); }
    finally { setBusy(false); }
  }

  async function finish(e: FormEvent) {
    e.preventDefault(); if (!challenge) return; setError('');
    if (password.length < 8) { setError('The new password must be at least 8 characters.'); return; }
    if (password !== confirm) { setError('The passwords do not match.'); return; }
    setBusy(true);
    try { setDone((await resetPassword({ challengeId: challenge.challengeId, code: code.trim(), newPassword: password })).message); }
    catch (err) { setError(err instanceof Error ? err.message : 'Could not change the password.'); }
    finally { setBusy(false); }
  }

  return (
    <main className="auth-page">
      <div className="auth-side">
        <span className="auth-kicker">ACCOUNT HELP</span><h1>Forgot your password?</h1>
        <p>Prove it is you with a 6-digit code, then choose a new password. Your other sessions will be signed out.</p>
        <div className="auth-feature">🔒 &nbsp; Uses your authenticator app when you have one</div>
      </div>
      <section className="auth-panel"><div className="auth-card">
        <span className="pill">{done ? 'DONE' : challenge ? 'STEP 2 OF 2' : 'STEP 1 OF 2'}</span>
        <h2>{done ? 'Password changed' : 'Reset your password'}</h2>
        {error && <div className="auth-error" role="alert">{error}</div>}
        {done ? (<>
          <div className="flash ok" role="status"><span>✓ {done}</span></div>
          <Link className="btn auth-submit" style={{ display: 'block', textAlign: 'center', marginTop: 16 }} href="/login">Go to sign in</Link>
        </>) : !challenge ? (
          <form className="form auth-form" onSubmit={start}>
            <p className="muted">Enter the email address you use to sign in.</p>
            <label>Email address<input className="input" type="email" autoComplete="email" required value={email} onChange={e => setEmail(e.target.value)} placeholder="you@example.com" /></label>
            <button className="btn auth-submit" disabled={busy}>{busy ? 'Checking…' : 'Continue'}</button>
          </form>
        ) : (
          <form className="form auth-form" onSubmit={finish}>
            <div className="flash ok" role="status"><span>{hint(challenge)}</span></div>
            {challenge.devCode && <div className="notice">Development mode: your code is <b>{challenge.devCode}</b></div>}
            <label>6-digit code<input className="input code-input" inputMode="numeric" autoComplete="one-time-code" maxLength={6} required value={code} onChange={e => setCode(e.target.value.replace(/\D/g, ''))} placeholder="••••••" autoFocus /></label>
            <label>New password<input className="input" type="password" autoComplete="new-password" minLength={8} required value={password} onChange={e => setPassword(e.target.value)} placeholder="At least 8 characters" /></label>
            <label>Confirm new password<input className="input" type="password" autoComplete="new-password" required value={confirm} onChange={e => setConfirm(e.target.value)} /></label>
            <button className="btn auth-submit" disabled={busy || code.length !== 6}>{busy ? 'Saving…' : 'Change password'}</button>
            {challenge.method === 'TOTP' && challenge.allowFallback &&
              <button type="button" className="btn secondary" disabled={busy} onClick={other}>Can&apos;t use your app? Get a code another way</button>}
            <button type="button" className="linklike" onClick={() => { setChallenge(null); setError(''); }}>← Start again</button>
          </form>
        )}
        <Link className="auth-back" href="/login">← Back to sign in</Link>
      </div></section>
    </main>
  );
}
