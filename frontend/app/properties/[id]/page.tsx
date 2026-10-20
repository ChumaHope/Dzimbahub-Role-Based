'use client';
import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { api, Booking, Property, Review, Viewing } from '../../../lib/api';
import { useUser } from '../../../lib/useUser';
import { ActionButton, Flash, Notice, Stars, fmtDate, run } from '../../../components/ui';

export default function Detail() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const { user } = useUser();
  const [p, setP] = useState<Property | null>(null);
  const [missing, setMissing] = useState(false);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [myViewings, setMyViewings] = useState<Viewing[]>([]);
  const [myBooking, setMyBooking] = useState<Booking | null>(null);
  const [flash, setFlash] = useState<Flash>(null);

  const [date, setDate] = useState('');
  const [time, setTime] = useState('10:00');
  const [note, setNote] = useState('');
  const [message, setMessage] = useState('');
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');

  const loadProperty = useCallback(async () => {
    try { const [prop, rv] = await Promise.all([api.property(id), api.reviewsFor(id)]); setP(prop); setReviews(rv); }
    catch { setMissing(true); }
  }, [id]);
  const loadMine = useCallback(async () => {
    if (user?.role !== 'TENANT') return;
    try {
      const [v, b] = await Promise.all([api.tenant.viewings(), api.tenant.bookings()]);
      setMyViewings(v.filter(x => x.propertyId === id)); setMyBooking(b.find(x => x.propertyId === id) ?? null);
    } catch { /* the tenant dashboard shows load errors */ }
  }, [user?.role, id]);
  useEffect(() => { loadProperty(); }, [loadProperty]);
  useEffect(() => { loadMine(); }, [loadMine]);

  if (missing) return <main className="container section"><div className="empty">This property is not available. <Link href="/properties">Back to listings</Link></div></main>;
  if (!p) return <main className="container section"><div className="empty">Loading property…</div></main>;

  const today = new Date().toISOString().slice(0, 10);
  const hasPending = myViewings.some(v => v.status === 'PENDING');
  const hasApproved = myViewings.some(v => v.status === 'APPROVED');
  const isOwner = user?.role === 'LANDLORD' && user.id === p.ownerId;

  return (
    <main className="container section">
      <div className="two-col">
        <div>
          <img className="detail-image" src={p.imageUrl} alt={p.title} />
          <div className="detail" style={{ marginTop: 16 }}>
            <h1>{p.title}</h1>
            <p className="muted">📍 {p.address}, {p.area}, {p.city}</p>
            <div className="price">US$ {p.price}/month</div>
            <p className="muted">{p.roomType} · {p.bedrooms} bed · {p.bathrooms} bath · {p.furnished ? 'Furnished' : 'Unfurnished'} · Landlord: {p.landlordName}</p>
            <p>{p.description}</p>
            {p.amenities.map(a => <span className="pill" key={a}>{a}</span>)}
          </div>
          <div className="detail" style={{ marginTop: 16 }}>
            <h2>Reviews {p.reviewCount > 0 && <span className="muted">· {p.rating.toFixed(1)} <Stars value={p.rating} /> ({p.reviewCount})</span>}</h2>
            {!reviews.length ? <p className="muted">No tenant reviews yet.</p> : reviews.map(r => (
              <div className="msg theirs" key={r.id}><b>{r.tenantName}</b> <Stars value={r.rating} /> <span className="muted">· {fmtDate(r.createdAt)}</span>{r.comment && <p>{r.comment}</p>}</div>))}
          </div>
        </div>

        <div className="detail">
          <Notice flash={flash} onClose={() => setFlash(null)} />

          {!user && (<>
            <h2>Interested in this property?</h2>
            <p className="muted">Sign in as a tenant to request a viewing, send an enquiry, book or rate this property.</p>
            <div className="row"><Link className="btn" href="/login">Log in</Link><Link className="btn orange" href="/signup">Request an account</Link></div>
          </>)}

          {user && user.role !== 'TENANT' && (<>
            <h2>{user.role === 'LANDLORD' ? 'Landlord view' : 'Administrator view'}</h2>
            <p className="muted">Viewings, enquiries, bookings and reviews are for tenants. Your role is <b>{user.role.toLowerCase()}</b>.</p>
            {isOwner && <Link className="btn" href="/dashboard">Manage this listing</Link>}
            {user.role === 'ADMIN' && <Link className="btn" href="/dashboard">Open administration</Link>}
          </>)}

          {user?.role === 'TENANT' && (<>
            <h2>Arrange a viewing</h2>
            {myViewings.length > 0 && <p className="muted">Your latest request: <b>{myViewings[0].status.toLowerCase()}</b> ({myViewings[0].date} at {myViewings[0].time.slice(0, 5)})</p>}
            <div className="form">
              <label>Date<input className="input" type="date" min={today} value={date} onChange={e => setDate(e.target.value)} /></label>
              <label>Time<input className="input" type="time" value={time} onChange={e => setTime(e.target.value)} /></label>
              <input className="input" value={note} maxLength={500} onChange={e => setNote(e.target.value)} placeholder="Optional note for the landlord" />
              <ActionButton busyLabel="Sending request…" disabled={hasPending} onClick={async () => {
                if (!date) { setFlash({ kind: 'error', text: 'Choose a date for the viewing.' }); return; }
                if (await run(() => api.tenant.requestViewing({ propertyId: p.id, date, time, note }), setFlash, 'Viewing request sent. The landlord will approve or decline it.')) { setNote(''); await loadMine(); }
              }}>{hasPending ? 'Request already waiting' : 'Request viewing'}</ActionButton>
            </div>

            <hr className="rule" />
            <h2>Send an enquiry</h2>
            <div className="form">
              <textarea className="input" rows={4} value={message} maxLength={1000} onChange={e => setMessage(e.target.value)} placeholder="Ask the landlord about availability…" />
              <ActionButton className="secondary" busyLabel="Sending…" disabled={!message.trim()} onClick={async () => {
                if (await run(() => api.tenant.enquire({ propertyId: p.id, message }), setFlash, 'Enquiry sent. Replies will appear on your dashboard.')) setMessage('');
              }}>Send enquiry</ActionButton>
            </div>

            <hr className="rule" />
            <h2>Book this room</h2>
            {myBooking ? (
              <p className="muted">Booking <b>{myBooking.status.toLowerCase()}</b>{myBooking.paymentRef ? ` · reference ${myBooking.paymentRef}` : ''}. {myBooking.status === 'PENDING' && <Link href="/dashboard">Complete the simulated payment on your dashboard.</Link>}</p>
            ) : (
              <div className="form">
                {!hasApproved && <p className="muted">You can book after the landlord approves a viewing.</p>}
                <ActionButton className="orange" busyLabel="Creating booking…" disabled={!hasApproved} onClick={async () => {
                  if (await run(() => api.tenant.book(p.id), setFlash, 'Booking created. Confirm it with the simulated payment on your dashboard.')) await loadMine();
                }}>Create booking</ActionButton>
              </div>)}

            <hr className="rule" />
            <h2>Rate this property</h2>
            <div className="form">
              {!hasApproved && <p className="muted">You can rate a property after the landlord approves your viewing.</p>}
              <div className="stars-input" role="radiogroup" aria-label="Rating">
                {[1, 2, 3, 4, 5].map(n => <button key={n} type="button" role="radio" aria-checked={rating === n} className={n <= rating ? 'star on' : 'star'} onClick={() => setRating(n)} aria-label={`${n} star${n > 1 ? 's' : ''}`}>★</button>)}
                <span className="muted"> {rating} / 5</span>
              </div>
              <textarea className="input" rows={3} value={comment} maxLength={1000} onChange={e => setComment(e.target.value)} placeholder="Write a review (optional)" />
              <ActionButton className="orange" busyLabel="Submitting…" disabled={!hasApproved} onClick={async () => {
                if (await run(() => api.tenant.review({ propertyId: p.id, rating, comment }), setFlash, 'Thank you. Your review has been published.')) { setComment(''); await loadProperty(); }
              }}>Submit review</ActionButton>
            </div>
          </>)}
        </div>
      </div>
    </main>
  );
}
