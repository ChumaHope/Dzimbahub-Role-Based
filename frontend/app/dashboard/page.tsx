'use client';
import Link from 'next/link';
import { useUser } from '../../lib/useUser';
import AdminDashboard from '../../components/dashboards/AdminDashboard';
import LandlordDashboard from '../../components/dashboards/LandlordDashboard';
import TenantDashboard from '../../components/dashboards/TenantDashboard';

/** Each role gets its own dashboard; the API enforces the same separation on the server. */
export default function Dashboard() {
  const { user, loaded } = useUser();
  if (!loaded) return <main className="container section"><p className="muted">Loading your dashboard…</p></main>;
  if (!user) return (
    <main className="container section"><section className="detail center" style={{ maxWidth: 650, margin: '30px auto' }}>
      <span className="pill">SIGN IN REQUIRED</span><h1>Sign in to continue</h1>
      <p className="muted">Log in with your approved DzimbaHub account to open your dashboard.</p>
      <div style={{ display: 'flex', justifyContent: 'center', gap: 12, marginTop: 22 }}><Link className="btn" href="/login">Log in</Link><Link className="btn orange" href="/signup">Request an account</Link></div>
    </section></main>
  );
  if (user.role === 'ADMIN') return <AdminDashboard user={user} />;
  if (user.role === 'LANDLORD') return <LandlordDashboard user={user} />;
  return <TenantDashboard user={user} />;
}
