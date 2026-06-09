import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  AccesPlateformeHistoriqueDto,
  ActionDemandeHistoriqueDto,
  ActionDocumentHistoriqueDto,
  BanqueDashboardDto,
  DashboardReportingDto,
  DecisionFinancementHistoriqueDto,
  DossierArchiveDto,
  PageResponse,
  ReportingListFilters,
} from '../models/reporting.models';

@Injectable({ providedIn: 'root' })
export class ReportingArchivageService {
  private readonly reportingUrl = 'http://localhost:8083/api/reporting';
  private readonly archivageUrl = 'http://localhost:8083/api/archivage';

  constructor(private readonly http: HttpClient) {}

  getDashboard(): Observable<DashboardReportingDto> {
    return this.http
      .get<DashboardReportingDto>(`${this.reportingUrl}/dashboard`, {
        headers: this.authHeaders(),
      })
      .pipe(map((raw) => this.normalizeDashboard(raw)));
  }

  /** Conversion JSON → types TS (pas de calcul métier). */
  private normalizeDashboard(raw: DashboardReportingDto): DashboardReportingDto {
    const n = (v: unknown): number => {
      if (v == null || v === '') return 0;
      const x = Number(v);
      return Number.isFinite(x) ? x : 0;
    };
    const mapObj = (v: Record<string, number> | undefined): Record<string, number> => {
      if (!v) return {};
      const out: Record<string, number> = {};
      for (const [k, val] of Object.entries(v)) out[k] = n(val);
      return out;
    };
    return {
      ...raw,
      actionsDemandes24h: n(raw.actionsDemandes24h),
      decisionsFinancement24h: n(raw.decisionsFinancement24h),
      accesSuspects24h: n(raw.accesSuspects24h),
      dossiersArchivesTotal: n(raw.dossiersArchivesTotal),
      dossiersArchives30j: n(raw.dossiersArchives30j),
      repartitionActionsParType: mapObj(raw.repartitionActionsParType),
      repartitionDecisionsParType: mapObj(raw.repartitionDecisionsParType),
      dernieresActions: raw.dernieresActions ?? [],
      demandesTotal: n(raw.demandesTotal),
      demandesCeMois: n(raw.demandesCeMois),
      montantTotalDemande: n(raw.montantTotalDemande),
      montantMoyenDemande: n(raw.montantMoyenDemande),
      clientsInscrits: n(raw.clientsInscrits),
      commercantsPartenaires: n(raw.commercantsPartenaires),
      banquesPartenaires: n(raw.banquesPartenaires),
      utilisateursActifs: n(raw.utilisateursActifs),
      utilisateursTotal: n(raw.utilisateursTotal),
      demandesAcceptees: n(raw.demandesAcceptees),
      demandesRefusees: n(raw.demandesRefusees),
      tauxAcceptationPct: n(raw.tauxAcceptationPct),
      demandesEnCoursAnalyse: n(raw.demandesEnCoursAnalyse),
      demandesCloturees: n(raw.demandesCloturees),
      scoreMoyenPrescoring: n(raw.scoreMoyenPrescoring),
      prescoringRisqueFaible: n(raw.prescoringRisqueFaible),
      prescoringRisqueMoyen: n(raw.prescoringRisqueMoyen),
      prescoringRisqueEleve: n(raw.prescoringRisqueEleve),
      demandesRoutees: n(raw.demandesRoutees),
      reponsesBancairesRecues: n(raw.reponsesBancairesRecues),
      tempsMoyenTraitementHeures:
        raw.tempsMoyenTraitementHeures == null ? null : n(raw.tempsMoyenTraitementHeures),
      repartitionPrescoringParZone: mapObj(raw.repartitionPrescoringParZone),
      evolutionDemandesParJour: mapObj(raw.evolutionDemandesParJour),
      repartitionStatuts: mapObj(raw.repartitionStatuts),
      tauxAcceptationParBanque: mapObj(raw.tauxAcceptationParBanque),
      demandesParCommercant: mapObj(raw.demandesParCommercant),
    };
  }

  getDashboardBanque(): Observable<BanqueDashboardDto> {
    return this.http.get<BanqueDashboardDto>(`${this.reportingUrl}/dashboard/banque`, {
      headers: this.authHeaders(),
    });
  }

  getActionsDemandes(filters: ReportingListFilters = {}): Observable<PageResponse<ActionDemandeHistoriqueDto>> {
    return this.http.get<PageResponse<ActionDemandeHistoriqueDto>>(
      `${this.reportingUrl}/actions-demandes`,
      { headers: this.authHeaders(), params: this.toParams(filters) }
    );
  }

  getActionsDocuments(filters: ReportingListFilters = {}): Observable<PageResponse<ActionDocumentHistoriqueDto>> {
    return this.http.get<PageResponse<ActionDocumentHistoriqueDto>>(
      `${this.reportingUrl}/actions-documents`,
      { headers: this.authHeaders(), params: this.toParams(filters, true) }
    );
  }

  getAcces(filters: ReportingListFilters = {}): Observable<PageResponse<AccesPlateformeHistoriqueDto>> {
    let params = this.toParams(filters);
    if (filters.suspectOnly) {
      params = params.set('suspectOnly', 'true');
    }
    if (filters.userId != null) {
      params = params.set('userId', String(filters.userId));
    }
    return this.http.get<PageResponse<AccesPlateformeHistoriqueDto>>(
      `${this.reportingUrl}/acces`,
      { headers: this.authHeaders(), params }
    );
  }

  getDecisions(filters: ReportingListFilters = {}): Observable<PageResponse<DecisionFinancementHistoriqueDto>> {
    return this.http.get<PageResponse<DecisionFinancementHistoriqueDto>>(
      `${this.reportingUrl}/decisions`,
      { headers: this.authHeaders(), params: this.toParams(filters) }
    );
  }

  getArchives(filters: ReportingListFilters = {}): Observable<PageResponse<DossierArchiveDto>> {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20));
    if (filters.debut) params = params.set('debut', filters.debut);
    if (filters.fin) params = params.set('fin', filters.fin);
    return this.http.get<PageResponse<DossierArchiveDto>>(
      `${this.archivageUrl}/dossiers`,
      { headers: this.authHeaders(), params }
    );
  }

  getArchiveByDemandeId(demandeId: number): Observable<DossierArchiveDto> {
    return this.http.get<DossierArchiveDto>(
      `${this.archivageUrl}/dossiers/${demandeId}`,
      { headers: this.authHeaders() }
    );
  }

  private toParams(filters: ReportingListFilters, withObjectKey = false): HttpParams {
    let params = new HttpParams()
      .set('page', String(filters.page ?? 0))
      .set('size', String(filters.size ?? 20));
    if (filters.demandeId != null) params = params.set('demandeId', String(filters.demandeId));
    if (filters.type) params = params.set('type', filters.type);
    if (withObjectKey && filters.objectKey) params = params.set('objectKey', filters.objectKey);
    if (filters.debut) params = params.set('debut', filters.debut);
    if (filters.fin) params = params.set('fin', filters.fin);
    if (filters.acteurUserId != null) params = params.set('acteurUserId', String(filters.acteurUserId));
    return params;
  }

  private authHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    if (!token) throw new Error('Token JWT manquant');
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }
}
