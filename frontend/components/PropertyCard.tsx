import Link from 'next/link';
import type { Property } from '../lib/api';
import { mediaUrl } from '../lib/api';

/** A listing card. On the recommendations page it also shows the match % and the reasons. */
export default function PropertyCard({ p, match, reasons }: { p: Property; match?: number; reasons?: string[] }) {
  return (
    <article className={p.reserved ? 'card reserved' : 'card'}>
      <div className="card-img" style={{ backgroundImage: `url(${mediaUrl(p.imageUrl)})` }}>
        {p.reserved && <span className="card-flag reserved">Reserved</span>}
        {match != null && <span className={`card-flag match ${match >= 75 ? 'high' : match >= 50 ? 'mid' : 'low'}`}>{match}% match</span>}
      </div>
      <div className="card-body">
        <div className="price">US$ {p.price}/month</div>
        <h3>{p.title}</h3>
        <div className="meta"><span>📍 {p.area}</span><span>⭐ {p.rating.toFixed(1)}</span>{p.reserved && <span className="badge reserved">reserved</span>}</div>
        {reasons && reasons.length > 0
          ? <ul className="reasons">{reasons.slice(0, 4).map(r => <li key={r}>✓ {r}</li>)}</ul>
          : <div>{p.amenities.slice(0, 3).map(a => <span className="pill" key={a}>{a}</span>)}</div>}
        <Link href={`/properties/${p.id}`} className="btn secondary" style={{ display: 'inline-block', marginTop: 12 }}>View property</Link>
      </div>
    </article>
  );
}
