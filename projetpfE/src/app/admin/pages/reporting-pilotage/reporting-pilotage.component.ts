import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../services/auth-service.service';
import { ReportingArchivageService } from '../../../services/reporting-archivage.service';
import {
  AccesPlateformeHistoriqueDto,
  ActionDemandeHistoriqueDto,
  ActionDocumentHistoriqueDto,
  DashboardReportingDto,
  DecisionFinancementHistoriqueDto,
  DossierArchiveDto,
  libelleTypeAcces,
  libelleTypeActionDemande,
  libelleTypeActionDocument,
  libelleTypeDecision,
  ReportingListFilters,
} from '../../../models/reporting.models';

type ReportingTab = 'dashboard' | 'actions-demandes' | 'actions-documents' | 'acces' | 'decisions' | 'archives';

@Component({
  selector: 'app-reporting-pilotage',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './reporting-pilotage.component.html',
  styleUrls: ['./reporting-pilotage.component.css', '../../../shared/styles/uib-list-page.css'],
  host: { class: 'page-host' },
})
export class ReportingPilotageComponent implements OnInit {
  activeTab: ReportingTab = 'dashboard';
  loading = false;
  errorMessage = '';

  dashboard: DashboardReportingDto | null = null;

  actionsDemandes: ActionDemandeHistoriqueDto[] = [];
  actionsDocuments: ActionDocumentHistoriqueDto[] = [];
  acces: AccesPlateformeHistoriqueDto[] = [];
  decisions: DecisionFinancementHistoriqueDto[] = [];
  archives: DossierArchiveDto[] = [];

  totalElements = 0;
  currentPage = 0;
  pageSize = 20;

  filterDemandeId: number | null = null;
  filterType = '';
  filterObjectKey = '';
  filterUserId: number | null = null;
  filterSuspectOnly = false;
  filterDebut = '';
  filterFin = '';

  selectedArchive: DossierArchiveDto | null = null;

  readonly libelleDecision = libelleTypeDecision;
  readonly libelleActionDemande = libelleTypeActionDemande;
  readonly libelleActionDocument = libelleTypeActionDocument;
  readonly libelleAcces = libelleTypeAcces;

  /** Espace banque : pas d’audit admin plateforme (accès utilisateurs, KPI globaux). */
  readonly isBanqueScope: boolean;
  /** Admin : traçabilité dédiée (dashboard sur /admin/dashboard). */
  readonly isTraceabiliteOnly: boolean;

  constructor(
    private readonly reportingService: ReportingArchivageService,
    private readonly authService: AuthService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {
    const scope = route.snapshot.data['scope'] as string | undefined;
    const role = this.authService.getRole()?.toUpperCase();
    this.isBanqueScope =
      scope === 'banque' || role === 'ANALYSTE_BANCAIRE' || role === 'BANQUE';
    this.isTraceabiliteOnly =
      scope === 'traceabilite' || route.snapshot.routeConfig?.path === 'traceabilite';
  }

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((qp) => {
      this.applyRouteQuery(qp);
      this.loadTabData();
    });
  }

  clearDemandeFilter(): void {
    void this.router.navigate(['/admin/traceabilite'], {
      queryParams: { tab: this.activeTab },
    });
  }

  private applyRouteQuery(qp: { get: (k: string) => string | null }): void {
    const tab = qp.get('tab') as ReportingTab | null;
    const demandeIdRaw = qp.get('demandeId');
    if (demandeIdRaw) {
      const id = Number(demandeIdRaw);
      this.filterDemandeId = !Number.isNaN(id) ? id : null;
    } else if (this.isTraceabiliteOnly) {
      this.filterDemandeId = null;
    }
    if (tab && this.canShowTab(tab)) {
      this.activeTab = tab;
      return;
    }
    if (!tab) {
      if (this.isBanqueScope) this.activeTab = 'decisions';
      else if (this.isTraceabiliteOnly) this.activeTab = 'actions-demandes';
    }
  }

  canShowTab(tab: ReportingTab): boolean {
    if (this.isBanqueScope) {
      return tab !== 'acces' && tab !== 'dashboard';
    }
    if (this.isTraceabiliteOnly) {
      return tab !== 'dashboard';
    }
    return true;
  }

  setTab(tab: ReportingTab): void {
    if (!this.canShowTab(tab)) return;
    this.activeTab = tab;
    this.currentPage = 0;
    this.errorMessage = '';
    this.selectedArchive = null;
    this.loadTabData();
  }

  search(): void {
    this.currentPage = 0;
    this.loadTabData();
  }

  prevPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.loadTabData();
    }
  }

  nextPage(): void {
    if ((this.currentPage + 1) * this.pageSize < this.totalElements) {
      this.currentPage++;
      this.loadTabData();
    }
  }

  private filtersBase(): ReportingListFilters {
    return {
      demandeId: this.filterDemandeId,
      type: this.filterType || null,
      objectKey: this.filterObjectKey || null,
      userId: this.filterUserId,
      suspectOnly: this.filterSuspectOnly,
      debut: this.toIsoDateTime(this.filterDebut),
      fin: this.toIsoDateTime(this.filterFin),
      page: this.currentPage,
      size: this.pageSize,
    };
  }

  private loadTabData(): void {
    this.loading = true;
    this.errorMessage = '';

    switch (this.activeTab) {
      case 'dashboard':
        this.reportingService.getDashboard().subscribe({
          next: (d) => { this.dashboard = d; this.loading = false; },
          error: (e) => this.handleError(e),
        });
        break;
      case 'actions-demandes':
        this.reportingService.getActionsDemandes(this.filtersBase()).subscribe({
          next: (p) => this.applyPage(p.content, p.totalElements),
          error: (e) => this.handleError(e),
        });
        break;
      case 'actions-documents':
        this.reportingService.getActionsDocuments(this.filtersBase()).subscribe({
          next: (p) => this.applyPage(p.content, p.totalElements),
          error: (e) => this.handleError(e),
        });
        break;
      case 'acces':
        this.reportingService.getAcces(this.filtersBase()).subscribe({
          next: (p) => {
            this.acces = p.content;
            this.totalElements = p.totalElements;
            this.loading = false;
          },
          error: (e) => this.handleError(e),
        });
        break;
      case 'decisions':
        this.reportingService.getDecisions(this.filtersBase()).subscribe({
          next: (p) => {
            this.decisions = p.content;
            this.totalElements = p.totalElements;
            this.loading = false;
          },
          error: (e) => this.handleError(e),
        });
        break;
      case 'archives':
        this.reportingService.getArchives(this.filtersBase()).subscribe({
          next: (p) => {
            this.archives = p.content;
            this.totalElements = p.totalElements;
            this.loading = false;
          },
          error: (e) => this.handleError(e),
        });
        break;
    }
  }

  private applyPage<T>(content: T[], total: number): void {
    if (this.activeTab === 'actions-demandes') {
      this.actionsDemandes = content as ActionDemandeHistoriqueDto[];
    } else if (this.activeTab === 'actions-documents') {
      this.actionsDocuments = content as ActionDocumentHistoriqueDto[];
    }
    this.totalElements = total;
    this.loading = false;
  }

  viewArchiveDetail(demandeId: number): void {
    this.reportingService.getArchiveByDemandeId(demandeId).subscribe({
      next: (a) => (this.selectedArchive = a),
      error: (e) => this.handleError(e),
    });
  }

  parseSnapshot(json: string): unknown {
    try {
      return JSON.parse(json);
    } catch {
      return json;
    }
  }

  repartitionEntries(map: Record<string, number> | undefined): { key: string; value: number }[] {
    if (!map) return [];
    return Object.entries(map).map(([key, value]) => ({ key, value }));
  }

  /** ISO string, epoch ms, or Jackson LocalDateTime array (legacy API responses). */
  asApiDate(value: unknown): Date | null {
    if (value == null || value === '') return null;
    if (Array.isArray(value)) {
      const [y, m, d, h = 0, min = 0, s = 0, nano = 0] = value as number[];
      if (y == null || m == null || d == null) return null;
      return new Date(y, m - 1, d, h, min, s, Math.floor(nano / 1_000_000));
    }
    if (typeof value === 'string') {
      const parts = value.split(',').map((p) => Number(p.trim()));
      if (parts.length >= 3 && parts.every((n) => !Number.isNaN(n))) {
        const [y, m, d, h = 0, min = 0, s = 0, nano = 0] = parts;
        return new Date(y, m - 1, d, h, min, s, Math.floor(nano / 1_000_000));
      }
      const parsed = new Date(value);
      return Number.isNaN(parsed.getTime()) ? null : parsed;
    }
    if (typeof value === 'number') {
      const parsed = new Date(value);
      return Number.isNaN(parsed.getTime()) ? null : parsed;
    }
    return null;
  }

  private toIsoDateTime(local: string): string | null {
    if (!local) return null;
    const d = new Date(local);
    return Number.isNaN(d.getTime()) ? null : d.toISOString().slice(0, 19);
  }

  private handleError(err: unknown): void {
    this.loading = false;
    const body = (err as { error?: { message?: string } })?.error;
    this.errorMessage = body?.message ?? 'Impossible de charger les données (vérifiez que reporting-archivage tourne sur le port 8083).';
  }
}
