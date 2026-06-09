import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import type { CoherenceAnomalie } from '../models/coherence-anomalie.model';

export interface DocumentMultipart {
  typeDocument: string;
  file: File;
}

export type SituationFamilialeCode = 'CELIBATAIRE' | 'MARIE' | 'DIVORCE';

export function libelleSituationFamiliale(code: string | undefined | null): string {
  if (!code) return '-';
  const m: Record<string, string> = {
    CELIBATAIRE: 'Célibataire', MARIE: 'Marié(e)', PACSE: 'Pacsé(e)',
    DIVORCE: 'Divorcé(e)', VEUF: 'Veuf / veuve', CONCUBINAGE: 'Concubinage',
  };
  return m[code] ?? code;
}

export interface CreationDemandeCompleteRequest {
  nom: string;
  prenom: string;
  email: string;
  telephone: string;
  cin: string;
  adresse: string;
  sexe: string;
  profession: string;
  employeur: string;
  dateNaissance: string;
  situationFamiliale: SituationFamilialeCode;
  nombreEnfants: number;
  ancienneteEmploiMois: number;
  typeContrat: string;
  revenuMensuelNet: number;
  autresRevenusMensuels: number;
  revenuAnnuel: number;
  encoursCredits: number;
  loyerMensuel: number;
  mensualitesCredits: number;
  autresChargesFixes: number;
  montant: number;
  dureeMois: number;
  typeProduit: string;
  documents: DocumentMultipart[];
}

/** Réponse de /coherence (HTTP 200) */
export interface CoherenceSuccessResponse {
  coherent: boolean;
  processInstanceId?: string;
  analysisSessionId?: string;
  corrections?: Record<string, unknown>;
  alertes?: string[];
  /** Présent dès la cohérence OK (génération serveur). */
  recommandations?: string[];
}

/** Réponse de /recommandations (HTTP 200) */
export interface RecommandationsSuccessResponse {
  recommandations: string[];
  processInstanceId?: string;
}

/** Réponse de /analyse-ia (HTTP 200) — rétrocompat */
export interface AnalyseIASuccessResponse {
  recommandations: string[];
  corrections?: Record<string, unknown>;
  alertes?: string[];
  processInstanceId?: string;
}

/** Réponse de /analyse-ia (HTTP 422) */
export interface CoherenceErreurReponse {
  message: string;
  anomalies: CoherenceAnomalie[] | string[];
  corrections?: Record<string, unknown>;
}

export interface PrescoringScoreDto {
  id: number;
  probabiliteDefaut: number;
  score: number;
  zoneCode: string;
  zoneLibelle: string;
  explicationsJson: string;
  computedAt: string;
}

export interface RecommandationDto {
  id: number;
  recommandationsJson: string;
  generatedAt: string;
}

export interface DocumentDossierDto {
  id: number;
  typeDocument: string;
  objectKey: string;
  nomFichier: string;
  contentType: string;
  tailleOctets: number;
}

export interface DossierClientDto {
  id: number;
  clientId: number;
  referenceDossier: string;
  dateCreation: string;
  dateDerniereMiseAJour: string;
  situationFamiliale?: string;
  nombreEnfants?: number;
  ancienneteEmploiMois: number;
  revenuMensuelNet: number;
  autresRevenusMensuels: number;
  loyerMensuel: number;
  mensualitesCredits: number;
  autresChargesFixes: number;
  chargesMensuelles: number;
  encoursCredits: number;
  tauxEndettement: number;
  documents: DocumentDossierDto[];
}

export interface ClientIdentityLiteDto {
  id: number;
  nom: string;
  prenom: string;
  cin: string;
  telephone?: string;
  email: string;
}

export interface DemandeFinancementDto {
  id: number;
  referenceDemande: string;
  montant: number;
  dureeMois?: number;
  statut: string;
  dateCreation: string;
  dateDerniereMiseAJour: string;
  typeProduit: string;
  clientId?: number;
  clientNom?: string;
  clientPrenom?: string;
  clientCin?: string;
  commercantUserId?: number;
}

export interface HistoriqueEvenementDto {
  type: string;
  libelle: string;
  detail?: string;
  statutAvant?: string;
  statutApres?: string;
  dateEvenement: string;
}

export interface DemandeCompleteDto {
  id: number;
  referenceDemande: string;
  montant: number;
  dureeMois: number;
  statut: string;
  dateCreation: string;
  dateDerniereMiseAJour: string;
  typeProduit: string;
  client?: ClientIdentityLiteDto;
  dossierClient?: DossierClientDto;
  recommandation?: RecommandationDto;
  prescoringScore?: PrescoringScoreDto;
  historique?: HistoriqueEvenementDto[];
}

