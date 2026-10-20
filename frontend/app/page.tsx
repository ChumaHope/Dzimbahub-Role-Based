import Link from 'next/link';
import PropertyCard from '../components/PropertyCard';
import { api } from '../lib/api';

export default async function Home() {
  let properties: any[] = [];
  try {
    properties = await api.properties('?limit=6');
  } catch (error) {
    console.error("Failed to load properties", error);
  }

  return (
    <>
      <section className="hero">
        <div className="container">
          <h1>Find a boarding house without the endless search.</h1>
          <p>DzimbaHub brings accommodation listings, location-aware discovery and personalised recommendations into one map-centric platform.</p>
          <div className="searchbox">
            <input className="input" placeholder="Search area e.g. Belvedere, Avondale"/>
            <select className="select">
              <option>Any budget</option>
              <option>Under US$150</option>
              <option>US$150–250</option>
              <option>Above US$250</option>
            </select>
            <select className="select">
              <option>Any room</option>
              <option>Single</option>
              <option>Shared</option>
              <option>Self-contained</option>
            </select>
            <select className="select">
              <option>Any furnishing</option>
              <option>Furnished</option>
              <option>Unfurnished</option>
            </select>
            <Link href="/properties" className="btn orange">Search</Link>
          </div>
        </div>
      </section>

      <main className="container">
        <section className="section">
          <div className="section-head">
            <div>
              <h2>Featured boarding houses</h2>
              <p className="muted">Top-rated boarding houses from approved landlords.</p>
            </div>
            <Link href="/properties" className="btn secondary">View all</Link>
          </div>
          <div className="grid">
            {properties.map((p: any) => (
              <PropertyCard key={p.id} p={p} />
            ))}
          </div>
          {properties.length === 0 && (
            <div className="empty">Listings could not be loaded. Check that the API is running.</div>
          )}
        </section> {/* <--- This was the fix! Changed </div> to </section> */}

        <section className="section">
          <div className="two-col">
            <div className="detail">
              <span className="pill">MAP-CENTRIC</span>
              <h2>Search by place, not just by listing.</h2>
              <p className="muted">Explore properties on a map, compare distance and shortlist accommodation close to your preferred destination.</p>
              <Link href="/properties" className="btn">Open property map</Link>
            </div>
            <div className="detail">
              <span className="pill">SMART MATCHING</span>
              <h2>Get recommendations that match your needs.</h2>
              <p className="muted">Enter a budget, preferred area, room type and furnishing preference to generate ranked property suggestions.</p>
              <Link href="/recommendations" className="btn orange">Try recommendations</Link>
            </div>
          </div>
        </section>
      </main>
    </>
  );
}