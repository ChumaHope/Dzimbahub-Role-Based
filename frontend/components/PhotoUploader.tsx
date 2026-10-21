'use client';
import { useRef, useState } from 'react';
import { api, mediaUrl } from '../lib/api';
import { shrinkPhoto } from '../lib/images';

export const MAX_PHOTOS = 8;

/**
 * Lets a landlord pick photos from their phone gallery, camera or computer.
 * Each photo is shrunk in the browser, uploaded straight away, and its address added to the list.
 * The first photo is the cover shown on listing cards.
 */
export default function PhotoUploader({ photos, onChange, onError }: {
  photos: string[]; onChange: (next: string[]) => void; onError: (message: string) => void;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(0);

  async function pick(files: FileList | null) {
    if (!files?.length) return;
    const room = MAX_PHOTOS - photos.length;
    if (room <= 0) { onError(`A listing can have at most ${MAX_PHOTOS} photos.`); return; }
    const chosen = Array.from(files).slice(0, room);
    if (files.length > room) onError(`Only the first ${room} photo(s) were added. A listing can have at most ${MAX_PHOTOS}.`);
    let next = [...photos];
    setBusy(chosen.length);
    for (const file of chosen) {
      try {
        const small = await shrinkPhoto(file);
        const { url } = await api.landlord.uploadImage(small, file.name.replace(/\.[^.]+$/, '') + '.jpg');
        next = [...next, url];
        onChange(next);
      } catch (e) {
        onError(e instanceof Error ? e.message : `"${file.name}" could not be uploaded.`);
      } finally {
        setBusy(b => b - 1);
      }
    }
    if (input.current) input.current.value = '';
  }

  const remove = (url: string) => onChange(photos.filter(p => p !== url));
  const makeCover = (url: string) => onChange([url, ...photos.filter(p => p !== url)]);

  return (
    <div className="photo-uploader">
      <div className="photo-grid">
        {photos.map((url, i) => (
          <figure key={url} className="photo-tile">
            <img src={mediaUrl(url)} alt={`Photo ${i + 1}`} />
            {i === 0 ? <span className="photo-cover">Cover</span>
              : <button type="button" className="photo-action left" onClick={() => makeCover(url)} title="Use as cover photo">★ Cover</button>}
            <button type="button" className="photo-action" onClick={() => remove(url)} aria-label={`Remove photo ${i + 1}`}>✕</button>
          </figure>
        ))}
        {Array.from({ length: busy }).map((_, i) => <div key={`busy${i}`} className="photo-tile loading"><span className="spinner" /> Uploading…</div>)}
        {photos.length + busy < MAX_PHOTOS && (
          <button type="button" className="photo-tile add" onClick={() => input.current?.click()} disabled={busy > 0}>
            <span className="photo-plus">＋</span>Add photos
          </button>
        )}
      </div>
      <input ref={input} type="file" accept="image/*" multiple hidden onChange={e => pick(e.target.files)} />
      <p className="muted" style={{ margin: '8px 0 0' }}>
        Choose up to {MAX_PHOTOS} photos from your phone or computer (JPG or PNG). They are resized automatically. The first photo is the cover.
      </p>
    </div>
  );
}
