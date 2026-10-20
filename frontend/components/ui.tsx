'use client';
import { ReactNode, useState } from 'react';

export type Flash = { kind: 'ok' | 'error'; text: string } | null;

/** Runs an action and reports the outcome in a Flash message. Returns true on success. */
export async function run(fn: () => Promise<unknown>, setFlash: (f: Flash) => void, okText: string): Promise<boolean> {
  try { await fn(); setFlash({ kind: 'ok', text: okText }); return true; }
  catch (e) { setFlash({ kind: 'error', text: e instanceof Error ? e.message : 'Something went wrong. Please try again.' }); return false; }
}

export function Notice({ flash, onClose }: { flash: Flash; onClose?: () => void }) {
  if (!flash) return null;
  return (
    <div className={`flash ${flash.kind}`} role={flash.kind === 'error' ? 'alert' : 'status'}>
      <span>{flash.kind === 'ok' ? '✓ ' : '⚠ '}{flash.text}</span>
      {onClose && <button className="flash-x" onClick={onClose} aria-label="Dismiss message">×</button>}
    </div>
  );
}

/** A button that shows progress while its action runs and cannot be clicked twice. */
export function ActionButton({ onClick, children, busyLabel, className = '', disabled = false, type = 'button' }: {
  onClick: () => Promise<unknown> | void; children: ReactNode; busyLabel?: string; className?: string; disabled?: boolean; type?: 'button' | 'submit';
}) {
  const [busy, setBusy] = useState(false);
  async function handle() {
    if (busy) return;
    setBusy(true);
    try { await onClick(); } finally { setBusy(false); }
  }
  return (
    <button type={type} className={`btn ${className}`} disabled={busy || disabled} aria-busy={busy} onClick={handle}>
      {busy ? <><span className="spinner" aria-hidden="true" /> {busyLabel ?? 'Working…'}</> : children}
    </button>
  );
}

export function Stat({ label, value, tone }: { label: string; value: ReactNode; tone?: 'warn' | 'good' }) {
  return <div className={`stat ${tone ?? ''}`}><span className="muted">{label}</span><br /><b>{value}</b></div>;
}

export function Badge({ status }: { status: string }) {
  return <span className={`badge ${status.toLowerCase()}`}>{status.toLowerCase()}</span>;
}

export function Empty({ children }: { children: ReactNode }) { return <div className="empty small">{children}</div>; }

export function fmtDate(iso: string) { try { return new Date(iso).toLocaleString(); } catch { return iso; } }
export function Stars({ value }: { value: number }) { return <span aria-label={`${value} out of 5`}>{'★'.repeat(Math.round(value))}{'☆'.repeat(5 - Math.round(value))}</span>; }
