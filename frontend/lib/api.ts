export type Role = 'TENANT' | 'LANDLORD' | 'ADMIN';

export type Property = {
  id: number; title: string; area: string; city: string; address: string; description: string;
  price: number; roomType: string; bedrooms: number; bathrooms: number; furnished: boolean;
  latitude: number; longitude: number; imageUrl: string; landlordName: string; rating: number; reviewCount: number;
  amenities: string[]; amenitiesCsv?: string; ownerId?: number | null; listingStatus?: ListingStatus;
  reviewNote?: string | null; photos?: string[]; createdAt?: string;
};
export type ListingStatus = 'PENDING' | 'ACTIVE' | 'REJECTED' | 'SUSPENDED';
export type PropertyInput = {
  title: string; area: string; city: string; address: string; description: string; price: number; roomType: string;
  bedrooms: number; bathrooms: number; furnished: boolean; latitude: number; longitude: number; imageUrl: string; amenities: string;
  photos: string[];
};
export type RecommendationRequest = { budget: number; area: string; roomType: string; furnished: boolean };
export type Preferences = { budget: number; area: string; roomType: string; furnished: boolean };
export type Viewing = { id: number; propertyId: number; propertyTitle: string; tenantId: number; tenantName: string; date: string; time: string; note?: string; status: 'PENDING' | 'APPROVED' | 'DECLINED' | 'CANCELLED'; createdAt: string };
export type Msg = { id: number; propertyId: number; propertyTitle: string; senderId: number; senderName: string; recipientId: number; recipientName: string; body: string; createdAt: string; mine: boolean };
export type Review = { id: number; propertyId: number; tenantName: string; rating: number; comment?: string; createdAt: string };
export type Booking = { id: number; propertyId: number; propertyTitle: string; tenantId: number; tenantName: string; amount: number; status: 'PENDING' | 'CONFIRMED'; paymentRef?: string; createdAt: string };
export type UserRow = { id: number; fullName: string; email: string; phone?: string; role: Role; status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED'; createdAt: string; authenticator?: boolean };
export type AuditRow = { id: number; actor?: string; action: string; targetType?: string; targetId?: string; detail?: string; createdAt: string };

export const API = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
/** Uploaded photos are stored as "/api/images/12"; this turns them into a full address on the API server. */
export function mediaUrl(url?: string | null): string {
  if (!url) return '';
  return url.startsWith('/api/') ? API.replace(/\/api\/?$/, '') + url : url;
}
const TOKEN_KEY = 'dzimbahub_token';
const USER_KEY = 'dzimbahub_user';

function authHeaders(): Record<string, string> {
  if (typeof window === 'undefined') return {};
  const token = localStorage.getItem(TOKEN_KEY);
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** All calls go through here so errors always become readable messages. */
export async function request<T>(path: string, options?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`${API}${path}`, {
      ...options,
      headers: { 'Content-Type': 'application/json', ...authHeaders(), ...(options?.headers || {}) },
      cache: 'no-store'
    });
  } catch {
    throw new Error('Cannot reach the server. Check your internet connection and try again.');
  }
  const body = await res.json().catch(() => null);
  if (!res.ok) {
    if (res.status === 401 && typeof window !== 'undefined' && localStorage.getItem(TOKEN_KEY)) {
      // the saved sign-in is no longer valid (expired token or suspended account)
      localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY);
      window.dispatchEvent(new Event('dzimbahub-auth-change'));
    }
    throw new Error(body?.message || `The server returned an error (${res.status}).`);
  }
  return body as T;
}

/** Sends one photo as multipart form data (no JSON Content-Type, the browser sets the boundary). */
async function uploadFile(path: string, file: Blob, name: string): Promise<{ id: number; url: string }> {
  const form = new FormData();
  form.append('file', file, name);
  let res: Response;
  try { res = await fetch(`${API}${path}`, { method: 'POST', headers: authHeaders(), body: form }); }
  catch { throw new Error('Cannot reach the server. Check your internet connection and try again.'); }
  const body = await res.json().catch(() => null);
  if (!res.ok) throw new Error(body?.message || `The photo could not be uploaded (${res.status}).`);
  return body as { id: number; url: string };
}

