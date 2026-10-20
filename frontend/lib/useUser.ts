'use client';
import { useEffect, useState } from 'react';
import { AuthUser, getUser } from './auth';

/** The signed-in user (null when signed out). `loaded` is false until the browser has been checked. */
export function useUser() {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loaded, setLoaded] = useState(false);
  useEffect(() => {
    const sync = () => setUser(getUser());
    sync(); setLoaded(true);
    window.addEventListener('storage', sync);
    window.addEventListener('dzimbahub-auth-change', sync);
    return () => { window.removeEventListener('storage', sync); window.removeEventListener('dzimbahub-auth-change', sync); };
  }, []);
  return { user, loaded };
}
