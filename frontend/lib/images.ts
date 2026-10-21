/**
 * Shrinks a photo in the browser before upload: longest side at most 1600 px, saved as JPEG.
 * A 6 MB phone photo usually becomes 200-400 KB, which uploads quickly on mobile data
 * and keeps the database small.
 */
export async function shrinkPhoto(file: File, maxSide = 1600, quality = 0.82): Promise<Blob> {
  if (!file.type.startsWith('image/')) throw new Error(`"${file.name}" is not a photo.`);
  let bitmap: ImageBitmap | HTMLImageElement;
  try {
    bitmap = 'createImageBitmap' in window ? await createImageBitmap(file) : await loadImage(file);
  } catch {
    throw new Error(`"${file.name}" could not be opened. Please choose a JPG or PNG photo.`);
  }
  const w = bitmap.width, h = bitmap.height;
  const scale = Math.min(1, maxSide / Math.max(w, h));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(w * scale); canvas.height = Math.round(h * scale);
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Your browser cannot prepare photos for upload.');
  ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, canvas.width, canvas.height); // transparent PNGs get a white background
  ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  if ('close' in bitmap) bitmap.close();
  const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/jpeg', quality));
  if (!blob) throw new Error(`"${file.name}" could not be prepared for upload.`);
  return blob;
}

function loadImage(file: File): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => { URL.revokeObjectURL(url); resolve(img); };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('unreadable')); };
    img.src = url;
  });
}
