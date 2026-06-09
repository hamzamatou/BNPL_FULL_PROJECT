import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DemandeFinancementDto, DemandeService } from '../../../services/demande.service';
import { Router } from '@angular/router';
import { badgeClassStatutDemande, libelleStatutDemande } from '../../../shared/utils/statut-demande.util';

@Component({
  selector: 'app-banque-prise-en-charge',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './banque-prise-en-charge.component.html',
  styleUrls: ['./banque-prise-en-charge.component.css'],
  host: { class: 'page-host' },
})
export class BanquePriseEnChargeComponent implements OnInit {
  demandes: DemandeFinancementDto[] = [];
  loading = false;
  errorMessage = '';

  searchTerm = '';
  sortBy: 'date_desc' | 'date_asc' | 'montant_desc' | 'montant_asc' | 'client_asc' = 'date_desc';

  constructor(
    private readonly demandeService: DemandeService,
    private readonly router: Router
  ) {}

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
        return ref.includes(q) || client.includes(q);
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
    this.demandeService.getDemandesAffecteesPourBanque().subscribe({
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

  statutLabel(statut?: string): string {
    return libelleStatutDemande(statut);
  }

  statusBadgeClass(statut?: string): 'wait' | 'analysis' | 'sent' | 'danger' | 'muted' {
    return badgeClassStatutDemande(statut);
  }

  goToDetail(id: number): void {
    this.router.navigate(['/banque', 'affectees', id]);
  }

  private dateKey(d: DemandeFinancementDto): number {
    const raw = d.dateDerniereMiseAJour || d.dateCreation;
    return raw ? new Date(raw).getTime() : 0;
  }
}
