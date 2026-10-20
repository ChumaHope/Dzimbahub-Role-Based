'use client';
import { useCallback, useEffect, useState } from 'react';
import { api, AuditRow, Property, UserRow } from '../../lib/api';
import { AuthUser } from '../../lib/auth';
import { ActionButton, Badge, Empty, Flash, Notice, Stat, fmtDate, run } from '../ui';

type Tab = 'requests' | 'users' | 'listings' | 'activity';

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
        <Stat label="Active listings" value={n('activeProperties')} tone="good" />
        <Stat label="Viewing requests" value={n('viewings')} />
        <Stat label="Enquiries" value={n('messages')} />
        <Stat label="Reviews" value={n('reviews')} />
        <Stat label="Bookings" value={n('bookings')} />
      </div>

      <div className="tabs" role="tablist">
        {([['requests', `Signup requests (${pending.length})`], ['users', 'All users'], ['listings', 'Listings'], ['activity', 'Activity log']] as [Tab, string][]).map(([k, label]) => (
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

      {tab === 'listings' && (!listings.length ? <Empty>There are no listings.</Empty> : (
        <div className="table-wrap"><table className="table"><thead><tr><th>Listing</th><th>Landlord</th><th>Rent</th><th>Visibility</th><th></th></tr></thead><tbody>
          {listings.map(p => {
            const suspended = p.listingStatus === 'SUSPENDED';
            return (
              <tr key={p.id}><td>{p.title}<br /><span className="muted">{p.area}, {p.city}</span></td><td>{p.landlordName}</td><td>US$ {p.price}</td><td><Badge status={suspended ? 'SUSPENDED' : 'ACTIVE'} /></td>
                <td className="actions">
                  <ActionButton className="secondary small" busyLabel="Updating…" onClick={async () => { if (await run(() => api.admin.setListingStatus(p.id, suspended ? 'ACTIVE' : 'SUSPENDED'), setFlash, suspended ? 'Listing restored.' : 'Listing hidden from the public.')) await load(); }}>{suspended ? 'Restore' : 'Suspend'}</ActionButton>
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
