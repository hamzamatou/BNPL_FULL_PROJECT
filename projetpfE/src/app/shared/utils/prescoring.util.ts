/** Parse explicationsJson du prescoring (liste de phrases analyste). */
export function parseExplicationsJson(raw?: string | null): string[] {
  if (!raw?.trim()) return [];
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed
      .map((item) => (typeof item === 'string' ? item.trim() : String(item ?? '').trim()))
      .filter((item) => item.length > 0);
  } catch {
    return raw.trim() ? [raw.trim()] : [];
  }
}
