/** Clés renvoyées par le service Python / gestion-demande. */
export type CoherenceCorrectionKey =
  | 'cin'
  | 'revenu_mensuel'
  | 'loyer_mensuel'
  | 'anciennete_emploi_mois'
  | 'montant';

const FIELD_LABELS: Record<CoherenceCorrectionKey, string> = {
  cin: 'CIN',
  revenu_mensuel: 'Revenu mensuel net',
  loyer_mensuel: 'Loyer mensuel',
  anciennete_emploi_mois: 'Ancienneté emploi (mois)',
  montant: 'Montant du financement',
};

export interface ApplyCorrectionsResult {
  infosClientData: Record<string, unknown>;
  donneesFinancieresData: Record<string, unknown>;
  donneesFinancieresPrefill: Record<string, unknown>;
  champsCorriges: string[];
}

function toNumber(value: unknown): number | null {
  if (value === null || value === undefined || value === '') return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

/**
 * Applique les corrections OCR sur les données du formulaire commerçant.
 */
export function applyCoherenceCorrections(
  corrections: Record<string, unknown> | null | undefined,
  infosClientData: Record<string, unknown>,
  donneesFinancieresData: Record<string, unknown>
): ApplyCorrectionsResult {
  const infos = { ...infosClientData };
  const finances = { ...donneesFinancieresData };
  const champsCorriges: string[] = [];

  if (!corrections || Object.keys(corrections).length === 0) {
    return {
      infosClientData: infos,
      donneesFinancieresData: finances,
      donneesFinancieresPrefill: buildFinancesPrefill(finances),
      champsCorriges,
    };
  }

  const cin = corrections['cin'];
  if (cin != null && String(cin).trim() !== '') {
    infos['cin'] = String(cin).trim();
    champsCorriges.push(FIELD_LABELS.cin);
  }

  const revenu = toNumber(corrections['revenu_mensuel']);
  if (revenu !== null) {
    finances['revenu'] = revenu;
    finances['revenuMensuelNet'] = revenu;
    champsCorriges.push(FIELD_LABELS.revenu_mensuel);
  }

  const loyer = toNumber(corrections['loyer_mensuel']);
  if (loyer !== null) {
    finances['loyer'] = loyer;
    finances['loyerMensuel'] = loyer;
    finances['aUnLoyer'] = loyer > 0;
    champsCorriges.push(FIELD_LABELS.loyer_mensuel);
  }

  const anciennete = toNumber(corrections['anciennete_emploi_mois']);
  if (anciennete !== null) {
    infos['ancienneteEmploiMois'] = Math.max(0, Math.round(anciennete));
    finances['ancienneteEmploiMois'] = infos['ancienneteEmploiMois'];
    champsCorriges.push(FIELD_LABELS.anciennete_emploi_mois);
  }

  const montant = toNumber(corrections['montant']);
  if (montant !== null) {
    finances['montant'] = montant;
    champsCorriges.push(FIELD_LABELS.montant);
  }

  if (revenu !== null || toNumber(finances['autresRevenus']) !== null) {
    const rev = toNumber(finances['revenu']) ?? 0;
    const autres = toNumber(finances['autresRevenus'] ?? finances['autresRevenusMensuels']) ?? 0;
    finances['revenuAnnuel'] = Math.max(0, Math.round((rev + autres) * 12));
  }

  return {
    infosClientData: infos,
    donneesFinancieresData: finances,
    donneesFinancieresPrefill: buildFinancesPrefill(finances),
    champsCorriges,
  };
}

function buildFinancesPrefill(finances: Record<string, unknown>): Record<string, unknown> {
  return {
    revenu: finances['revenu'] ?? finances['revenuMensuelNet'],
    autresRevenus: finances['autresRevenus'] ?? finances['autresRevenusMensuels'] ?? 0,
    loyer: finances['loyer'] ?? finances['loyerMensuel'] ?? 0,
    mensualitesCredits: finances['mensualitesCredits'] ?? 0,
    autresChargesFixes: finances['autresChargesFixes'] ?? 0,
    credits: finances['credits'] ?? finances['encoursCredits'] ?? 0,
    montant: finances['montant'],
    ancienneteEmploiMois: finances['ancienneteEmploiMois'],
  };
}
