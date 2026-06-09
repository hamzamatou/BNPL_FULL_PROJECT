/** Types alignés sur le microservice reporting-archivage (port 8083). */

export type TypeDecisionFinancement =
  | 'ROUTAGE'
  | 'PRISE_EN_CHARGE'
  | 'ACCEPTEE'
  | 'REFUSEE'
  | 'DEMANDE_COMPLEMENTS'
  | 'SCORING_IA'
  | 'AUTRE';

export type TypeActionDemande =
  | 'CREATION'
  | 'CONSENTEMENT'
  | 'SCORING'
  | 'ROUTAGE'
  | 'RECOMMANDATION'
  | 'PRISE_EN_CHARGE'
  | 'ACCEPTION'
  | 'REFUS'
  | 'COMPLEMENTS'
  | 'CLOTURE'
  | 'AUTRE';

export type TypeActionDocument =
  | 'UPLOAD'
  | 'CONSULTATION'
  | 'TELECHARGEMENT'
  | 'SUPPRESSION'
  | 'VERIFICATION_OCR'
  | 'AUTRE';

export type TypeAccesPlateforme =
  | 'CONNEXION'
  | 'DECONNEXION'
  | 'APPEL_API'
  | 'ECHEC_AUTH'
  | 'AUTRE';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first?: boolean;
  last?: boolean;
}

export interface BanqueDashboardDto {
  decisions24h: number;
  acceptees: number;
  refusees: number;
  complements: number;
  prisesEnCharge: number;
  repartitionDecisionsParType: Record<string, number>;
  dernieresDecisions: BanqueDecisionResumeDto[];
}

export interface BanqueDecisionResumeDto {
  id: number;
  demandeId: number;
  referenceDemande: string | null;
  typeDecision: string;
  libelle: string;
  dateDecision: string;
}

export interface DashboardReportingDto {
  actionsDemandes24h: number;
  decisionsFinancement24h: number;
  accesSuspects24h: number;
  dossiersArchivesTotal: number;
  dossiersArchives30j: number;
  repartitionActionsParType: Record<string, number>;
  repartitionDecisionsParType: Record<string, number>;
  dernieresActions: ActionDemandeResumeDto[];
  demandesTotal: number;
  demandesCeMois: number;
  montantTotalDemande: number;
  montantMoyenDemande: number;
  clientsInscrits: number;
  commercantsPartenaires: number;
  banquesPartenaires: number;
  utilisateursActifs: number;
  utilisateursTotal: number;
  demandesAcceptees: number;
  demandesRefusees: number;
  tauxAcceptationPct: number;
  demandesEnCoursAnalyse: number;
  demandesCloturees: number;
  scoreMoyenPrescoring: number;
  prescoringRisqueFaible: number;
  prescoringRisqueMoyen: number;
  prescoringRisqueEleve: number;
  demandesRoutees: number;
  reponsesBancairesRecues: number;
  tempsMoyenTraitementHeures: number | null;
  repartitionPrescoringParZone: Record<string, number>;
  evolutionDemandesParJour: Record<string, number>;
  repartitionStatuts: Record<string, number>;
  tauxAcceptationParBanque: Record<string, number>;
  demandesParCommercant: Record<string, number>;
}

export interface ActionDemandeResumeDto {
  id: number;
  demandeId: number;
  referenceDemande: string | null;
  typeAction: string;
  libelle: string;
  acteurEmail: string | null;
  dateAction: string;
}

export interface DecisionFinancementHistoriqueDto {
  id: number;
  demandeId: number;
  referenceDemande: string | null;
  typeDecision: TypeDecisionFinancement;
  libelle: string;
  detailsJson: string | null;
  acteurUserId: number | null;
  acteurEmail: string | null;
  acteurRole: string | null;
  etapeWorkflow: string | null;
  correlationId: string | null;
  dateDecision: string;
  dateEnregistrement: string;
}

