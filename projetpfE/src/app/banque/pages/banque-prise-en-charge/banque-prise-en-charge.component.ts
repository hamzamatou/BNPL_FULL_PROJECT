import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { DemandeFinancementDto, DemandeService } from '../../../services/demande.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-banque-prise-en-charge',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './banque-prise-en-charge.component.html',
  styleUrls: ['./banque-prise-en-charge.component.css'],
})
export class BanquePriseEnChargeComponent implements OnInit {
  demandes: DemandeFinancementDto[] = [];
  loading = false;
  errorMessage = '';

  constructor(
    private readonly demandeService: DemandeService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.loadDemandes();
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

  goToDetail(id: number): void {
    this.router.navigate(['/banque', 'affectees', id]);
  }
}

