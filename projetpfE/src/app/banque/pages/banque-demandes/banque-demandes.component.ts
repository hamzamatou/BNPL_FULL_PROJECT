import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { DemandeFinancementDto, DemandeService } from '../../../services/demande.service';

@Component({
  selector: 'app-banque-demandes',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './banque-demandes.component.html',
  styleUrls: ['./banque-demandes.component.css'],
})
export class BanqueDemandesComponent implements OnInit {
  demandes: DemandeFinancementDto[] = [];
  loading = false;
  errorMessage = '';

  actionLoadingId: number | null = null;

  recapOpen = false;
  recapDemande: DemandeFinancementDto | null = null;
  private closeRecapAfterSaisir = false;

  constructor(private readonly demandeService: DemandeService) {}

  ngOnInit(): void {
    this.loadDemandes();
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

  montantLabel(v: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(v || 0)} TND`;
  }

  statusBadgeClass(statut?: string): 'wait' | 'analysis' | 'sent' {
    const s = (statut || '').toUpperCase();
    if (s.includes('EN_ANALYSE') || s.includes('EN_COURS') || s.includes('ANALYSE')) return 'analysis';
    if (s.includes('SOUMISE') || s.includes('ACCEPTEE')) return 'sent';
    return 'wait';
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
    this.recapOpen = true;
    this.errorMessage = '';
  }

  closeRecap(): void {
    this.recapOpen = false;
    this.recapDemande = null;
  }

  seSaisirDepuisRecap(): void {
    if (!this.recapDemande) return;
    this.closeRecapAfterSaisir = true;
    this.seSaisir(this.recapDemande.id);
  }
}

