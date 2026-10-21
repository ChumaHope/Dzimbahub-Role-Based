'use client';
import { useCallback, useEffect, useState } from 'react';
import { api, AuditRow, mediaUrl, Property, UserRow } from '../../lib/api';
import { AuthUser } from '../../lib/auth';
import { ActionButton, Badge, Empty, Flash, Notice, Stat, fmtDate, run } from '../ui';

type Tab = 'requests' | 'approvals' | 'users' | 'listings' | 'activity';

export default function AdminDashboard({ user }: { user: AuthUser }) {
  const [tab, setTab] = useState<Tab>('requests');
  const [stats, setStats] = useState<Record<string, number>>({});
  const [users, setUsers] = useState<UserRow[]>([]);
  const [listings, setListings] = useState<Property[]>([]);
  const [audit, setAudit] = useState<AuditRow[]>([]);
  const [flash, setFlash] = useState<Flash>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const [s, u, l, a] = await Promise.all([api.admin.stats(), api.admin.users(), api.admin.properties(), api.admin.audit()]);
      setStats(s); setUsers(u); setListings(l); setAudit(a);
    } catch (e) { setFlash({ kind: 'error', text: e instanceof Error ? e.message : 'Could not load administration data.' }); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { load(); }, [load]);

  const pending = users.filter(u => u.status === 'PENDING');
  const toApprove = listings.filter(p => p.listingStatus === 'PENDING');
  const approve = (p: Property) => async () => {
    if (await run(() => api.admin.approveListing(p.id), setFlash, `“${p.title}” approved. It is now visible to tenants and the landlord has been notified.`)) await load();
  };
  const reject = (p: Property) => async () => {
    const reason = window.prompt(`Why is “${p.title}” not approved? The landlord will see this message.`, 'Photos are unclear');
    if (reason === null) return;
    if (!reason.trim()) { setFlash({ kind: 'error', text: 'Please type a reason so the landlord knows what to fix.' }); return; }
    if (await run(() => api.admin.rejectListing(p.id, reason.trim()), setFlash, `“${p.title}” rejected. The landlord has been told why.`)) await load();
  };
  const act = (u: UserRow, action: 'approve' | 'reject' | 'suspend' | 'reactivate', done: string) => async () => {
    if (await run(() => api.admin.userAction(u.id, action), setFlash, `${u.fullName}: ${done}`)) await load();
  };
  const n = (k: string) => (loading ? '…' : stats[k] ?? 0);

  return (
    <main className="container section">
      <div className="section-head"><div><span className="role-chip admin">administrator</span><h1>Administration</h1>
        <p className="muted">Signed in as {user.email}. Approve new accounts and keep the platform safe.</p></div></div>
      <Notice flash={flash} onClose={() => setFlash(null)} />
      <div className="dashboard">
        <Stat label="Signup requests waiting" value={n('pendingApprovals')} tone={pending.length ? 'warn' : undefined} />
        <Stat label="Tenants" value={n('tenants')} />
        <Stat label="Landlords" value={n('landlords')} />
        <Stat label="Listings to approve" value={n('pendingListings')} tone={toApprove.length ? 'warn' : undefined} />
        <Stat label="Active listings" value={n('activeProperties')} tone="good" />
        <Stat label="Viewing requests" value={n('viewings')} />
        <Stat label="Enquiries" value={n('messages')} />
        <Stat label="Reviews" value={n('reviews')} />
        <Stat label="Bookings" value={n('bookings')} />
      </div>

      <div className="tabs" role="tablist">
        {([['requests', `Signup requests (${pending.length})`], ['approvals', `Listings to approve (${toApprove.length})`], ['users', 'All users'], ['listings', 'Listings'], ['activity', 'Activity log']] as [Tab, string][]).map(([k, label]) => (
          <button key={k} role="tab" aria-selected={tab === k} className={tab === k ? 'tab active' : 'tab'} onClick={() => setTab(k)}>{label}</button>))}
      </div>

      {tab === 'requests' && (!pending.length ? <Empty>No signup requests are waiting. 🎉</Empty> : (
        <div className="table-wrap"><table className="table"><thead><tr><th>Name</th><th>Role</th><th>Email</th><th>Phone</th><th>Requested</th><th></th></tr></thead><tbody>
          {pending.map(u => (
            <tr key={u.id}><td>{u.fullName}</td><td><span className={`role-chip ${u.role.toLowerCase()}`}>{u.role.toLowerCase()}</span></td><td>{u.email}</td><td>{u.phone ?? '—'}</td><td>{fmtDate(u.createdAt)}</td>
              <td className="actions"><ActionButton className="small" busyLabel="Approving…" onClick={act(u, 'approve', 'approved. They have been notified by text message.')}>Approve</ActionButton>
                <ActionButton className="danger small" busyLabel="Rejecting…" onClick={act(u, 'reject', 'request rejected.')}>Reject</ActionButton></td></tr>))}
        </tbody></table></div>))}

      {tab === 'users' && (
        <div className="table-wrap"><table className="table"><thead><tr><th>Name</th><th>Role</th><th>Email</th><th>Status</th><th></th></tr></thead><tbody>
          {users.map(u => (
            <tr key={u.id}><td>{u.fullName}</td><td><span className={`role-chip ${u.role.toLowerCase()}`}>{u.role.toLowerCase()}</span></td><td>{u.email}</td><td><Badge status={u.status} /></td>
              <td className="actions">
                {u.role !== 'ADMIN' && u.status === 'APPROVED' && <ActionButton className="secondary small" busyLabel="Suspending…" onClick={act(u, 'suspend', 'suspended.')}>Suspend</ActionButton>}
                {u.role !== 'ADMIN' && u.status === 'SUSPENDED' && <ActionButton className="small" busyLabel="Reactivating…" onClick={act(u, 'reactivate', 'reactivated.')}>Reactivate</ActionButton>}
                {u.role !== 'ADMIN' && u.status === 'REJECTED' && <ActionButton className="small" busyLabel="Approving…" onClick={act(u, 'approve', 'approved.')}>Approve instead</ActionButton>}
                {u.role !== 'ADMIN' && u.status === 'PENDING' && <span className="muted">See “Signup requests”</span>}
              </td></tr>))}
        </tbody></table></div>)}

      {tab === 'approvals' && (!toApprove.length ? <Empty>No listings are waiting for approval. 🎉</Empty> : (
        <div className="review-list">
          {toApprove.map(p => (
            <article className="detail review-card" key={p.id}>
              <div className="review-photos">
                {(p.photos?.length ? p.photos : [p.imageUrl]).map((url, i) => (
                  <a key={url + i} href={mediaUrl(url)} target="_blank" rel="noreferrer"><img src={mediaUrl(url)} alt={`${p.title} photo ${i + 1}`} /></a>))}
              </div>
              <div className="review-body">
                <h3>{p.title}</h3>
                <p className="muted">📍 {p.address}, {p.area}, {p.city} · by {p.landlordName}{p.createdAt ? ` · submitted ${fmtDate(p.createdAt)}` : ''}</p>
                <div className="price">US$ {p.price}/month</div>
                <p className="muted">{p.roomType} · {p.bedrooms} bed · {p.bathrooms} bath · {p.furnished ? 'Furnished' : 'Unfurnished'}</p>
                {p.description && <p>{p.description}</p>}
                <div>{p.amenities.map(a => <span className="pill" key={a}>{a}</span>)}</div>
                <p className="muted">Map pin: {p.latitude.toFixed(4)}, {p.longitude.toFixed(4)} · <a href={`https://www.google.com/maps?q=${p.latitude},${p.longitude}`} target="_blank" rel="noreferrer">check on Google Maps</a></p>
                <div className="row" style={{ marginTop: 12 }}>
                  <ActionButton className="small" busyLabel="Approving…" onClick={approve(p)}>Approve</ActionButton>
                  <ActionButton className="danger small" busyLabel="Rejecting…" onClick={reject(p)}>Reject…</ActionButton>
                </div>
              </div>
            </article>))}
        </div>))}

      {tab === 'listings' && (!listings.length ? <Empty>There are no listings.</Empty> : (
        <div className="table-wrap"><table className="table"><thead><tr><th>Listing</th><th>Landlord</th><th>Rent</th><th>Status</th><th></th></tr></thead><tbody>
          {listings.map(p => {
            const status = p.listingStatus ?? 'ACTIVE';
            return (
              <tr key={p.id}><td><div className="listing-cell"><img className="thumb" src={mediaUrl(p.imageUrl)} alt="" /><div>{p.title}<br /><span className="muted">{p.area}, {p.city}</span></div></div></td>
                <td>{p.landlordName}</td><td>US$ {p.price}</td>
                <td><Badge status={status} />{status === 'REJECTED' && p.reviewNote && <div className="muted">{p.reviewNote}</div>}</td>
                <td className="actions">
                  {(status === 'PENDING' || status === 'REJECTED') && <ActionButton className="small" busyLabel="Approving…" onClick={approve(p)}>Approve</ActionButton>}
                  {status === 'PENDING' && <ActionButton className="secondary small" busyLabel="Rejecting…" onClick={reject(p)}>Reject…</ActionButton>}
                  {status === 'ACTIVE' && <ActionButton className="secondary small" busyLabel="Updating…" onClick={async () => { if (await run(() => api.admin.setListingStatus(p.id, 'SUSPENDED'), setFlash, 'Listing hidden from the public.')) await load(); }}>Suspend</ActionButton>}
                  {status === 'SUSPENDED' && <ActionButton className="small" busyLabel="Updating…" onClick={async () => { if (await run(() => api.admin.setListingStatus(p.id, 'ACTIVE'), setFlash, 'Listing restored.')) await load(); }}>Restore</ActionButton>}
                  <ActionButton className="danger small" busyLabel="Removing…" onClick={async () => { if (!window.confirm(`Permanently remove “${p.title}”?`)) return; if (await run(() => api.admin.removeListing(p.id), setFlash, 'Listing removed.')) await load(); }}>Remove</ActionButton></td></tr>);
          })}
        </tbody></table></div>))}

      {tab === 'activity' && (!audit.length ? <Empty>No activity recorded yet.</Empty> : (
        <div className="table-wrap"><table className="table"><thead><tr><th>When</th><th>Who</th><th>Action</th><th>Detail</th></tr></thead><tbody>
          {audit.map(a => <tr key={a.id}><td>{fmtDate(a.createdAt)}</td><td>{a.actor ?? 'system'}</td><td>{a.action.replace(/_/g, ' ').toLowerCase()}</td><td>{[a.targetType, a.targetId, a.detail].filter(Boolean).join(' · ')}</td></tr>)}
        </tbody></table></div>))}
    </main>
  );
}
