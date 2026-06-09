import { CommonModule, DatePipe, NgClass } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ReportingArchivageService } from '../../../services/reporting-archivage.service';
import { libelleTypeDecision } from '../../../models/reporting.models';

export interface DemandeSupervisionRow {
  demandeId: number;
  reference: string;
  dernierType: string;
  dernierLibelle: string;
  derniereDate: string;
  acteurEmail: string | null;
  nbEvenements: number;
}

@Component({
  selector: 'app-admin-demandes',
  standalone: true,
  imports: [CommonModule, NgClass, FormsModule, DatePipe],
  templateUrl: './admin-demandes.component.html',
  styleUrls: ['./admin-demandes.component.css'],
  host: { class: 'page-host' },
})
export class AdminDemandesComponent implements OnInit {
  loading = false;
  errorMessage = '';
  demandes: DemandeSupervisionRow[] = [];
  searchTerm = '';

  readonly libelleDecision = libelleTypeDecision;

  constructor(
    private readonly reportingService: ReportingArchivageService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.load();
  }

  get displayedDemandes(): DemandeSupervisionRow[] {
    const q = this.searchTerm.trim().toLowerCase();
    if (!q) return this.demandes;
    return this.demandes.filter((d) => {
      const ref = d.reference.toLowerCase();
      return (
        ref.includes(q) ||
        (d.dernierLibelle || '').toLowerCase().includes(q) ||
        (d.acteurEmail || '').toLowerCase().includes(q)
      );
    });
  }

  openTraceabilite(demandeId: number): void {
    void this.router.navigate(['/admin/traceabilite'], {
      queryParams: { demandeId: String(demandeId), tab: 'actions-demandes' },
    });
  }

  decisionBadgeClass(type: string): string {
    const t = (type || '').toUpperCase();
    if (t === 'ACCEPTEE') return 'badge-accept';
    if (t === 'REFUSEE') return 'badge-refuse';
    if (t === 'DEMANDE_COMPLEMENTS') return 'badge-complement';
    if (t === 'PRISE_EN_CHARGE') return 'badge-pec';
    if (t === 'CREATION') return 'badge-create';
    return 'badge-neutral';
  }

  asApiDate(value: unknown): Date | null {
    if (value == null || value === '') return null;
    if (typeof value === 'string') {
      const parsed = new Date(value);
      return Number.isNaN(parsed.getTime()) ? null : parsed;
    }
    return null;
  }

  private load(): void {
    this.loading = true;
    this.errorMessage = '';

    this.reportingService.getDecisions({ page: 0, size: 200 }).subscribe({
      next: (p) => {
        this.demandes = this.buildDemandesIndex(p.content);
        this.loading = false;
      },
      error: (e) => this.handleError(e),
    });
  }

  private buildDemandesIndex(
    decisions: import('../../../models/reporting.models').DecisionFinancementHistoriqueDto[]
  ): DemandeSupervisionRow[] {
    const map = new Map<number, DemandeSupervisionRow>();

    for (const d of decisions) {
      const existing = map.get(d.demandeId);
      const date = d.dateDecision || '';
      if (!existing) {
        map.set(d.demandeId, {
          demandeId: d.demandeId,
          reference: d.referenceDemande || `DEM-${d.demandeId}`,
          dernierType: d.typeDecision,
          dernierLibelle: d.libelle || '',
          derniereDate: date,
          acteurEmail: d.acteurEmail,
          nbEvenements: 1,
        });
      } else {
        existing.nbEvenements += 1;
        if (date >= existing.derniereDate) {
          existing.derniereDate = date;
          existing.dernierType = d.typeDecision;
          existing.dernierLibelle = d.libelle || '';
          existing.acteurEmail = d.acteurEmail ?? existing.acteurEmail;
        }
      }
    }

    return [...map.values()].sort((a, b) => b.derniereDate.localeCompare(a.derniereDate));
  }

  private handleError(err: unknown): void {
    this.loading = false;
    const body = (err as { error?: { message?: string } })?.error;
    this.errorMessage = body?.message ?? 'Impossible de charger les demandes.';
  }
}
