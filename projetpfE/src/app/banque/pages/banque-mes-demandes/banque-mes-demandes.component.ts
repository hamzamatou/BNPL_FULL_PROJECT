import { CommonModule, DatePipe } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ReportingArchivageService } from '../../../services/reporting-archivage.service';
import {
  DecisionFinancementHistoriqueDto,
  libelleTypeDecision,
} from '../../../models/reporting.models';

@Component({
  selector: 'app-banque-mes-demandes',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, RouterLink],
  templateUrl: './banque-mes-demandes.component.html',
  styleUrls: ['./banque-mes-demandes.component.css'],
  host: { class: 'page-host' },
})
export class BanqueMesDemandesComponent implements OnInit {
  loading = false;
  errorMessage = '';

  historique: DecisionFinancementHistoriqueDto[] = [];
  totalHistorique = 0;
  currentPage = 0;
  pageSize = 15;

  filterType = '';
  filterDebut = '';
  filterFin = '';

  readonly libelleDecision = libelleTypeDecision;

  constructor(private readonly reportingService: ReportingArchivageService) {}

  ngOnInit(): void {
    this.loadHistorique();
  }

  searchHistorique(): void {
    this.currentPage = 0;
    this.loadHistorique();
  }

  prevPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.loadHistorique();
    }
  }

  nextPage(): void {
    if ((this.currentPage + 1) * this.pageSize < this.totalHistorique) {
      this.currentPage++;
      this.loadHistorique();
    }
  }

  decisionBadgeClass(type: string): string {
    const t = (type || '').toUpperCase();
    if (t === 'ACCEPTEE') return 'badge-accept';
    if (t === 'REFUSEE') return 'badge-refuse';
    if (t === 'DEMANDE_COMPLEMENTS') return 'badge-complement';
    if (t === 'PRISE_EN_CHARGE') return 'badge-pec';
    return 'badge-neutral';
  }

  private loadHistorique(): void {
    this.loading = true;
    this.errorMessage = '';

    this.reportingService
      .getDecisions({
        type: this.filterType || null,
        debut: this.toIso(this.filterDebut),
        fin: this.toIso(this.filterFin),
        page: this.currentPage,
        size: this.pageSize,
      })
      .subscribe({
        next: (page) => {
          this.historique = page.content;
          this.totalHistorique = page.totalElements;
          this.loading = false;
        },
        error: (err) => this.handleError(err),
      });
  }

  private toIso(local: string): string | null {
    if (!local) return null;
    const d = new Date(local);
    return Number.isNaN(d.getTime()) ? null : d.toISOString().slice(0, 19);
  }

  private handleError(err: unknown): void {
    this.loading = false;
    const body = (err as { error?: { message?: string } })?.error;
    this.errorMessage =
      body?.message ??
      'Impossible de charger l\'historique (vérifiez que reporting-archivage tourne sur le port 8083).';
  }
}
