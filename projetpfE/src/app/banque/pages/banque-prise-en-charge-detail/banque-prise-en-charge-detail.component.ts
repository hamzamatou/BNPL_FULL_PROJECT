import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DomSanitizer, SafeResourceUrl, SafeUrl } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { DemandeCompleteDto, DemandeService, DocumentDossierDto } from '../../../services/demande.service';

@Component({
  selector: 'app-banque-prise-en-charge-detail',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './banque-prise-en-charge-detail.component.html',
  styleUrls: ['./banque-prise-en-charge-detail.component.css'],
})
export class BanquePriseEnChargeDetailComponent implements OnInit {
  loading = false;
  errorMessage = '';
  demande: DemandeCompleteDto | null = null;

  decision: 'ACCEPTER' | 'REFUSER' | 'COMPLEMENTS' | null = null;
  commentaire = '';
  actionLoading = false;
  actionError = '';

  // Dock viewer (documents)
  dockOpen = false;
  dockLoading = false;
  dockDoc: DocumentDossierDto | null = null;
  dockSafeResourceUrl: SafeResourceUrl | null = null;
  dockSafeUrl: SafeUrl | null = null;
  dockErrorMessage = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly demandeService: DemandeService,
    private readonly sanitizer: DomSanitizer
  ) {}

  ngOnInit(): void {
    const rawId = this.route.snapshot.paramMap.get('id');
    const id = rawId ? Number(rawId) : NaN;
    if (!Number.isFinite(id)) {
      this.errorMessage = 'Identifiant de demande invalide.';
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.demandeService.getDemandeDetailBanqueById(id).subscribe({
      next: (row) => {
        this.demande = row;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage =
          err?.error?.message || err?.error?.error || err?.message || 'Impossible de charger le détail';
      },
    });
  }

  back(): void {
    this.router.navigate(['/banque/affectees']);
  }

  get clientFullName(): string {
    const nom = this.demande?.client?.nom || '';
    const prenom = this.demande?.client?.prenom || '';
    const full = `${nom} ${prenom}`.trim();
    return full || '-';
  }

  montantLabel(v?: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(v || 0)} TND`;
  }

  tauxEndettementPercent(): number {
    return (this.demande?.dossierClient?.tauxEndettement || 0) * 100;
  }

  formatDateShort(date?: string): string {
    if (!date) return '-';
    const d = new Date(date);
    if (!Number.isFinite(d.getTime())) return '-';
    return d.toLocaleDateString('fr-FR');
  }

  setDecision(key: 'ACCEPTER' | 'REFUSER' | 'COMPLEMENTS'): void {
    this.decision = key;
    this.actionError = '';
  }

  validateDecision(): void {
    if (!this.demande) return;
    if (!this.decision) {
      this.actionError = 'Choisis une action (accepter/refuser/demander des compléments).';
      return;
    }

    this.actionLoading = true;
    this.actionError = '';

    const id = this.demande.id;
    const payload = { commentaire: this.commentaire };

    let req$;
    if (this.decision === 'ACCEPTER') {
      req$ = this.demandeService.accepterDemande(id, payload);
    } else if (this.decision === 'REFUSER') {
      req$ = this.demandeService.refuserDemande(id, { motifRefus: this.commentaire, commentaire: this.commentaire });
    } else {
      req$ = this.demandeService.demanderComplements(id, payload);
    }

    req$.subscribe({
      next: () => {
        this.actionLoading = false;
        this.router.navigate(['/banque/affectees']);
      },
      error: (err) => {
        this.actionLoading = false;
        this.actionError =
          err?.error?.message || err?.error?.error || err?.message || 'Impossible de valider la décision';
      },
    });
  }

  openDoc(doc: DocumentDossierDto): void {
    this.dockDoc = doc;
    this.dockSafeResourceUrl = null;
    this.dockSafeUrl = null;
    this.dockErrorMessage = '';
    this.dockOpen = true;
    this.dockLoading = true;

    this.demandeService.getDocumentPresignedUrl(doc.objectKey).subscribe({
      next: (res) => {
        this.dockSafeResourceUrl = this.sanitizer.bypassSecurityTrustResourceUrl(res.url);
        this.dockSafeUrl = this.sanitizer.bypassSecurityTrustUrl(res.url);
        this.dockLoading = false;
      },
      error: () => {
        this.dockLoading = false;
        this.dockErrorMessage = 'Impossible de charger le document (MinIO).';
      },
    });
  }

  closeDocDock(): void {
    this.dockOpen = false;
    this.dockLoading = false;
    this.dockDoc = null;
    this.dockSafeResourceUrl = null;
    this.dockSafeUrl = null;
    this.dockErrorMessage = '';
  }

  get dockContentTypeLc(): string {
    return (this.dockDoc?.contentType || '').toLowerCase();
  }
}