export interface DernierDossierFinancierDto {
  ancienneteEmploiMois: number;
  revenuMensuelNet: number;
  autresRevenusMensuels: number;
  loyerMensuel: number;
  mensualitesCredits: number;
  autresChargesFixes: number;
  encoursCredits: number;
}

@Injectable({ providedIn: 'root' })
export class DemandeService {

  private baseUrl          = 'http://localhost:8081/api/demandes';
  private priseEnChargeUrl = 'http://localhost:8081/api/prises-en-charge';

  constructor(private http: HttpClient) {}

  private buildAnalyseFormData(
    request: CreationDemandeCompleteRequest,
    documents: DocumentMultipart[]
  ): FormData {
    const formData = new FormData();
    const declared: Record<string, unknown> = {};
    (Object.keys(request) as (keyof CreationDemandeCompleteRequest)[]).forEach(key => {
      if (key === 'documents') return;
      const v = request[key];
      if (v !== null && v !== undefined) declared[key as string] = v;
    });
    formData.append('declared_data', JSON.stringify(declared));
    documents.forEach(doc => {
      formData.append(doc.typeDocument, doc.file, doc.file.name);
    });
    return formData;
  }

  /** Étape 1 : cohérence OCR (POST /coherence/check côté micro IA). */
  verifierCoherence(
    request: CreationDemandeCompleteRequest,
    documents: DocumentMultipart[],
    processInstanceId?: string
  ): Observable<CoherenceSuccessResponse> {
    const formData = this.buildAnalyseFormData(request, documents);
    let params = new HttpParams();
    if (processInstanceId) {
      params = params.set('process_instance_id', processInstanceId);
    }
    return this.http.post<CoherenceSuccessResponse>(
      `${this.baseUrl}/coherence`,
      formData,
      { headers: this.authHeadersNoContentType(), params }
    );
  }

  /** Étape 2 : recommandations (après cohérence OK, anomalies[] vide). */
  obtenirRecommandations(
    processInstanceId?: string,
    analysisSessionId?: string
  ): Observable<RecommandationsSuccessResponse> {
    let params = new HttpParams();
    if (processInstanceId?.trim()) {
      params = params.set('process_instance_id', processInstanceId.trim());
    }
    if (analysisSessionId?.trim()) {
      params = params.set('analysis_session_id', analysisSessionId.trim());
    }
    return this.http.post<RecommandationsSuccessResponse>(
      `${this.baseUrl}/recommandations`,
      null,
      { headers: this.authHeaders(), params }
    );
  }

  /**
   * Analyse complète en un appel (backend enchaîne cohérence puis recommandations).
   * Préférer verifierCoherence puis obtenirRecommandations pour le flux en deux temps.
   */
  analyseIA(
    request: CreationDemandeCompleteRequest,
    documents: DocumentMultipart[],
    processInstanceId?: string
  ): Observable<AnalyseIASuccessResponse> {
    const formData = this.buildAnalyseFormData(request, documents);
    let params = new HttpParams();
    if (processInstanceId) {
      params = params.set('process_instance_id', processInstanceId);
    }
    return this.http.post<AnalyseIASuccessResponse>(
      `${this.baseUrl}/analyse`,
      formData,
      { headers: this.authHeadersNoContentType(), params }
    );
  }

  /** Création en base + email consentement (après validation IA côté front). */
  creerDemande(
    request: CreationDemandeCompleteRequest,
    recommandationsJson: string = '[]',
    processInstanceId?: string
  ): Observable<DemandeFinancementDto> {
    const formData = new FormData();
    const declared: Record<string, unknown> = {};
    (Object.keys(request) as (keyof CreationDemandeCompleteRequest)[]).forEach(key => {
      if (key === 'documents') return;
      const v = request[key];
      if (v !== null && v !== undefined) {
        declared[key as string] = v;
      }
    });
    formData.append('declared_data', JSON.stringify(declared));
    request.documents.forEach((doc) => {
      formData.append(doc.typeDocument, doc.file, doc.file.name);
    });
    formData.append('recommandations_json', recommandationsJson);
    let params = new HttpParams();
    if (processInstanceId) {
      params = params.set('process_instance_id', processInstanceId);
    }

    return this.http.post<DemandeFinancementDto>(
      `${this.baseUrl}/creation-complete`,
      formData,
      { headers: this.authHeadersNoContentType(), params }
    );
  }

  // ── Autres appels (inchangés) ─────────────────────────────────────────────

  getDemandesByCommercantFromToken(): Observable<DemandeFinancementDto[]> {
    const payload = this.decodeToken();
    return this.http.get<DemandeFinancementDto[]>(
      `${this.baseUrl}/par-commercant/${payload.id}`,
      { headers: this.authHeaders() }
    );
  }

