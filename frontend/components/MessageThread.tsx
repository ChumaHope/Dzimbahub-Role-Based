'use client';
import { useState } from 'react';
import { Msg } from '../lib/api';
import { ActionButton, Empty, Flash, Notice, fmtDate, run } from './ui';

/** Shows messages newest first and lets the user reply to messages that were sent to them. */
export default function MessageThread({ messages, reply, onChanged }: {
  messages: Msg[]; reply: (id: number, text: string) => Promise<unknown>; onChanged: () => Promise<unknown> | void;
}) {
  const [open, setOpen] = useState<number | null>(null);
  const [text, setText] = useState('');
  const [flash, setFlash] = useState<Flash>(null);

  if (!messages.length) return <Empty>No messages yet.</Empty>;
  return (
    <div>
      <Notice flash={flash} onClose={() => setFlash(null)} />
      {messages.map(m => (
        <div key={m.id} className={`msg ${m.mine ? 'mine' : 'theirs'}`}>
          <div className="msg-head"><b>{m.mine ? `You → ${m.recipientName}` : `${m.senderName} → You`}</b><span className="muted"> · {m.propertyTitle} · {fmtDate(m.createdAt)}</span></div>
          <p>{m.body}</p>
          {!m.mine && open !== m.id && <button className="btn secondary small" onClick={() => { setOpen(m.id); setText(''); }}>Reply</button>}
          {!m.mine && open === m.id && (
            <div className="form" style={{ marginTop: 8 }}>
              <textarea className="input" rows={2} value={text} maxLength={1000} onChange={e => setText(e.target.value)} placeholder="Write your reply…" />
              <div className="row">
                <ActionButton busyLabel="Sending…" disabled={!text.trim()} onClick={async () => {
                  const ok = await run(() => reply(m.id, text), setFlash, 'Reply sent.');
                  if (ok) { setOpen(null); setText(''); await onChanged(); }
                }}>Send reply</ActionButton>
                <button className="btn secondary" onClick={() => setOpen(null)}>Cancel</button>
              </div>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
