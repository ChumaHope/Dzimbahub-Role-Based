'use client';
import { useEffect, useState } from 'react';
import { api, Property } from '../../lib/api';
import { useUser } from '../../lib/useUser';
import PropertyCard from '../../components/PropertyCard';
import { Flash, Notice } from '../../components/ui';

export default function Recommendations() {
  const { user } = useUser();
  const [budget, setBudget] = useState('200');
  const [area, setArea] = useState('');
  const [roomType, setRoomType] = useState('Single');
  const [furnished, setFurnished] = useState(false);
  const [items, setItems] = useState<Property[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [flash, setFlash] = useState<Flash>(null);

  // tenants get their saved preferences pre-filled
  useEffect(() => {
    if (user?.role !== 'TENANT') return;
    api.tenant.preferences().then(p => {
      if (p.budget) setBudget(String(p.budget)); if (p.area) setArea(p.area); if (p.roomType) setRoomType(p.roomType); setFurnished(p.furnished);
    }).catch(() => {});
  }, [user?.role]);

  const go = async () => {
    setLoading(true); setFlash(null);
    try { setItems(await api.recommendations({ budget: Number(budget) || 0, area, roomType, furnished })); setSearched(true); }
    catch (e) { setFlash({ kind: 'error', text: e instanceof Error ? e.message : 'Could not load recommendations.' }); }
    finally { setLoading(false); }
  };

  return (
    <main className="container section">
      <div className="detail"><span className="pill">PROPERTY RECOMMENDATIONS</span><h1>Tell DzimbaHub what you need.</h1>
        <p className="muted">Properties are ranked with a transparent scoring method based on budget, area, room type, furnishing and property rating.
          {user?.role === 'TENANT' ? ' Your saved preferences are filled in for you.' : ' Tenants can save their preferences on their dashboard.'}</p>
        <div className="searchbox" style={{ boxShadow: 'none', padding: 0, marginTop: 20 }}>
          <input className="input" type="number" min={0} value={budget} onChange={e => setBudget(e.target.value)} placeholder="Monthly budget" />
          <input className="input" value={area} onChange={e => setArea(e.target.value)} placeholder="Preferred area" />
          <select className="select" value={roomType} onChange={e => setRoomType(e.target.value)}><option>Single</option><option>Shared</option><option>Self-contained</option></select>
          <label className="input"><input type="checkbox" checked={furnished} onChange={e => setFurnished(e.target.checked)} /> Furnished</label>
          <button className="btn orange" onClick={go} disabled={loading}>{loading ? 'Matching…' : 'Recommend'}</button>
        </div>
      </div>
      <section className="section">
        <Notice flash={flash} onClose={() => setFlash(null)} />
        <div className="section-head"><h2>Recommended properties</h2></div>
        <div className="grid">{items.map(p => <PropertyCard key={p.id} p={p} />)}</div>
        {!items.length && !loading && <div className="empty">{searched ? 'No properties match yet. Try a different budget or area.' : 'Enter preferences and select Recommend.'}</div>}
      </section>
    </main>
  );
}
