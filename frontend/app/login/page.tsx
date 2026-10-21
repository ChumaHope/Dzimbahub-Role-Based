'use client';
import Link from 'next/link';
import { FormEvent, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import QRCode from 'qrcode';
import { Challenge, loginRequest, saveAuth, sendFallbackCode, verifyCode } from '../../lib/auth';

/** Shows the secret in groups of four so it is easier to type into the app by hand. */
const grouped = (s: string) => s.replace(/(.{4})/g, '$1 ').trim();

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [challenge, setChallenge] = useState<Challenge | null>(null);
  const [code, setCode] = useState('');
  const [qr, setQr] = useState('');
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');
  const [busy, setBusy] = useState(false);

  // Draw the QR code for the first-time authenticator setup.
  useEffect(() => {
    setQr('');
    if (challenge?.method === 'TOTP_SETUP' && challenge.otpauthUri)
      QRCode.toDataURL(challenge.otpauthUri, { width: 220, margin: 1 }).then(setQr).catch(() => setQr(''));
  }, [challenge]);

  function describe(c: Challenge) {
    if (c.method === 'TOTP') return 'Open your authenticator app and enter the 6-digit code shown for DzimbaHub.';
    if (c.method === 'TOTP_SETUP') return '';
    return `We sent a 6-digit code by ${c.channel === 'WHATSAPP' ? 'WhatsApp' : 'text message'} to ${c.phoneHint ?? 'your phone'}.`;
  }

  async function start(e?: FormEvent) {
    e?.preventDefault(); setError(''); setInfo(''); setBusy(true);
    try {
      const c = await loginRequest({ email, password });
      setChallenge(c); setCode(''); setInfo(describe(c));
    } catch (err) { setError(err instanceof Error ? err.message : 'Unable to sign in. Please try again.'); }
    finally { setBusy(false); }
  }

  async function fallback() {
    if (!challenge) return;
    setError(''); setBusy(true);
    try {
      const c = await sendFallbackCode(challenge.challengeId);
      setChallenge(c); setCode(''); setInfo(describe(c));
    } catch (err) { setError(err instanceof Error ? err.message : 'Could not send a code.'); }
    finally { setBusy(false); }
  }

  async function confirm(e: FormEvent) {
    e.preventDefault(); if (!challenge) return; setError(''); setBusy(true);
    try { const result = await verifyCode({ challengeId: challenge.challengeId, code: code.trim() }); saveAuth(result); router.push('/dashboard'); router.refresh(); }
    catch (err) { setError(err instanceof Error ? err.message : 'That code could not be checked.'); }
    finally { setBusy(false); }
  }

  const m = challenge?.method;
  const heading = !challenge ? 'Log in' : m === 'TOTP_SETUP' ? 'Set up Google Authenticator' : m === 'TOTP' ? 'Enter your app code' : 'Enter your code';
  const sub = !challenge ? 'Only accounts approved by an administrator can sign in.'
    : m === 'TOTP_SETUP' ? 'One-time setup. After this you will sign in with your password and the code from the app.'
    : m === 'TOTP' ? 'The code changes every 30 seconds.'
    : challenge.channel === 'WHATSAPP' ? 'Check your WhatsApp messages. Tap "Copy code" and paste it below.' : 'Check your messages.';

  return (
    <main className="auth-page">
      <div className="auth-side">
        <span className="auth-kicker">WELCOME BACK</span><h1>Your next home is closer than you think.</h1>
        <p>Sign in with your password and a 6-digit code from your authenticator app.</p>
        <div className="auth-feature">⌖ &nbsp; Location-aware property discovery</div>
        <div className="auth-feature">✦ &nbsp; A dashboard built for your role</div>
        <div className="auth-feature">🔒 &nbsp; Protected with Google Authenticator</div>
      </div>
      <section className="auth-panel"><div className="auth-card">
        <span className="pill">{challenge ? 'STEP 2 OF 2' : 'STEP 1 OF 2'}</span>
        <h2>{heading}</h2>
        <p className="muted">{sub}</p>
        {error && <div className="auth-error" role="alert">{error}</div>}
        {info && !error && <div className="flash ok" role="status"><span>{info}</span></div>}
        {challenge?.devCode && <div className="notice" style={{ margin: '0 0 14px' }}>Development mode: your code is <b>{challenge.devCode}</b></div>}

        {!challenge ? (
          <form className="form auth-form" onSubmit={start}>
            <label>Email address<input className="input" type="email" autoComplete="email" required value={email} onChange={e => setEmail(e.target.value)} placeholder="you@example.com" /></label>
            <label>Password<input className="input" type="password" autoComplete="current-password" required value={password} onChange={e => setPassword(e.target.value)} placeholder="Enter your password" /></label>
            <button className="btn auth-submit" disabled={busy}>{busy ? 'Checking…' : 'Continue'}</button>
          </form>
        ) : (
          <form className="form auth-form" onSubmit={confirm}>
            {m === 'TOTP_SETUP' && (
              <ol className="totp-steps">
                <li>Install <b>Google Authenticator</b> (or Microsoft Authenticator) from the Play Store or App Store.</li>
                <li>In the app tap <b>+</b> then <b>Scan a QR code</b>, and scan this:
                  <div className="totp-qr">{qr ? <img src={qr} alt="QR code to add DzimbaHub to your authenticator app" width={220} height={220} /> : <span className="muted">Preparing QR code…</span>}</div>
                  {challenge.secret && <details className="totp-manual"><summary>Can&apos;t scan? Enter a setup key instead</summary>
                    <p className="muted">Choose <b>Enter a setup key</b>. Account: <b>{challenge.account}</b>. Key (time-based):</p>
                    <code className="totp-key">{grouped(challenge.secret)}</code></details>}
                </li>
                <li>Type the 6-digit code the app now shows for <b>DzimbaHub</b>.</li>
              </ol>
            )}
            <label>6-digit code<input className="input code-input" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} required value={code} onChange={e => setCode(e.target.value.replace(/\D/g, ''))} placeholder="••••••" autoFocus /></label>
            <button className="btn auth-submit" disabled={busy || code.length !== 6}>{busy ? 'Verifying…' : m === 'TOTP_SETUP' ? 'Finish setup and sign in' : 'Verify and sign in'}</button>
            {m !== 'CODE' && challenge.allowFallback && (
              <button type="button" className="btn secondary" disabled={busy} onClick={fallback}>
                {m === 'TOTP_SETUP' ? 'Skip for now – get a code another way' : "Can't use your app? Get a code another way"}</button>)}
            {m === 'CODE' && <button type="button" className="btn secondary" disabled={busy} onClick={() => start()}>Start again</button>}
            <button type="button" className="linklike" onClick={() => { setChallenge(null); setError(''); setInfo(''); }}>← Use a different account</button>
          </form>
        )}
        <p className="auth-switch">No account yet? <Link href="/signup">Request one</Link></p>
        <Link className="auth-back" href="/">← Back to home</Link>
      </div></section>
    </main>
  );
}
