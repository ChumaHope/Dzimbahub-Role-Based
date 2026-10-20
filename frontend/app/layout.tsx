import './globals.css';
import SiteNav from '../components/SiteNav';
export const metadata = { title: 'DzimbaHub | Boarding House Discovery', description: 'Map-centric boarding house management system' };
export default function RootLayout({ children }: { children: React.ReactNode }) {
  return <><SiteNav />{children}<footer className="footer"><div className="container">DzimbaHub: Map-Centric Boarding House Management System with Preference-Based Property Recommendations.</div></footer></>;
}
