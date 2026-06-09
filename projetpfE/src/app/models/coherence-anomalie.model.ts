export interface CoherenceAnomalieDetails {
  revenu_declare?: number;
  revenu_extrait?: number;
  tolerance_pct?: number;
  fourchette_declarable_min?: number;
  fourchette_declarable_max?: number;
  revenu_document_minimum?: number;
  action?: string;
}

export interface CoherenceAnomalie {
  code?: string;
  niveau?: string;
  message: string;
  details?: CoherenceAnomalieDetails;
}

export function normalizeCoherenceAnomalies(raw: unknown): CoherenceAnomalie[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw.map((item) => {
    if (typeof item === 'string') {
      return { message: item, niveau: 'BLOQUANT' };
    }
    if (item && typeof item === 'object' && 'message' in item) {
      return item as CoherenceAnomalie;
    }
    return { message: String(item), niveau: 'BLOQUANT' };
  });
}

export function formatTnd(value: number | undefined | null): string {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) {
    return '—';
  }
  const n = Number(value);
  return `${n.toLocaleString('fr-TN', { maximumFractionDigits: 2 })} TND`;
}
