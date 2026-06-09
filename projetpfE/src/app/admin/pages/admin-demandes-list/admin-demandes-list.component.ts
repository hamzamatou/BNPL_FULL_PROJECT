import { CommonModule, DatePipe, NgClass } from '@angular/common';

import { Component, OnInit } from '@angular/core';

import { FormsModule } from '@angular/forms';

import { ActivatedRoute, Router } from '@angular/router';

import { catchError, forkJoin, of } from 'rxjs';

import { DemandeFinancementDto, DemandeService } from '../../../services/demande.service';

import { ReportingArchivageService } from '../../../services/reporting-archivage.service';

import { User, UserService } from '../../../services/user.service';

import { AdminIconComponent } from '../../../shared/admin-icon/admin-icon.component';
import { DossierArchiveDto } from '../../../models/reporting.models';

export type AdminDemandesMode = 'en-cours' | 'archivees';



export interface AdminDemandeRow {

  demandeId: number;

  reference: string;

  clientLabel: string;

  commercantLabel: string;

  montant: number | null;

  statut: string;

  date: string;

}



@Component({

  selector: 'app-admin-demandes-list',

  standalone: true,

  imports: [CommonModule, NgClass, FormsModule, DatePipe, AdminIconComponent],

  templateUrl: './admin-demandes-list.component.html',

  styleUrls: ['./admin-demandes-list.component.css'],

  host: { class: 'page-host' },

})

export class AdminDemandesListComponent implements OnInit {

  mode: AdminDemandesMode = 'en-cours';

  loading = false;

  errorMessage = '';

  rows: AdminDemandeRow[] = [];

  searchTerm = '';



  constructor(

    private readonly route: ActivatedRoute,

    private readonly router: Router,

    private readonly demandeService: DemandeService,

    private readonly reportingService: ReportingArchivageService,

    private readonly userService: UserService

  ) {}



  ngOnInit(): void {

    const m = this.route.snapshot.data['mode'] as AdminDemandesMode | undefined;

    this.mode = m === 'archivees' ? 'archivees' : 'en-cours';

    this.load();

  }



  get pageTitle(): string {

    return this.mode === 'archivees' ? 'Demandes archivées' : 'Demandes en cours';

  }



  get pageSub(): string {

    return this.mode === 'archivees'

      ? 'Dossiers clôturés et archivés (reporting-archivage)'

      : 'Dossiers actifs dans gestion-demande (micro-financement)';

  }



  get displayedRows(): AdminDemandeRow[] {

    const q = this.searchTerm.trim().toLowerCase();

    if (!q) return this.rows;

    return this.rows.filter((r) => {

      return (

        r.reference.toLowerCase().includes(q) ||

        r.clientLabel.toLowerCase().includes(q) ||

        r.commercantLabel.toLowerCase().includes(q) ||

        r.statut.toLowerCase().includes(q)

      );

    });

  }



  openTraceabilite(demandeId: number): void {

    void this.router.navigate(['/admin/demandes', demandeId, 'traceabilite'], {

      queryParams: { source: this.mode },

    });

  }



  statutBadgeClass(statut: string): string {

    const s = (statut || '').toUpperCase();

    if (s.includes('ACCEPT') || s === 'ACCEPTEE') return 'badge-accept';

    if (s.includes('REFUS')) return 'badge-refuse';

    if (s.includes('ANALYSE') || s.includes('SOUMIS')) return 'badge-pec';

    if (s.includes('ARCHIV') || s.includes('CLOTUR')) return 'badge-archived';

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

    this.rows = [];



    if (this.mode === 'en-cours') {

      forkJoin({

        list: this.demandeService.getDemandesAdminEnCours(),

        users: this.userService.getUsers().pipe(catchError(() => of([] as User[]))),

      }).subscribe({

        next: ({ list, users }) => {

          const userById = new Map<number, User>();

          for (const u of users) {

            if (u.id != null) userById.set(Number(u.id), u);

          }

          this.rows = list.map((d) => this.fromDemande(d, userById));

          this.loading = false;

        },

        error: (e) => this.handleError(e, 'gestion-demande (port 8081)'),

      });

    } else {

      this.reportingService.getArchives({ page: 0, size: 500 }).subscribe({

        next: (p) => {

          this.rows = p.content.map((a) => this.fromArchive(a));

          this.loading = false;

        },

        error: (e) => this.handleError(e, 'reporting-archivage (port 8083)'),

      });

    }

  }



  private fromDemande(d: DemandeFinancementDto, userById: Map<number, User>): AdminDemandeRow {

    const client = [d.clientPrenom, d.clientNom].filter(Boolean).join(' ').trim();

    return {

      demandeId: d.id,

      reference: d.referenceDemande || `DEM-${d.id}`,

      clientLabel: client || d.clientCin || '—',

      commercantLabel: this.commercantLabel(d.commercantUserId, userById),

      montant: typeof d.montant === 'number' ? d.montant : null,

      statut: d.statut || '—',

      date: d.dateDerniereMiseAJour || d.dateCreation,

    };

  }



  private fromArchive(a: DossierArchiveDto): AdminDemandeRow {

    return {

      demandeId: a.demandeId,

      reference: a.referenceDemande || `DEM-${a.demandeId}`,

      clientLabel: a.cinClient || '—',

      commercantLabel: '—',

      montant: a.montant ?? null,

      statut: a.statutFinal || 'ARCHIVÉE',

      date: a.dateArchivage,

    };

  }



  private commercantLabel(commercantUserId: number | undefined, userById: Map<number, User>): string {

    if (commercantUserId == null) return '—';

    const u = userById.get(Number(commercantUserId));

    if (!u) return '—';

    if (u.nomMagasin?.trim()) return u.nomMagasin.trim();

    const name = [u.prenom, u.nom].filter(Boolean).join(' ').trim();

    return name || u.email;

  }



  private handleError(err: unknown, serviceHint: string): void {

    this.loading = false;

    const body = (err as { error?: { message?: string } })?.error;

    this.errorMessage =

      body?.message ?? `Impossible de charger les demandes (vérifiez ${serviceHint}).`;

  }

}


