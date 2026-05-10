import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { DemandeFinancementDto, DemandeService } from '../../../services/demande.service';

type Demande = {
  id: number;
  reference: string;
  client: string;
  clientNom: string;
  clientPrenom: string;
  montant: string;
  montantValue: number;
  statut: string;
  date: string;
  dateValue: number;
  typeProduit: string;
};

@Component({
  selector: 'app-mes-demandes',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './mes-demandes.component.html',
  styleUrl: './mes-demandes.component.css'
})
export class MesDemandesComponent implements OnInit {
  demandes: Demande[] = [];
  searchTerm = '';
  sortBy: 'date_desc' | 'date_asc' | 'montant_desc' | 'montant_asc' | 'client_asc' = 'date_desc';
  loading = false;
  errorMessage = '';

  constructor(
    private readonly demandeService: DemandeService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.loadDemandes();
  }

  get totalCount(): number {
    return this.demandes.length;
  }

  get waitingCount(): number {
    return this.demandes.filter((d) => d.statut === 'EN_ATTENTE_CONSENTEMENT').length;
  }

  get analysisCount(): number {
    return this.demandes.filter((d) => d.statut === 'EN_ANALYSE').length;
  }

  get acceptedCount(): number {
    return this.demandes.filter((d) => d.statut === 'SOUMISE').length;
  }

  private loadDemandes(): void {
    this.loading = true;
    this.errorMessage = '';

    this.demandeService.getDemandesByCommercantFromToken().subscribe({
      next: (rows) => {
        this.demandes = rows.map((row) => this.mapDemande(row));
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage =
          err?.error?.message || err?.error?.error || err?.message || 'Erreur chargement des demandes';
      },
    });
  }

  private mapDemande(row: DemandeFinancementDto): Demande {
    const montantValue = typeof row.montant === 'number' ? row.montant : 0;
    const amount = `${new Intl.NumberFormat('fr-FR').format(montantValue)} TND`;

    const rawDate = row.dateCreation || row.dateDerniereMiseAJour;
    const dateValue = rawDate ? new Date(rawDate).getTime() : 0;
    const date = rawDate ? new Date(rawDate).toLocaleDateString('fr-FR') : '-';
    const clientNom = row.clientNom || '';
    const clientPrenom = row.clientPrenom || '';
    const client = clientNom || clientPrenom ? `${clientNom} ${clientPrenom}`.trim() : row.clientId ? `Client #${row.clientId}` : '-';

    return {
      id: row.id,
      reference: row.referenceDemande || `DEM-${row.id}`,
      client,
      clientNom,
      clientPrenom,
      montant: amount,
      montantValue,
      statut: this.normalizeStatut(row.statut),
      date,
      dateValue,
      typeProduit: row.typeProduit || '-',
    };
  }

  private normalizeStatut(statut?: string): string {
    const s = (statut || '').toUpperCase();
    if (s.includes('ANALYSE') || s.includes('EN_COURS')) return 'EN_ANALYSE';
    if (s.includes('SOUMISE') || s.includes('ACCEPTEE')) return 'SOUMISE';
    // Backend: "EN_ATTENTE_CONSENTEMENT" est le statut unique avant consentement.
    return 'EN_ATTENTE_CONSENTEMENT';
  }

  get displayedDemandes(): Demande[] {
    const q = this.searchTerm.trim().toLowerCase();
    const filtered = this.demandes.filter((d) => {
      if (!q) return true;
      return (
        d.reference.toLowerCase().includes(q) ||
        d.client.toLowerCase().includes(q) ||
        d.statut.toLowerCase().includes(q) ||
        d.typeProduit.toLowerCase().includes(q)
      );
    });

    return filtered.sort((a, b) => {
      switch (this.sortBy) {
        case 'date_asc':
          return a.dateValue - b.dateValue;
        case 'date_desc':
          return b.dateValue - a.dateValue;
        case 'montant_asc':
          return a.montantValue - b.montantValue;
        case 'montant_desc':
          return b.montantValue - a.montantValue;
        case 'client_asc':
          return a.client.localeCompare(b.client, 'fr');
        default:
          return 0;
      }
    });
  }

  openDetails(demande: Demande): void {
    this.router.navigate(['/mes-demandes', demande.id]);
  }
}
