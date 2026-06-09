/** Parse recommandationsJson stocké en base. */
export function parseRecommandationsJson(raw?: string | null): string[] {
  if (!raw?.trim()) return [];
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((x): x is string => typeof x === 'string' && x.trim().length > 0);
  } catch {
    return [];
  }
}

/** Message affiché au commerçant : cohérence OK, sans recommandation d'ajustement. */
export const MESSAGE_DOSSIER_CONFORME_FICHE_PAIE =
  'Dossier conforme. Il faut juste vérifier les dates des fiches de paie, s\'il vous plaît.';

/** Texte indiquant une demande conforme (règle 40 %). */
export function isDemandeConformeMessage(texte: string): boolean {
  const low = texte.toLowerCase();
  return (
    low.includes('demande conforme') ||
    low.includes('dossier conforme') ||
    low.includes('fiches de paie') ||
    (low.includes('aucun ajustement') && low.includes('plafond'))
  );
}

export function listeIndiqueDemandeConforme(recommandations: string[]): boolean {
  return (
    recommandations.length === 0 ||
    recommandations.some(isDemandeConformeMessage)
  );
}

/** Après analyse IA réussie : message par défaut si aucune recommandation métier. */
export function normaliserRecommandationsApresAnalyse(recommandations: string[]): string[] {
  const cleaned = (recommandations ?? []).filter(
    (x): x is string => typeof x === 'string' && x.trim().length > 0
  );
  if (cleaned.length === 0) {
    return [MESSAGE_DOSSIER_CONFORME_FICHE_PAIE];
  }
  return cleaned;
}