  /** Supervision admin : demandes actives en base gestion-demande. */
  getDemandesAdminEnCours(): Observable<DemandeFinancementDto[]> {
    return this.http.get<DemandeFinancementDto[]>(
      `${this.baseUrl}/admin/en-cours`,
      { headers: this.authHeaders() }
    );
  }

  getDemandeDetailById(id: number): Observable<DemandeCompleteDto> {
    return this.http.get<DemandeCompleteDto>(
      `${this.baseUrl}/${id}/detail`,
      { headers: this.authHeaders() }
    );
  }

  annulerDemande(id: number): Observable<DemandeFinancementDto> {
    return this.http.post<DemandeFinancementDto>(
      `${this.baseUrl}/${id}/annuler`,
      {},
      { headers: this.authHeaders() }
    );
  }

  renvoyerConsentement(id: number): Observable<DemandeFinancementDto> {
    return this.http.post<DemandeFinancementDto>(
      `${this.baseUrl}/${id}/renvoyer-consentement`,
      {},
      { headers: this.authHeaders() }
    );
  }

  getDemandeDetailBanqueById(id: number): Observable<DemandeCompleteDto> {
    return this.http.get<DemandeCompleteDto>(
      `${this.priseEnChargeUrl}/demandes/${id}/detail`,
      { headers: this.authHeaders() }
    );
  }

  getRecapBanqueById(id: number): Observable<DemandeCompleteDto> {
    return this.http.get<DemandeCompleteDto>(
      `${this.priseEnChargeUrl}/demandes/${id}/recap`,
      { headers: this.authHeaders() }
    );
  }

  getDocumentPresignedUrl(objectKey: string): Observable<{ url: string }> {
    const params = new HttpParams().set('objectKey', objectKey);
    return this.http.get<{ url: string }>(
      `${this.baseUrl}/documents/presigned`,
      { headers: this.authHeaders(), params }
    );
  }

  getDernierDossierFinancierParCin(cin: string): Observable<DernierDossierFinancierDto> {
    const params = new HttpParams().set('cin', cin);
    return this.http.get<DernierDossierFinancierDto>(
      `${this.baseUrl}/dossiers/dernier`,
      { headers: this.authHeaders(), params }
    );
  }

  getDemandesDisponiblesPourBanque(): Observable<DemandeFinancementDto[]> {
    return this.http.get<DemandeFinancementDto[]>(
      `${this.priseEnChargeUrl}/demandes/disponibles`,
      { headers: this.authHeaders() }
    );
  }

  getDemandesAffecteesPourBanque(): Observable<DemandeFinancementDto[]> {
    return this.http.get<DemandeFinancementDto[]>(
      `${this.priseEnChargeUrl}/demandes/verrouillees`,
      { headers: this.authHeaders() }
    );
  }

  seSaisir(demandeId: number): Observable<any> {
    return this.http.post(
      `${this.priseEnChargeUrl}/demandes/${demandeId}/se-saisir`, {},
      { headers: this.authHeaders() }
    );
  }

  accepterDemande(demandeId: number, payload?: { commentaire?: string }): Observable<DemandeCompleteDto> {
    return this.http.post<DemandeCompleteDto>(
      `${this.priseEnChargeUrl}/demandes/${demandeId}/accepter`,
      payload ?? {}, { headers: this.authHeaders() }
    );
  }

  refuserDemande(demandeId: number, payload?: { motifRefus?: string; commentaire?: string }): Observable<DemandeCompleteDto> {
    return this.http.post<DemandeCompleteDto>(
      `${this.priseEnChargeUrl}/demandes/${demandeId}/refuser`,
      payload ?? {}, { headers: this.authHeaders() }
    );
  }

  demanderComplements(demandeId: number, payload?: { commentaire?: string }): Observable<DemandeCompleteDto> {
    return this.http.post<DemandeCompleteDto>(
      `${this.priseEnChargeUrl}/demandes/${demandeId}/complements`,
      payload ?? {}, { headers: this.authHeaders() }
    );
  }

  // ─── Helpers privés ──────────────────────────────────────────────────────

  /** Headers sans Content-Type (laissé au browser pour multipart boundary) */
  private authHeadersNoContentType(): HttpHeaders {
    const token = localStorage.getItem('token');
    if (!token) throw new Error('Token JWT manquant');
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }

  private authHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    if (!token) throw new Error('Token JWT manquant');
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }

  private decodeToken(): any {
    const token = localStorage.getItem('token');
    if (!token) throw new Error('Token JWT manquant');
    const payload = JSON.parse(atob(token.split('.')[1]));
    if (payload?.id == null) throw new Error("Claim 'id' introuvable dans le JWT");
    return payload;
  }
}