import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DocumentMultipart {
  typeDocument: string;
  file: File;
}

export type SituationFamilialeCode =
  | 'CELIBATAIRE' | 'MARIE' | 'PACSE'
  | 'DIVORCE'     | 'VEUF'  | 'CONCUBINAGE';

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

/** Réponse de /analyse-ia (HTTP 200) */
export interface AnalyseIASuccessResponse {
  recommandations: string[];
}

/** Réponse de /analyse-ia (HTTP 422) et /creation-complete erreur */
export interface CoherenceErreurReponse {
  message: string;
  anomalies: string[];
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
  statut: string;
  dateCreation: string;
  dateDerniereMiseAJour: string;
  typeProduit: string;
  clientId?: number;
  clientNom?: string;
  clientPrenom?: string;
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

  // ── Étape IA : cohérence + recommandations (AVANT création) ─────────────────
  //
  // Envoie les documents et les données déclarées au backend.
  // HTTP 200 → { recommandations: string[] }
  // HTTP 422 → { message, anomalies: string[] }  (anomalies bloquantes)

  analyseIA(
    request: CreationDemandeCompleteRequest,
    documents: DocumentMultipart[]
  ): Observable<AnalyseIASuccessResponse> {
    const formData = new FormData();

    // Sérialiser les champs scalaires en JSON pour declared_data
    const declared: Record<string, any> = {};
    (Object.keys(request) as (keyof CreationDemandeCompleteRequest)[]).forEach(key => {
      if (key === 'documents') return;
      const v = request[key];
      if (v !== null && v !== undefined) declared[key as string] = v;
    });
    formData.append('declared_data', JSON.stringify(declared));

    // Joindre les fichiers sous leur nom technique Python
    documents.forEach(doc => {
      formData.append(doc.typeDocument, doc.file, doc.file.name);
    });

    return this.http.post<AnalyseIASuccessResponse>(
      `${this.baseUrl}/analyse-ia`,
      formData,
      { headers: this.authHeadersNoContentType() }
    );
  }

  // ── Création de la demande (après consentement validé par le commerçant) ──────
  //
  // recommandationsJson : JSON.stringify(string[]) — résultat de analyseIA()
  // Le backend persiste les recommandations ET envoie l'email de consentement.

  creerDemande(
    request: CreationDemandeCompleteRequest,
    recommandationsJson: string = '[]'
  ): Observable<DemandeFinancementDto> {
    const formData = new FormData();

    (Object.keys(request) as (keyof CreationDemandeCompleteRequest)[]).forEach(key => {
      if (key === 'documents') return;
      const value = request[key];
      if (value !== null && value !== undefined) formData.append(key, String(value));
    });

    // Passer les recommandations calculées lors de analyseIA()
    formData.append('recommandations_json', recommandationsJson);

    request.documents.forEach((doc, index) => {
      formData.append(`documents[${index}].typeDocument`, doc.typeDocument);
      formData.append(`documents[${index}].file`, doc.file, doc.file.name);
    });

    return this.http.post<DemandeFinancementDto>(
      `${this.baseUrl}/creation-complete`,
      formData,
      { headers: this.authHeadersNoContentType() }
    );
  }

  // ── Autres appels (inchangés) ─────────────────────────────────────────────

  getDemandesByCommercantFromToken(): Observable<DemandeFinancementDto[]> {
    const payload = this.decodeToken();
    const params  = new HttpParams().set('clientId', String(payload.id));
    return this.http.get<DemandeFinancementDto[]>(
      `${this.baseUrl}/par-client`,
      { headers: this.authHeaders(), params }
    );
  }

  getDemandeDetailById(id: number): Observable<DemandeCompleteDto> {
    return this.http.get<DemandeCompleteDto>(
      `${this.baseUrl}/${id}/detail`,
      { headers: this.authHeaders() }
    );
  }

  getDemandeDetailBanqueById(id: number): Observable<DemandeCompleteDto> {
    return this.http.get<DemandeCompleteDto>(
      `${this.priseEnChargeUrl}/demandes/${id}/detail`,
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