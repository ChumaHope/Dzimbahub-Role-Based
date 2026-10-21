'use client';
import Link from 'next/link';
import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Challenge, loginRequest, saveAuth, verifyCode } from '../../lib/auth';

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [challenge, setChallenge] = useState<Challenge | null>(null);
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');
  const [busy, setBusy] = useState(false);

  async function sendCode(e?: FormEvent) {
    e?.preventDefault(); setError(''); setInfo(''); setBusy(true);
    try {
      const c = await loginRequest({ email, password });
      setChallenge(c); setCode('');
      setInfo(`We sent a 6-digit code by ${c.channel === 'WHATSAPP' ? 'WhatsApp' : 'text message'} to ${c.phoneHint}.`);
    } catch (err) { setError(err instanceof Error ? err.message : 'Unable to sign in. Please try again.'); }
    finally { setBusy(false); }
  }

  async function confirm(e: FormEvent) {
    e.preventDefault(); if (!challenge) return; setError(''); setBusy(true);
    try { const result = await verifyCode({ challengeId: challenge.challengeId, code: code.trim() }); saveAuth(result); router.push('/dashboard'); router.refresh(); }
    catch (err) { setError(err instanceof Error ? err.message : 'That code could not be checked.'); }
    finally { setBusy(false); }
  }

  return (
    <main className="auth-page">
      <div className="auth-side">
        <span className="auth-kicker">WELCOME BACK</span><h1>Your next home is closer than you think.</h1>
        <p>Sign in with your password and a one-time code sent to your phone on WhatsApp.</p>
        <div className="auth-feature">⌖ &nbsp; Location-aware property discovery</div>
        <div className="auth-feature">✦ &nbsp; A dashboard built for your role</div>
      </div>
      <section className="auth-panel"><div className="auth-card">
        <span className="pill">{challenge ? 'STEP 2 OF 2' : 'STEP 1 OF 2'}</span>
        <h2>{challenge ? 'Enter your code' : 'Log in'}</h2>
        <p className="muted">{challenge ? (challenge.channel === 'WHATSAPP' ? 'Check your WhatsApp messages. Tap "Copy code" and paste it below.' : 'Check your text messages.') : 'Only accounts approved by an administrator can sign in.'}</p>
        {error && <div className="auth-error" role="alert">{error}</div>}
        {info && !error && <div className="flash ok" role="status"><span>{info}</span></div>}
        {challenge?.devCode && <div className="notice" style={{ margin: '0 0 14px' }}>Development mode: your code is <b>{challenge.devCode}</b></div>}

        {!challenge ? (
          <form className="form auth-form" onSubmit={sendCode}>
            <label>Email address<input className="input" type="email" autoComplete="email" required value={email} onChange={e => setEmail(e.target.value)} placeholder="you@example.com" /></label>
            <label>Password<input className="input" type="password" autoComplete="current-password" required value={password} onChange={e => setPassword(e.target.value)} placeholder="Enter your password" /></label>
            <button className="btn auth-submit" disabled={busy}>{busy ? 'Checking…' : 'Send me a code'}</button>
          </form>
        ) : (
          <form className="form auth-form" onSubmit={confirm}>
            <label>6-digit code<input className="input code-input" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} required value={code} onChange={e => setCode(e.target.value.replace(/\D/g, ''))} placeholder="••••••" autoFocus /></label>
            <button className="btn auth-submit" disabled={busy || code.length !== 6}>{busy ? 'Verifying…' : 'Verify and sign in'}</button>
            <button type="button" className="btn secondary" disabled={busy} onClick={() => sendCode()}>Send a new code</button>
            <button type="button" className="linklike" onClick={() => { setChallenge(null); setError(''); setInfo(''); }}>← Use a different account</button>
          </form>
        )}
        <p className="auth-switch">No account yet? <Link href="/signup">Request one</Link></p>
        <Link className="auth-back" href="/">← Back to home</Link>
      </div></section>
    </main>
  );
}
