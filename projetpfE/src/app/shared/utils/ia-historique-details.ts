export interface IaHistoriqueDetails {
  score?: number;
  probabiliteDefaut?: number;
  zoneCode?: string;
  explications?: string[];
  recommandations?: string[];
  detail?: string;
}

export function parseIaHistoriqueDetails(detailsJson: string | null | undefined): IaHistoriqueDetails | null {
  if (!detailsJson?.trim()) {
    return null;
  }
  try {
    const raw = JSON.parse(detailsJson) as Record<string, unknown>;
    const explications = Array.isArray(raw['explications'])
      ? raw['explications'].map((item) => String(item))
      : undefined;
    const recommandations = Array.isArray(raw['recommandations'])
      ? raw['recommandations'].map((item) => String(item))
      : undefined;
    const score = typeof raw['score'] === 'number' ? raw['score'] : undefined;
    const probabiliteDefaut =
      typeof raw['probabiliteDefaut'] === 'number' ? raw['probabiliteDefaut'] : undefined;
    const zoneCode = typeof raw['zoneCode'] === 'string' ? raw['zoneCode'] : undefined;
    const detail = typeof raw['detail'] === 'string' ? raw['detail'] : undefined;

    if (
      score == null &&
      probabiliteDefaut == null &&
      !zoneCode &&
      !explications?.length &&
      !recommandations?.length &&
      !detail
    ) {
      return null;
    }

    return { score, probabiliteDefaut, zoneCode, explications, recommandations, detail };
  } catch {
    return null;
  }
}

export function hasIaHistoriqueDetails(ia: IaHistoriqueDetails | null | undefined): boolean {
  return ia != null;
}