const post = <T,>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body: JSON.stringify(body ?? {}) });
const put = <T,>(path: string, body: unknown) => request<T>(path, { method: 'PUT', body: JSON.stringify(body) });
const del = <T,>(path: string) => request<T>(path, { method: 'DELETE' });

export const api = {
  // public
  properties: (query = '') => request<Property[]>(`/properties${query}`),
  property: (id: number) => request<Property>(`/properties/${id}`),
  reviewsFor: (id: number) => request<Review[]>(`/properties/${id}/reviews`),
  recommendations: (body: RecommendationRequest) => post<Property[]>('/recommendations', body),

  // tenant
  tenant: {
    viewings: () => request<Viewing[]>('/tenant/viewings'),
    requestViewing: (body: { propertyId: number; date: string; time: string; note?: string }) => post<Viewing>('/tenant/viewings', body),
    cancelViewing: (id: number) => post<Viewing>(`/tenant/viewings/${id}/cancel`),
    enquire: (body: { propertyId: number; message: string }) => post<Msg>('/tenant/enquiries', body),
    messages: () => request<Msg[]>('/tenant/messages'),
    reply: (id: number, message: string) => post<Msg>(`/tenant/messages/${id}/reply`, { message }),
    review: (body: { propertyId: number; rating: number; comment: string }) => post<Review>('/tenant/reviews', body),
    bookings: () => request<Booking[]>('/tenant/bookings'),
    book: (propertyId: number) => post<Booking>('/tenant/bookings', { propertyId }),
    pay: (id: number) => post<Booking>(`/tenant/bookings/${id}/pay`),
    preferences: () => request<Preferences>('/tenant/preferences'),
    savePreferences: (p: Preferences) => put<Preferences>('/tenant/preferences', p)
  },

  // landlord
  landlord: {
    properties: () => request<Property[]>('/landlord/properties'),
    create: (p: PropertyInput) => post<Property>('/landlord/properties', p),
    update: (id: number, p: PropertyInput) => put<Property>(`/landlord/properties/${id}`, p),
    remove: (id: number) => del<{ message: string }>(`/landlord/properties/${id}`),
    uploadImage: (file: Blob, name = 'photo.jpg') => uploadFile('/landlord/images', file, name),
    viewings: () => request<Viewing[]>('/landlord/viewings'),
    decide: (id: number, approve: boolean) => post<Viewing>(`/landlord/viewings/${id}/decision`, { approve }),
    messages: () => request<Msg[]>('/landlord/messages'),
    reply: (id: number, message: string) => post<Msg>(`/landlord/messages/${id}/reply`, { message }),
    bookings: () => request<Booking[]>('/landlord/bookings')
  },

  // administrator
  admin: {
    stats: () => request<Record<string, number>>('/admin/stats'),
    users: (status = '') => request<UserRow[]>(`/admin/users${status ? `?status=${status}` : ''}`),
    userAction: (id: number, action: 'approve' | 'reject' | 'suspend' | 'reactivate' | 'reset-authenticator') => post<UserRow>(`/admin/users/${id}/${action}`),
    properties: () => request<Property[]>('/admin/properties'),
    setListingStatus: (id: number, status: ListingStatus) => post<Property>(`/admin/properties/${id}/status`, { status }),
    approveListing: (id: number) => post<Property>(`/admin/properties/${id}/approve`),
    rejectListing: (id: number, reason: string) => post<Property>(`/admin/properties/${id}/reject`, { reason }),
    removeListing: (id: number) => del<{ message: string }>(`/admin/properties/${id}`),
    audit: () => request<AuditRow[]>('/admin/audit')
  }
};
