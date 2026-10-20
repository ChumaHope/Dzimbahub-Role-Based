'use client';
import Link from 'next/link';
import { clearAuth } from '../lib/auth';
import { useUser } from '../lib/useUser';

const LINKS: Record<string, { href: string; label: string }[]> = {
  ANON: [{ href: '/properties', label: 'Find Houses' }, { href: '/recommendations', label: 'Recommendations' }],
  TENANT: [{ href: '/properties', label: 'Find Houses' }, { href: '/recommendations', label: 'Recommendations' }, { href: '/dashboard', label: 'My Dashboard' }],
  LANDLORD: [{ href: '/dashboard', label: 'My Listings & Requests' }, { href: '/properties', label: 'Browse Market' }],
  ADMIN: [{ href: '/dashboard', label: 'Administration' }, { href: '/properties', label: 'Browse Listings' }]
};

export default function SiteNav() {
  const { user } = useUser();
  const links = LINKS[user ? user.role : 'ANON'];
  function logout() { clearAuth(); window.location.href = '/'; }
  return (
    <header className="nav">
      <div className="container nav-inner">
        <Link href="/" className="brand">Dzimba<span>Hub</span></Link>
        <nav className="navlinks" aria-label="Main">{links.map(l => <Link key={l.href} href={l.href}>{l.label}</Link>)}</nav>
        <div className="nav-actions">
          {user ? (
            <>
              <span className={`role-chip ${user.role.toLowerCase()}`}>{user.role.toLowerCase()}</span>
              <span className="nav-user">{user.fullName.split(' ')[0]}</span>
              <button className="btn secondary" onClick={logout}>Sign out</button>
            </>
          ) : (
            <>
              <Link href="/login" className="nav-login">Log in</Link>
              <Link href="/signup" className="btn">Request account</Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
