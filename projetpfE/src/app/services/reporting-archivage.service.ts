import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AccesPlateformeHistoriqueDto,
  ActionDemandeHistoriqueDto,
  ActionDocumentHistoriqueDto,
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
    return this.http.get<DashboardReportingDto>(`${this.reportingUrl}/dashboard`, {
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
    return params;
  }

  private authHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    if (!token) throw new Error('Token JWT manquant');
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }
}
