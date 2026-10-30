'use client';
import Link from 'next/link';
import { FormEvent, useState } from 'react';
import { api } from '../../lib/api';
import { saveAuth } from '../../lib/auth';
import { useUser } from '../../lib/useUser';
import { Flash, Notice } from '../../components/ui';

export default function AccountPage() {
  const { user, loaded } = useUser();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [flash, setFlash] = useState<Flash>(null);
  const [busy, setBusy] = useState(false);

  if (loaded && !user) return <main className="container section"><div className="empty">Please <Link href="/login">sign in</Link> to manage your account.</div></main>;
  if (!user) return <main className="container section"><div className="empty">Loading…</div></main>;

  async function change(e: FormEvent) {
    e.preventDefault(); setFlash(null);
    if (next.length < 8) { setFlash({ kind: 'error', text: 'The new password must be at least 8 characters.' }); return; }
    if (next !== confirm) { setFlash({ kind: 'error', text: 'The new passwords do not match.' }); return; }
    setBusy(true);
    try {
      const r = await api.changePassword(current, next);
      saveAuth(r);
      setCurrent(''); setNext(''); setConfirm('');
      setFlash({ kind: 'ok', text: 'Password changed. You have been signed out on your other devices.' });
    } catch (err) { setFlash({ kind: 'error', text: err instanceof Error ? err.message : 'Could not change the password.' }); }
    finally { setBusy(false); }
  }

  return (
    <main className="container section">
      <div className="section-head"><div><span className={`role-chip ${user.role.toLowerCase()}`}>{user.role.toLowerCase()}</span>
        <h1>My account</h1><p className="muted">{user.fullName} · {user.email}</p></div>
        <Link className="btn secondary" href="/dashboard">Back to dashboard</Link></div>
      <section className="detail" style={{ maxWidth: 520 }}>
        <h2>Change password</h2>
        <Notice flash={flash} onClose={() => setFlash(null)} />
        <form className="form auth-form" onSubmit={change}>
          <label>Current password<input className="input" type="password" autoComplete="current-password" required value={current} onChange={e => setCurrent(e.target.value)} /></label>
          <label>New password<input className="input" type="password" autoComplete="new-password" minLength={8} required value={next} onChange={e => setNext(e.target.value)} placeholder="At least 8 characters" /></label>
          <label>Confirm new password<input className="input" type="password" autoComplete="new-password" required value={confirm} onChange={e => setConfirm(e.target.value)} /></label>
          <button className="btn" disabled={busy}>{busy ? 'Saving…' : 'Change password'}</button>
        </form>
        <p className="muted" style={{ marginTop: 14 }}>Forgot your current password? Sign out and use <b>Forgot password?</b> on the sign-in page.</p>
      </section>
    </main>
  );
}
