import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DocumentMultipart {
  typeDocument: string;
  file: File;
}

/** Aligné sur l'énumération domaine `SituationFamiliale` (diagramme de classes). */
export type SituationFamilialeCode =
  | 'CELIBATAIRE'
  | 'MARIE'
  | 'PACSE'
  | 'DIVORCE'
  | 'VEUF'
  | 'CONCUBINAGE';

export function libelleSituationFamiliale(code: string | undefined | null): string {
  if (!code) return '-';
  const m: Record<string, string> = {
    CELIBATAIRE: 'Célibataire',
    MARIE: 'Marié(e)',
    PACSE: 'Pacsé(e)',
    DIVORCE: 'Divorcé(e)',
    VEUF: 'Veuf / veuve',
    CONCUBINAGE: 'Concubinage',
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
  situationFamiliale: SituationFamilialeCode;
  nombreEnfants: number;
  typeContrat: string;
  dateNaissance: string;
  ancienneteEmploiMois: number;
  revenuMensuelNet: number;
  autresRevenusMensuels: number;
  encoursCredits: number;
  loyerMensuel: number;
  mensualitesCredits: number;
  autresChargesFixes: number;
  montant: number;
  dureeMois: number;
  typeProduit: string;
  revenuAnnuel?: number;
  aUnLoyer?: boolean;
  aDesCredits?: boolean;
  documents: DocumentMultipart[];
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
  /** Présent si le backend renvoie le domaine (cohérent avec le diagramme). */
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

export interface DernierDossierFinancierDto {
  ancienneteEmploiMois: number;
  revenuMensuelNet: number;
  autresRevenusMensuels: number;
  loyerMensuel: number;
  mensualitesCredits: number;
  autresChargesFixes: number;
  encoursCredits: number;
}

export interface ClientIdentityLiteDto {
  id: number;
  nom: string;
  prenom: string;
  cin: string;
  telephone?: string;
  email: string;
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
}

@Injectable({
  providedIn: 'root',
})
export class DemandeService {
  private baseUrl = 'http://localhost:8081/api/demandes';
  /** Listes / détail / décisions banque (backend {@code PriseEnChargeController}). */
  private priseEnChargeBaseUrl = 'http://localhost:8081/api/prises-en-charge';

  constructor(private http: HttpClient) {}

  creerDemande(request: CreationDemandeCompleteRequest): Observable<any> {
    const formData = new FormData();

    Object.keys(request).forEach((key) => {
      const value = (request as any)[key];
      if (key !== 'documents' && value !== null && value !== undefined) {
        formData.append(key, value.toString());
      }
    });

    if (request.documents && request.documents.length > 0) {
      request.documents.forEach((doc, index) => {
        formData.append(`documents[${index}].typeDocument`, doc.typeDocument);
        formData.append(`documents[${index}].file`, doc.file, doc.file.name);
      });
    }

    const token = localStorage.getItem('token');
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.post(`${this.baseUrl}/creation-complete`, formData, { headers });
  }

  getDemandesByCommercantFromToken(): Observable<DemandeFinancementDto[]> {
    const token = localStorage.getItem('token');
    if (!token) {
      throw new Error('Token JWT manquant');
    }

    const payload = JSON.parse(atob(token.split('.')[1]));
    const id = payload?.id;
    if (id === null || id === undefined) {
      throw new Error("Claim 'id' introuvable dans le JWT");
    }

    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });
    const params = new HttpParams().set('clientId', String(id));

    return this.http.get<DemandeFinancementDto[]>(`${this.baseUrl}/par-client`, {
      headers,
      params,
    });
  }

  getDemandeDetailById(id: number): Observable<DemandeCompleteDto> {
    const token = localStorage.getItem('token');
    if (!token) {
      throw new Error('Token JWT manquant');
    }
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });
    return this.http.get<DemandeCompleteDto>(`${this.baseUrl}/${id}/detail`, { headers });
  }

  getDemandeDetailBanqueById(id: number): Observable<DemandeCompleteDto> {
    const token = localStorage.getItem('token');
    if (!token) {
      throw new Error('Token JWT manquant');
    }

    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.get<DemandeCompleteDto>(`${this.priseEnChargeBaseUrl}/demandes/${id}/detail`, {
      headers,
    });
  }

  getDocumentPresignedUrl(objectKey: string): Observable<{ url: string }> {
    const token = localStorage.getItem('token');
    if (!token) {
      throw new Error('Token JWT manquant');
    }

    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    const params = new HttpParams().set('objectKey', objectKey);
    return this.http.get<{ url: string }>(`${this.baseUrl}/documents/presigned`, {
      headers,
      params,
    });
  }

  getDemandesDisponiblesPourBanque(): Observable<DemandeFinancementDto[]> {
    const token = localStorage.getItem('token');
    if (!token) {
      throw new Error('Token JWT manquant');
    }

    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.get<DemandeFinancementDto[]>(
      `${this.priseEnChargeBaseUrl}/demandes/disponibles`,
      {
        headers,
      }
    );
  }

  seSaisir(demandeId: number): Observable<any> {
    const token = localStorage.getItem('token');
    if (!token) {
      throw new Error('Token JWT manquant');
    }

    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.post(`${this.priseEnChargeBaseUrl}/demandes/${demandeId}/se-saisir`, {}, {
      headers,
    });
  }

  getDemandesAffecteesPourBanque(): Observable<DemandeFinancementDto[]> {
    const token = localStorage.getItem('token');
    if (!token) {
      throw new Error('Token JWT manquant');
    }

    const headers = new HttpHeaders({
      Authorization: `Bearer ${token}`,
    });

    return this.http.get<DemandeFinancementDto[]>(
      `${this.priseEnChargeBaseUrl}/demandes/verrouillees`,
      {
        headers,
      }
    );
  }

  getDernierDossierFinancierParCin(cin: string): Observable<DernierDossierFinancierDto> {
    const token = localStorage.getItem('token');
    if (!token) throw new Error('Token JWT manquant');

    const headers = new HttpHeaders({ Authorization: `Bearer ${token}` });
    const params = new HttpParams().set('cin', cin);

    return this.http.get<DernierDossierFinancierDto>(`${this.baseUrl}/dossiers/dernier`, {
      headers,
      params,
    });
  }

  accepterDemande(
    demandeId: number,
    payload?: { commentaire?: string }
  ): Observable<DemandeCompleteDto> {
    const token = localStorage.getItem('token');
    if (!token) throw new Error('Token JWT manquant');
    const headers = new HttpHeaders({ Authorization: `Bearer ${token}` });
    return this.http.post<DemandeCompleteDto>(
      `${this.priseEnChargeBaseUrl}/demandes/${demandeId}/accepter`,
      payload ?? {},
      { headers }
    );
  }

  refuserDemande(
    demandeId: number,
    payload?: { motifRefus?: string; commentaire?: string }
  ): Observable<DemandeCompleteDto> {
    const token = localStorage.getItem('token');
    if (!token) throw new Error('Token JWT manquant');
    const headers = new HttpHeaders({ Authorization: `Bearer ${token}` });
    return this.http.post<DemandeCompleteDto>(
      `${this.priseEnChargeBaseUrl}/demandes/${demandeId}/refuser`,
      payload ?? {},
      { headers }
    );
  }

  demanderComplements(
    demandeId: number,
    payload?: { commentaire?: string }
  ): Observable<DemandeCompleteDto> {
    const token = localStorage.getItem('token');
    if (!token) throw new Error('Token JWT manquant');
    const headers = new HttpHeaders({ Authorization: `Bearer ${token}` });
    return this.http.post<DemandeCompleteDto>(
      `${this.priseEnChargeBaseUrl}/demandes/${demandeId}/complements`,
      payload ?? {},
      { headers }
    );
  }
}
