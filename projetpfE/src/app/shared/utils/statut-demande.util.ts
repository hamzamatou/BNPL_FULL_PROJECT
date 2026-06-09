/** Libellés affichés pour les statuts demande (diagramme d'états BNPL). */
export function libelleStatutDemande(statut?: string | null): string {
  const s = (statut || '').toUpperCase();
  const labels: Record<string, string> = {
    CREE: 'Créée',
    EN_ATTENTE_CONSENTEMENT: 'En attente consentement',
    EN_COURS_PRESCORING: 'Prescoring en cours',
    SOUMISE: 'Soumise',
    EN_COURS_ANALYSE: 'En cours d\'analyse',
    EN_ATTENTE_COMPLEMENT: 'En attente compléments',
    ACCEPTEE: 'Acceptée',
    REFUSEE: 'Refusée',
    REJETEE_AUTO: 'Rejetée (auto)',
    REJET_AUTO: 'Rejetée (auto)',
    ANNULEE: 'Annulée',
    CLOTUREE: 'Clôturée',
  };
  return labels[s] || statut || '—';
}

export function badgeClassStatutDemande(statut?: string | null): 'wait' | 'analysis' | 'sent' | 'danger' | 'muted' {
  const s = (statut || '').toUpperCase();
  if (s === 'EN_COURS_ANALYSE' || s === 'EN_ATTENTE_COMPLEMENT' || s === 'EN_COURS_PRESCORING') {
    return 'analysis';
  }
  if (s === 'SOUMISE' || s === 'ACCEPTEE') {
    return 'sent';
  }
  if (s === 'REFUSEE' || s === 'REJETEE_AUTO' || s === 'REJET_AUTO' || s === 'ANNULEE') {
    return 'danger';
  }
  if (s === 'CLOTUREE') {
    return 'muted';
  }
  return 'wait';
}
