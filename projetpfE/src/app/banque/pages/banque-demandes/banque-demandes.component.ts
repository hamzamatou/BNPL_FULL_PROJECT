import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  DemandeCompleteDto,
  DemandeFinancementDto,
  DemandeService,
  libelleSituationFamiliale,
} from '../../../services/demande.service';
import { badgeClassStatutDemande, libelleStatutDemande } from '../../../shared/utils/statut-demande.util';

@Component({
  selector: 'app-banque-demandes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './banque-demandes.component.html',
  styleUrls: ['./banque-demandes.component.css', '../../../shared/styles/uib-list-page.css'],
  host: { class: 'page-host' },
})
export class BanqueDemandesComponent implements OnInit {
  demandes: DemandeFinancementDto[] = [];
  loading = false;
  errorMessage = '';

  searchTerm = '';
  sortBy: 'date_desc' | 'date_asc' | 'montant_desc' | 'montant_asc' | 'client_asc' = 'date_desc';

  actionLoadingId: number | null = null;

  recapOpen = false;
  recapDemande: DemandeFinancementDto | null = null;
  recapDetail: DemandeCompleteDto | null = null;
  recapLoading = false;
  recapError = '';
  private closeRecapAfterSaisir = false;

  constructor(private readonly demandeService: DemandeService) {}

  ngOnInit(): void {
    this.loadDemandes();
  }

  get displayedDemandes(): DemandeFinancementDto[] {
    const q = this.searchTerm.trim().toLowerCase();
    let rows = [...this.demandes];

    if (q) {
      rows = rows.filter((d) => {
        const ref = (d.referenceDemande || `DEM-${d.id}`).toLowerCase();
        const client = this.clientLabel(d).toLowerCase();
        const cin = (d.clientCin || '').toLowerCase();
        return ref.includes(q) || client.includes(q) || cin.includes(q);
      });
    }

    rows.sort((a, b) => {
      switch (this.sortBy) {
        case 'date_asc':
          return this.dateKey(a) - this.dateKey(b);
        case 'montant_desc':
          return (b.montant || 0) - (a.montant || 0);
        case 'montant_asc':
          return (a.montant || 0) - (b.montant || 0);
        case 'client_asc':
          return this.clientLabel(a).localeCompare(this.clientLabel(b), 'fr');
        case 'date_desc':
        default:
          return this.dateKey(b) - this.dateKey(a);
      }
    });

    return rows;
  }

  loadDemandes(): void {
    this.loading = true;
    this.errorMessage = '';

    this.demandeService.getDemandesDisponiblesPourBanque().subscribe({
      next: (rows) => {
        this.demandes = rows;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage =
          err?.error?.message || err?.error?.error || err?.message || 'Erreur chargement demandes banque';
      },
    });
  }

  clientLabel(d: DemandeFinancementDto): string {
    const nom = d.clientNom || '';
    const prenom = d.clientPrenom || '';
    const full = `${nom} ${prenom}`.trim();
    return full || '-';
  }

  montantLabel(v?: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(v || 0)} TND`;
  }

  statusBadgeClass(statut?: string): 'wait' | 'analysis' | 'sent' | 'danger' | 'muted' {
    return badgeClassStatutDemande(statut);
  }

  statutLabel(statut?: string): string {
    return libelleStatutDemande(statut);
  }

  situationLabel(code?: string): string {
    return libelleSituationFamiliale(code);
  }

  tauxEndettementPercent(detail: DemandeCompleteDto | null): number {
    return (detail?.dossierClient?.tauxEndettement || 0) * 100;
  }

  prescoringZoneLabel(zone?: string): string {
    const z = (zone || '').toLowerCase();
    if (z === 'vert') return 'Zone verte';
    if (z === 'orange') return 'Zone orange';
    if (z === 'rouge') return 'Zone rouge';
    return zone || '-';
  }

  prescoringZoneClass(zone?: string): string {
    const z = (zone || '').toLowerCase();
    if (z === 'vert') return 'zone-vert';
    if (z === 'orange') return 'zone-orange';
    if (z === 'rouge') return 'zone-rouge';
    return '';
  }

  formatProbabiliteDefaut(pd?: number): string {
    if (pd == null || !Number.isFinite(pd)) return '—';
    const pct = pd <= 1 ? pd * 100 : pd;
    return (
      new Intl.NumberFormat('fr-FR', {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1,
      }).format(pct) + ' %'
    );
  }

  documentsCount(detail: DemandeCompleteDto | null): number {
    return detail?.dossierClient?.documents?.length ?? 0;
  }

  formatDate(date?: string): string {
    if (!date) return '-';
    const d = new Date(date);
    if (!Number.isFinite(d.getTime())) return '-';
    return d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' });
  }

  seSaisir(demandeId: number): void {
    if (this.actionLoadingId === demandeId) return;
    this.actionLoadingId = demandeId;
    this.errorMessage = '';

    this.demandeService.seSaisir(demandeId).subscribe({
      next: () => {
        this.actionLoadingId = null;
        this.loadDemandes();
        if (this.closeRecapAfterSaisir) {
          this.closeRecap();
          this.closeRecapAfterSaisir = false;
        }
      },
      error: (err) => {
        this.actionLoadingId = null;
        this.errorMessage =
          err?.error?.message || err?.error?.error || err?.message || 'Impossible de se saisir';
        this.closeRecapAfterSaisir = false;
      },
    });
  }

  openRecap(d: DemandeFinancementDto): void {
    this.recapDemande = d;
    this.recapDetail = null;
    this.recapError = '';
    this.recapLoading = true;
    this.recapOpen = true;
    this.errorMessage = '';

    this.demandeService.getRecapBanqueById(d.id).subscribe({
      next: (detail) => {
        this.recapDetail = detail;
        this.recapLoading = false;
      },
      error: (err) => {
        this.recapLoading = false;
        this.recapError =
          err?.error?.message || err?.error?.error || err?.message || 'Impossible de charger le récapitulatif';
      },
    });
  }

  closeRecap(): void {
    this.recapOpen = false;
    this.recapDemande = null;
    this.recapDetail = null;
    this.recapLoading = false;
    this.recapError = '';
  }

  seSaisirDepuisRecap(): void {
    if (!this.recapDemande) return;
    this.closeRecapAfterSaisir = true;
    this.seSaisir(this.recapDemande.id);
  }

  private dateKey(d: DemandeFinancementDto): number {
    const raw = d.dateDerniereMiseAJour || d.dateCreation;
    return raw ? new Date(raw).getTime() : 0;
  }
}
