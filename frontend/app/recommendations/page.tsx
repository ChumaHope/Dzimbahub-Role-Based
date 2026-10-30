'use client';
import { useCallback, useEffect, useState } from 'react';
import { api, RecommendationResult } from '../../lib/api';
import { useUser } from '../../lib/useUser';
import PropertyCard from '../../components/PropertyCard';
import { Flash, Notice } from '../../components/ui';

const AMENITIES = ['WiFi', 'Security', 'Kitchen', 'Parking', 'Solar', 'Borehole', 'Laundry'];

export default function Recommendations() {
  const { user, loaded } = useUser();
  const [budget, setBudget] = useState('200');
  const [area, setArea] = useState('');
  const [roomType, setRoomType] = useState('');
  const [furnished, setFurnished] = useState(false);
  const [amenities, setAmenities] = useState<string[]>([]);
  const [result, setResult] = useState<RecommendationResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [flash, setFlash] = useState<Flash>(null);

  const go = useCallback(async (b = budget, a = area, r = roomType, f = furnished, am = amenities) => {
    setLoading(true); setFlash(null);
    try { setResult(await api.recommendations({ budget: Number(b) || 0, area: a, roomType: r, furnished: f, amenities: am })); }
    catch (e) { setFlash({ kind: 'error', text: e instanceof Error ? e.message : 'Could not load recommendations.' }); }
    finally { setLoading(false); }
  }, [budget, area, roomType, furnished, amenities]);

  // Tenants: fill in saved preferences and show matches straight away. Everyone else: show matches for the defaults.
  useEffect(() => {
    if (!loaded) return;
    if (user?.role !== 'TENANT') { go(); return; }
    api.tenant.preferences().then(p => {
      const b = p.budget ? String(p.budget) : budget, a = p.area || '', r = p.roomType || '';
      setBudget(b); setArea(a); setRoomType(r); setFurnished(p.furnished);
      go(b, a, r, p.furnished, amenities);
    }).catch(() => go());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loaded, user?.role]);

  const toggle = (a: string) => setAmenities(list => list.includes(a) ? list.filter(x => x !== a) : [...list, a]);

  return (
    <main className="container section">
      <div className="detail"><span className="pill">SMART RECOMMENDATIONS</span><h1>Tell DzimbaHub what you need.</h1>
        <p className="muted">Each home gets a match score from your budget, location (including nearby areas on the map), room type, furnishing,
          amenities and tenant ratings. {user?.role === 'TENANT'
            ? 'Because you are signed in, homes similar to the ones you have viewed or enquired about are ranked higher.'
            : 'Sign in as a tenant to get recommendations that learn from the homes you look at.'}</p>
        <form className="searchbox" style={{ boxShadow: 'none', padding: 0, marginTop: 20 }} onSubmit={e => { e.preventDefault(); go(); }}>
          <input className="input" type="number" min={0} value={budget} onChange={e => setBudget(e.target.value)} placeholder="Monthly budget (US$)" aria-label="Monthly budget" />
          <input className="input" value={area} onChange={e => setArea(e.target.value)} placeholder="Preferred area, e.g. Avondale" aria-label="Preferred area" />
          <select className="select" value={roomType} onChange={e => setRoomType(e.target.value)} aria-label="Room type"><option value="">Any room type</option><option>Single</option><option>Shared</option><option>Self-contained</option></select>
          <label className="input"><input type="checkbox" checked={furnished} onChange={e => setFurnished(e.target.checked)} /> Furnished</label>
          <button className="btn orange" disabled={loading}>{loading ? 'Matching…' : 'Recommend'}</button>
        </form>
        <div className="amenity-chips" aria-label="Wanted amenities">
          <span className="muted">Must-haves:</span>
          {AMENITIES.map(a => <button type="button" key={a} className={amenities.includes(a) ? 'chip on' : 'chip'} onClick={() => toggle(a)} aria-pressed={amenities.includes(a)}>{a}</button>)}
        </div>
      </div>
      <section className="section">
        <Notice flash={flash} onClose={() => setFlash(null)} />
        <div className="section-head"><div><h2>Recommended for you</h2>
          {result?.personalised && <p className="muted">Personalised using the homes you have viewed, enquired about or booked.</p>}</div></div>
        {result?.note && <div className="notice" style={{ marginBottom: 16 }}>{result.note}</div>}
        <div className="grid">{result?.results.map(r => <PropertyCard key={r.property.id} p={r.property} match={r.match} reasons={r.reasons} />)}</div>
        {!result && loading && <div className="empty">Finding the best homes for you…</div>}
        {result && !result.results.length && !result.note && <div className="empty">No homes found. Try a different budget or area.</div>}
      </section>
    </main>
  );
}
