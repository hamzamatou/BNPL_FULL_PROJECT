import { CommonModule, DatePipe } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ReportingArchivageService } from '../../../services/reporting-archivage.service';
import {
  AccesPlateformeHistoriqueDto,
  libelleTypeAcces,
} from '../../../models/reporting.models';

import { AdminIconComponent } from '../../../shared/admin-icon/admin-icon.component';

@Component({
  selector: 'app-admin-utilisateurs-acces',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, RouterLink, AdminIconComponent],
  templateUrl: './admin-utilisateurs-acces.component.html',
  styleUrls: ['./admin-utilisateurs-acces.component.css'],
  host: { class: 'page-host' },
})
export class AdminUtilisateursAccesComponent implements OnInit {
  loading = false;
  errorMessage = '';
  acces: AccesPlateformeHistoriqueDto[] = [];
  totalElements = 0;
  currentPage = 0;
  pageSize = 20;

  filterUserId: number | null = null;
  filterSuspectOnly = false;

  readonly libelleAcces = libelleTypeAcces;

  constructor(private readonly reportingService: ReportingArchivageService) {}

  ngOnInit(): void {
    this.load();
  }

  search(): void {
    this.currentPage = 0;
    this.load();
  }

  prevPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.load();
    }
  }

  nextPage(): void {
    if ((this.currentPage + 1) * this.pageSize < this.totalElements) {
      this.currentPage++;
      this.load();
    }
  }

  asApiDate(value: unknown): Date | null {
    if (value == null || value === '') return null;
    if (typeof value === 'string') {
      const d = new Date(value);
      return Number.isNaN(d.getTime()) ? null : d;
    }
    return null;
  }

  private load(): void {
    this.loading = true;
    this.errorMessage = '';
    this.reportingService
      .getAcces({
        userId: this.filterUserId,
        suspectOnly: this.filterSuspectOnly,
        page: this.currentPage,
        size: this.pageSize,
      })
      .subscribe({
        next: (p) => {
          this.acces = p.content;
          this.totalElements = p.totalElements;
          this.loading = false;
        },
        error: (err) => {
          this.loading = false;
          const body = (err as { error?: { message?: string } })?.error;
          this.errorMessage = body?.message ?? 'Impossible de charger le journal des accès.';
        },
      });
  }
}
