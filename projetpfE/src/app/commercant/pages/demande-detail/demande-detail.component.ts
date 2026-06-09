import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import {
  DemandeCompleteDto,
  DemandeService,
  DocumentDossierDto,
  libelleSituationFamiliale,
} from '../../../services/demande.service';
import { DomSanitizer, SafeResourceUrl, SafeUrl } from '@angular/platform-browser';

@Component({
  selector: 'app-demande-detail',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './demande-detail.component.html',
  styleUrl: './demande-detail.component.css',
})
export class DemandeDetailComponent implements OnInit {
  loading = false;
  errorMessage = '';
  demande: DemandeCompleteDto | null = null;
  demandeId: number | null = null;
  actionLoading = false;
  showCancelConfirm = false;

  docDockOpen = false;
  dockLoading = false;
  dockDoc: DocumentDossierDto | null = null;
  dockUrl = '';
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
    this.demandeId = id;
    this.loadDemande(id);
  }

  private loadDemande(id: number): void {
    this.loading = true;
    this.errorMessage = '';
    this.demandeService.getDemandeDetailById(id).subscribe({
      next: (row) => {
        this.demande = row;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage =
          err?.error?.message || err?.error?.error || err?.message || 'Impossible de charger le detail';
      },
    });
  }

  get canCancel(): boolean {
    const s = (this.demande?.statut || '').toUpperCase();
    return s === 'CREE' || s === 'EN_ATTENTE_CONSENTEMENT' || s === 'EN_COURS_PRESCORING';
  }

  get canResendConsent(): boolean {
    const s = (this.demande?.statut || '').toUpperCase();
    return s === 'EN_ATTENTE_CONSENTEMENT';
  }

  cancelDemande(): void {
    if (!this.demandeId || this.actionLoading || !this.canCancel) return;
    this.showCancelConfirm = true;
  }

  closeCancelConfirm(): void {
    if (this.actionLoading) return;
    this.showCancelConfirm = false;
  }

  confirmCancelDemande(): void {
    if (!this.demandeId || this.actionLoading || !this.canCancel) return;
    this.actionLoading = true;
    this.demandeService.annulerDemande(this.demandeId).subscribe({
      next: () => {
        this.actionLoading = false;
        this.showCancelConfirm = false;
        this.loadDemande(this.demandeId!);
      },
      error: () => {
        this.actionLoading = false;
        this.showCancelConfirm = false;
      },
    });
  }

  resendConsent(): void {
    if (!this.demandeId || this.actionLoading || !this.canResendConsent) return;
    this.actionLoading = true;
    this.demandeService.renvoyerConsentement(this.demandeId).subscribe({
      next: () => {
        this.actionLoading = false;
        this.loadDemande(this.demandeId!);
      },
      error: () => {
        this.actionLoading = false;
      },
    });
  }

  get statusLabel(): string {
    const s = (this.demande?.statut || '').toUpperCase();
    if (!s) return '-';
    // Un seul statut backend avant validation client
    if (s === 'CREE') return 'Créée';
    if (s.includes('EN_ATTENTE_CONSENTEMENT')) return 'Consentement client';
    if (s.includes('EN_ATTENTE') || s.includes('BROUILLON')) return 'En attente';
    if (s.includes('SOUMISE')) return 'Soumise';
    if (s.includes('EN_ANALYSE') || s.includes('ANALYSE') || s.includes('EN_COURS')) return 'En analyse';
    if (s.includes('REFUSEE') || s.includes('REFUSE')) return 'Décision';
    if (s.includes('ACCEPTEE')) return 'Financement';
    if (s === 'ANNULEE' || s.includes('ANNULE')) return 'Annulée';
    return this.demande?.statut || '-';
  }

  // index 0..4 : (En attente consentement, Soumise, En analyse, Décision, Financement)
  get activeStepIndex(): number {
    const s = (this.demande?.statut || '').toUpperCase();
    if (!s) return 0;
    if (s.includes('ACCEPTEE')) return 4;
    if (s.includes('REFUSEE') || s.includes('REFUSE')) return 3;
    if (s.includes('EN_ANALYSE') || s.includes('ANALYSE') || s.includes('EN_COURS')) return 2;
    if (s.includes('SOUMISE')) return 1;
    if (s === 'CREE' || s.includes('EN_ATTENTE_CONSENTEMENT')) return 0;
    if (s.includes('EN_ATTENTE') || s.includes('BROUILLON')) return 0;
    return 0;
  }

  get stepDates(): string[] {
    const created = this.formatDateShort(this.demande?.dateCreation);
    const maj = this.formatDateShort(this.demande?.dateDerniereMiseAJour);
    return [created, maj, maj, maj, maj];
  }

  isStepDone(idx: number): boolean {
    const s = (this.demande?.statut || '').toUpperCase();
    if (!s) return false;

    if (idx < this.activeStepIndex) return true;

    return false;
  }

  openDoc(doc: DocumentDossierDto): void {
    this.dockDoc = doc;
    this.dockUrl = '';
    this.dockErrorMessage = '';
    this.docDockOpen = true;
    this.dockLoading = true;

    this.demandeService.getDocumentPresignedUrl(doc.objectKey).subscribe({
      next: (res) => {
        this.dockUrl = res.url;
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
    this.docDockOpen = false;
    this.dockLoading = false;
    this.dockDoc = null;
    this.dockUrl = '';
    this.dockSafeResourceUrl = null;
    this.dockSafeUrl = null;
    this.dockErrorMessage = '';
  }

  get dockContentTypeLc(): string {
    return (this.dockDoc?.contentType || '').toLowerCase();
  }

  get clientDisplay(): string {
    if (!this.demande) return '-';
    const nom = this.demande.client?.nom || '';
    const prenom = this.demande.client?.prenom || '';
    const full = `${nom} ${prenom}`.trim();
    if (full) return full;
    return this.demande.dossierClient?.clientId ? `Client #${this.demande.dossierClient.clientId}` : '-';
  }

  get situationFamilialeDisplay(): string {
    return libelleSituationFamiliale(this.demande?.dossierClient?.situationFamiliale);
  }

  get nombreEnfantsDisplay(): string {
    const n = this.demande?.dossierClient?.nombreEnfants;
    if (n === undefined || n === null) return '-';
    return String(n);
  }

  get montantDisplay(): string {
    if (!this.demande) return '-';
    return `${new Intl.NumberFormat('fr-FR').format(this.demande.montant || 0)} TND`;
  }

  get mensualiteEstimee(): number {
    if (!this.demande?.montant || !this.demande?.dureeMois) return 0;
    const d = this.demande.dureeMois || 1;
    if (!Number.isFinite(d) || d <= 0) return 0;
    return (this.demande.montant || 0) / d;
  }

  get tauxEndettementPercent(): number {
    const t = this.demande?.dossierClient?.tauxEndettement;
    if (!Number.isFinite(Number(t))) return 0;
    return (Number(t) || 0) * 100;
  }

  get dateCreationDisplay(): string {
    if (!this.demande?.dateCreation) return '-';
    return new Date(this.demande.dateCreation).toLocaleString('fr-FR');
  }

  get dateMajDisplay(): string {
    if (!this.demande?.dateDerniereMiseAJour) return '-';
    return new Date(this.demande.dateDerniereMiseAJour).toLocaleString('fr-FR');
  }

  formatDateShort(date?: string): string {
    if (!date) return '-';
    const d = new Date(date);
    if (!Number.isFinite(d.getTime())) return '-';
    return d.toLocaleDateString('fr-FR');
  }

  get historyItems(): { title: string; detail: string; date: string }[] {
    const rows = this.demande?.historique ?? [];
    return [...rows]
      .sort(
        (a, b) =>
          new Date(b.dateEvenement).getTime() - new Date(a.dateEvenement).getTime()
      )
      .map((e) => ({
        title: e.libelle,
        detail: e.detail || this.formatStatutTransition(e.statutAvant, e.statutApres),
        date: this.formatDateTime(e.dateEvenement),
      }));
  }

  private formatStatutTransition(avant?: string, apres?: string): string {
    if (avant && apres && avant !== apres) {
      return `${this.libelleStatut(avant)} → ${this.libelleStatut(apres)}`;
    }
    if (apres) return this.libelleStatut(apres);
    return '';
  }

  private libelleStatut(statut: string): string {
    const s = (statut || '').toUpperCase();
    if (s === 'CREE') return 'Créée';
    if (s.includes('EN_ATTENTE_CONSENTEMENT')) return 'Consentement client';
    if (s.includes('SOUMISE')) return 'Soumise';
    if (s.includes('EN_COURS_ANALYSE')) return 'En analyse';
    if (s.includes('ACCEPTEE')) return 'Acceptée';
    if (s.includes('REFUSEE')) return 'Refusée';
    if (s.includes('ANNULEE')) return 'Annulée';
    return statut;
  }

  formatDateTime(date?: string): string {
    if (!date) return '-';
    const d = new Date(date);
    if (!Number.isFinite(d.getTime())) return '-';
    return d.toLocaleString('fr-FR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  formatMoney(value?: number): string {
    return `${new Intl.NumberFormat('fr-FR').format(value || 0)} TND`;
  }

  formatFileSize(bytes?: number): string {
    if (!bytes || bytes <= 0) return '-';
    if (bytes < 1024) return `${bytes} o`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} Ko`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
  }

  back(): void {
    this.router.navigate(['/mes-demandes']);
  }
}