export interface ActionDemandeHistoriqueDto {
  id: number;
  demandeId: number;
  referenceDemande: string | null;
  typeAction: TypeActionDemande;
  libelle: string;
  statutAvant: string | null;
  statutApres: string | null;
  acteurUserId: number | null;
  acteurEmail: string | null;
  acteurRole: string | null;
  detailsJson: string | null;
  correlationId: string | null;
  dateAction: string;
  dateEnregistrement: string;
}

export interface ActionDocumentHistoriqueDto {
  id: number;
  demandeId: number;
  referenceDemande: string | null;
  documentId: number | null;
  objectKey: string | null;
  typeDocument: string | null;
  typeAction: TypeActionDocument;
  libelle: string;
  acteurUserId: number | null;
  acteurEmail: string | null;
  acteurRole: string | null;
  detailsJson: string | null;
  correlationId: string | null;
  dateAction: string;
  dateEnregistrement: string;
}

export interface AccesPlateformeHistoriqueDto {
  id: number;
  userId: number | null;
  userEmail: string | null;
  userRole: string | null;
  typeAcces: TypeAccesPlateforme;
  description: string;
  adresseIp: string | null;
  userAgent: string | null;
  endpoint: string | null;
  methodeHttp: string | null;
  suspect: boolean;
  detailsJson: string | null;
  correlationId: string | null;
  dateAcces: string;
  dateEnregistrement: string;
}

export interface DossierArchiveDto {
  id: number;
  demandeId: number;
  referenceDemande: string | null;
  clientId: number | null;
  cinClient: string | null;
  statutFinal: string;
  montant: number | null;
  dureeMois: number | null;
  typeProduit: string | null;
  snapshotJson: string;
  documentsMetadataJson: string | null;
  archiveParUserId: number | null;
  archiveParEmail: string | null;
  dateCloture: string;
  dateArchivage: string;
  dateEnregistrement: string;
}

/** Filtres communs pour les listes paginées. */
export interface ReportingListFilters {
  demandeId?: number | null;
  type?: string | null;
  objectKey?: string | null;
  userId?: number | null;
  acteurUserId?: number | null;
  suspectOnly?: boolean;
  debut?: string | null;
  fin?: string | null;
  page?: number;
  size?: number;
}

export function libelleTypeDecision(code: string | null | undefined): string {
  const m: Record<string, string> = {
    ROUTAGE: 'Routage',
    PRISE_EN_CHARGE: 'Prise en charge',
    ACCEPTEE: 'Acceptée',
    REFUSEE: 'Refusée',
    DEMANDE_COMPLEMENTS: 'Compléments demandés',
    SCORING_IA: 'Scoring IA',
    AUTRE: 'Autre',
  };
  return code ? (m[code] ?? code) : '—';
}

export function libelleTypeActionDemande(code: string | null | undefined): string {
  const m: Record<string, string> = {
    CREATION: 'Création',
    CONSENTEMENT: 'Consentement',
    SCORING: 'Scoring',
    ROUTAGE: 'Routage banque',
    RECOMMANDATION: 'Recommandations IA',
    PRISE_EN_CHARGE: 'Prise en charge',
    ACCEPTION: 'Acceptation',
    REFUS: 'Refus',
    COMPLEMENTS: 'Compléments',
    CLOTURE: 'Clôture',
    AUTRE: 'Autre',
  };
  return code ? (m[code] ?? code) : '—';
}

export function libelleTypeActionDocument(code: string | null | undefined): string {
  const m: Record<string, string> = {
    UPLOAD: 'Upload',
    CONSULTATION: 'Consultation',
    TELECHARGEMENT: 'Téléchargement',
    SUPPRESSION: 'Suppression',
    VERIFICATION_OCR: 'Vérification OCR',
    AUTRE: 'Autre',
  };
  return code ? (m[code] ?? code) : '—';
}

export function libelleTypeAcces(code: string | null | undefined): string {
  const m: Record<string, string> = {
    CONNEXION: 'Connexion',
    DECONNEXION: 'Déconnexion',
    APPEL_API: 'Appel API',
    ECHEC_AUTH: 'Échec authentification',
    AUTRE: 'Autre',
  };
  return code ? (m[code] ?? code) : '—';
}
