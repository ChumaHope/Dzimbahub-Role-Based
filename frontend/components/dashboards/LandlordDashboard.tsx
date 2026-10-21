'use client';
import Link from 'next/link';
import dynamic from 'next/dynamic';
import { useCallback, useEffect, useState } from 'react';
import { api, Booking, mediaUrl, Msg, Property, PropertyInput, Viewing } from '../../lib/api';
import { AuthUser } from '../../lib/auth';
import MessageThread from '../MessageThread';
import PhotoUploader from '../PhotoUploader';
import { ActionButton, Badge, Empty, Flash, Notice, Stat, run } from '../ui';

const LocationPicker = dynamic(() => import('../LocationPicker'), { ssr: false, loading: () => <div className="empty small">Loading map…</div> });
const BLANK: PropertyInput = { title: '', area: '', city: 'Harare', address: '', description: '', price: 0, roomType: 'Single', bedrooms: 1, bathrooms: 1, furnished: false, latitude: -17.8252, longitude: 31.0335, imageUrl: '', amenities: '', photos: [] };

export default function LandlordDashboard({ user }: { user: AuthUser }) {
  const [props, setProps] = useState<Property[]>([]);
  const [viewings, setViewings] = useState<Viewing[]>([]);
  const [messages, setMessages] = useState<Msg[]>([]);
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [form, setForm] = useState<PropertyInput>(BLANK);
  const [editing, setEditing] = useState<number | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [flash, setFlash] = useState<Flash>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const [p, v, m, b] = await Promise.all([api.landlord.properties(), api.landlord.viewings(), api.landlord.messages(), api.landlord.bookings()]);
      setProps(p); setViewings(v); setMessages(m); setBookings(b);
    } catch (e) { setFlash({ kind: 'error', text: e instanceof Error ? e.message : 'Could not load your dashboard.' }); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { load(); }, [load]);

  const set = <K extends keyof PropertyInput>(k: K, v: PropertyInput[K]) => setForm(f => ({ ...f, [k]: v }));
  function startNew() { setForm(BLANK); setEditing(null); setShowForm(true); }
  function startEdit(p: Property) {
    setForm({ title: p.title, area: p.area, city: p.city, address: p.address, description: p.description, price: p.price, roomType: p.roomType,
      bedrooms: p.bedrooms, bathrooms: p.bathrooms, furnished: p.furnished, latitude: p.latitude, longitude: p.longitude,
      imageUrl: p.imageUrl, amenities: p.amenitiesCsv ?? p.amenities.join(','),
      photos: p.photos?.length ? p.photos : (p.imageUrl ? [p.imageUrl] : []) });
    setEditing(p.id); setShowForm(true); window.scrollTo({ top: 0, behavior: 'smooth' });
  }
  function valid() {
    if (!form.title.trim() || !form.area.trim() || !form.address.trim()) return 'Title, area and address are required.';
    if (!(form.price > 0)) return 'Enter a monthly rent greater than zero.';
    if (!form.photos.length) return 'Add at least one photo so tenants can see the place.';
    return '';
  }
  async function save() {
    const problem = valid(); if (problem) { setFlash({ kind: 'error', text: problem }); return; }
    const body = { ...form, imageUrl: form.photos[0] ?? '' };
    const wasSuspended = editing != null && props.find(p => p.id === editing)?.listingStatus === 'SUSPENDED';
    const ok = await run(() => (editing ? api.landlord.update(editing, body) : api.landlord.create(body)), setFlash,
      wasSuspended ? 'Changes saved. This listing stays hidden until an administrator restores it.'
        : editing ? 'Changes saved and sent to an administrator for approval. The listing is hidden until it is approved.'
        : 'Listing submitted. An administrator will review it before tenants can see it.');
    if (ok) { setShowForm(false); setEditing(null); await load(); }
  }

  const waiting = viewings.filter(v => v.status === 'PENDING');
  const unanswered = messages.filter(m => !m.mine).length;

  return (
    <main className="container section">
      <div className="section-head"><div><span className="role-chip landlord">landlord</span><h1>Welcome, {user.fullName.split(' ')[0]}</h1>
        <p className="muted">Manage your listings, answer viewing requests and reply to enquiries.</p></div>
        <button className="btn orange" onClick={startNew}>+ Add a listing</button></div>
      <Notice flash={flash} onClose={() => setFlash(null)} />
      <div className="dashboard">
        <Stat label="My listings" value={loading ? '…' : props.length} />
        <Stat label="Awaiting approval" value={loading ? '…' : props.filter(p => p.listingStatus === 'PENDING').length} tone={props.some(p => p.listingStatus === 'PENDING') ? 'warn' : undefined} />
        <Stat label="Viewing requests to answer" value={loading ? '…' : waiting.length} tone={waiting.length ? 'warn' : undefined} />
        <Stat label="Enquiries received" value={loading ? '…' : unanswered} />
        <Stat label="Confirmed bookings" value={loading ? '…' : bookings.filter(b => b.status === 'CONFIRMED').length} tone="good" />
      </div>

      {showForm && (
        <section className="detail" style={{ marginTop: 22 }}>
          <h2>{editing ? 'Edit listing' : 'New listing'}</h2>
          <div className="form-grid">
            <label>Title<input className="input" value={form.title} maxLength={150} onChange={e => set('title', e.target.value)} placeholder="e.g. Sunrise Student Lodge" /></label>
            <label>Monthly rent (US$)<input className="input" type="number" min={1} value={form.price || ''} onChange={e => set('price', Number(e.target.value))} /></label>
            <label>Area<input className="input" value={form.area} onChange={e => set('area', e.target.value)} placeholder="e.g. Avondale" /></label>
            <label>City<input className="input" value={form.city} onChange={e => set('city', e.target.value)} /></label>
            <label className="wide">Street address<input className="input" value={form.address} onChange={e => set('address', e.target.value)} /></label>
            <label>Room type<select className="select" value={form.roomType} onChange={e => set('roomType', e.target.value)}><option>Single</option><option>Shared</option><option>Self-contained</option></select></label>
            <label>Bedrooms<input className="input" type="number" min={0} max={20} value={form.bedrooms} onChange={e => set('bedrooms', Number(e.target.value))} /></label>
            <label>Bathrooms<input className="input" type="number" min={0} max={20} value={form.bathrooms} onChange={e => set('bathrooms', Number(e.target.value))} /></label>
            <label className="check"><input type="checkbox" checked={form.furnished} onChange={e => set('furnished', e.target.checked)} /> Furnished</label>
            <label className="wide">Amenities (comma separated)<input className="input" value={form.amenities} onChange={e => set('amenities', e.target.value)} placeholder="WiFi, Security, Kitchen" /></label>
            <div className="wide"><b>Photos</b>
              <PhotoUploader photos={form.photos} onChange={next => set('photos', next)} onError={text => setFlash({ kind: 'error', text })} /></div>
            <label className="wide">Description<textarea className="input" rows={3} maxLength={1500} value={form.description} onChange={e => set('description', e.target.value)} /></label>
            <div className="wide"><b>Location</b> <span className="muted">Click the map to place the pin ({form.latitude}, {form.longitude})</span>
              <LocationPicker lat={form.latitude} lng={form.longitude} onPick={(la, lo) => setForm(f => ({ ...f, latitude: la, longitude: lo }))} /></div>
          </div>
          <div className="row" style={{ marginTop: 14 }}>
            <ActionButton busyLabel="Saving…" onClick={save}>{editing ? 'Save and resubmit' : 'Submit for approval'}</ActionButton>
            <button className="btn secondary" onClick={() => { setShowForm(false); setEditing(null); }}>Cancel</button>
          </div>
        </section>
      )}

      <section className="section"><h2>My listings</h2>
        {!props.length ? <Empty>You have no listings yet. Choose “Add a listing”.</Empty> : (
          <div className="table-wrap"><table className="table"><thead><tr><th>Listing</th><th>Rent</th><th>Rating</th><th>Status</th><th></th></tr></thead><tbody>
            {props.map(p => (
              <tr key={p.id}><td><div className="listing-cell"><img className="thumb" src={mediaUrl(p.imageUrl)} alt="" />
                <div>{p.listingStatus === 'ACTIVE' ? <Link href={`/properties/${p.id}`}>{p.title}</Link> : <b>{p.title}</b>}<br /><span className="muted">{p.area}, {p.city}</span></div></div></td>
                <td>US$ {p.price}</td><td>{p.reviewCount ? `${p.rating.toFixed(1)} (${p.reviewCount})` : 'No reviews'}</td>
                <td><Badge status={p.listingStatus ?? 'ACTIVE'} />
                  {p.listingStatus === 'PENDING' && <div className="muted">Waiting for an administrator to approve it</div>}
                  {p.listingStatus === 'ACTIVE' && <div className="muted">Visible to tenants</div>}
                  {p.listingStatus === 'REJECTED' && <div className="review-note">Not approved: {p.reviewNote || 'no reason given'}. Edit and save to resubmit.</div>}
                  {p.listingStatus === 'SUSPENDED' && <div className="muted">Hidden by an administrator{p.reviewNote ? `: ${p.reviewNote}` : ''}</div>}</td>
                <td className="actions"><button className="btn secondary small" onClick={() => startEdit(p)}>Edit</button>
                  <ActionButton className="danger small" busyLabel="Removing…" onClick={async () => {
                    if (!window.confirm(`Remove “${p.title}”? Its viewing requests, messages and reviews will also be removed.`)) return;
                    if (await run(() => api.landlord.remove(p.id), setFlash, 'Listing removed.')) await load();
                  }}>Remove</ActionButton></td></tr>))}
          </tbody></table></div>)}
      </section>

      <section className="section"><h2>Viewing requests</h2>
        {!viewings.length ? <Empty>No viewing requests yet.</Empty> : (
          <div className="table-wrap"><table className="table"><thead><tr><th>Property</th><th>Tenant</th><th>Requested for</th><th>Note</th><th>Status</th><th></th></tr></thead><tbody>
            {viewings.map(v => (
              <tr key={v.id}><td>{v.propertyTitle}</td><td>{v.tenantName}</td><td>{v.date} at {v.time.slice(0, 5)}</td><td>{v.note || '—'}</td><td><Badge status={v.status} /></td>
                <td className="actions">{v.status === 'PENDING' && <>
                  <ActionButton className="small" busyLabel="Approving…" onClick={async () => { if (await run(() => api.landlord.decide(v.id, true), setFlash, 'Viewing approved. The tenant can now book or rate.')) await load(); }}>Approve</ActionButton>
                  <ActionButton className="secondary small" busyLabel="Declining…" onClick={async () => { if (await run(() => api.landlord.decide(v.id, false), setFlash, 'Viewing declined.')) await load(); }}>Decline</ActionButton></>}</td></tr>))}
          </tbody></table></div>)}
      </section>

      <section className="section two-col">
        <div className="detail"><h2>Enquiries</h2><MessageThread messages={messages} reply={api.landlord.reply} onChanged={load} /></div>
        <div className="detail"><h2>Bookings</h2>
          {!bookings.length ? <Empty>No bookings yet.</Empty> : bookings.map(b => (
            <div className="msg theirs" key={b.id}><b>{b.tenantName}</b> · {b.propertyTitle}<br />US$ {b.amount} <Badge status={b.status} />{b.paymentRef && <span className="muted"> · ref {b.paymentRef}</span>}</div>))}
        </div>
      </section>
    </main>
  );
}
