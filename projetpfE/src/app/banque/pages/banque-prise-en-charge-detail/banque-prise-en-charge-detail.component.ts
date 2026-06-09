import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DomSanitizer, SafeResourceUrl, SafeUrl } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { DemandeCompleteDto, DemandeService, DocumentDossierDto } from '../../../services/demande.service';
import {
  listeIndiqueDemandeConforme,
  parseRecommandationsJson,
} from '../../../shared/utils/recommandations.util';
import { parseExplicationsJson } from '../../../shared/utils/prescoring.util';
import { libelleStatutDemande } from '../../../shared/utils/statut-demande.util';

@Component({
  selector: 'app-banque-prise-en-charge-detail',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './banque-prise-en-charge-detail.component.html',
  styleUrls: ['./banque-prise-en-charge-detail.component.css'],
  host: { class: 'page-host' },
})
export class BanquePriseEnChargeDetailComponent implements OnInit {
  loading = false;
  errorMessage = '';
  demande: DemandeCompleteDto | null = null;
  explicationsPd: string[] = [];

  decision: 'ACCEPTER' | 'REFUSER' | 'COMPLEMENTS' | null = null;
  commentaire = '';
  actionLoading = false;
  actionError = '';

  /** IDs des documents ouverts et chargés avec succès dans le visionneur */
  private readonly viewedDocumentIds = new Set<number>();

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
        this.explicationsPd = parseExplicationsJson(row.prescoringScore?.explicationsJson);
        this.viewedDocumentIds.clear();
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage =
          err?.error?.message || err?.error?.error || err?.message || 'Impossible de charger le détail';
      },
    });
  }

  get documents(): DocumentDossierDto[] {
    return this.demande?.dossierClient?.documents ?? [];
  }

  get totalDocumentsCount(): number {
    return this.documents.length;
  }

  get viewedDocumentsCount(): number {
    return this.documents.filter((d) => this.viewedDocumentIds.has(d.id)).length;
  }

  /** Aucun document → décision autorisée ; sinon tous doivent avoir été consultés */
  get allDocumentsViewed(): boolean {
    const docs = this.documents;
    if (docs.length === 0) return true;
    return docs.every((d) => this.viewedDocumentIds.has(d.id));
  }

  get recommandationsListe(): string[] {
    return parseRecommandationsJson(this.demande?.recommandation?.recommandationsJson);
  }

  get demandeConforme(): boolean {
    return listeIndiqueDemandeConforme(this.recommandationsListe);
  }

  get hasPrescoring(): boolean {
    return !!this.demande?.prescoringScore;
  }

  get hasRecommandations(): boolean {
    return this.recommandationsListe.length > 0;
  }

  get hasAiInsights(): boolean {
    return this.hasPrescoring || this.explicationsPd.length > 0 || this.hasRecommandations;
  }

  prescoringZoneLabel(zone?: string): string {
    const z = (zone || '').toLowerCase();
    if (z === 'vert') return 'Verte';
    if (z === 'orange') return 'Orange';
    if (z === 'rouge') return 'Rouge';
    return zone || '-';
  }

  prescoringZoneClass(zone?: string): string {
    const z = (zone || '').toLowerCase();
    if (z === 'vert') return 'ia-score-zone--vert';
    if (z === 'orange') return 'ia-score-zone--orange';
    if (z === 'rouge') return 'ia-score-zone--rouge';
    return '';
  }

  /** pd_pct backend : déjà en % (ex. 5.76 → 5,8 %), pas une fraction 0–1 */
  probabiliteDefautPercent(pd?: number): number {
    if (pd == null || !Number.isFinite(pd)) return 0;
    return pd <= 1 ? pd * 100 : pd;
  }

  formatProbabiliteDefaut(pd?: number): string {
    if (pd == null || !Number.isFinite(pd)) return '—';
    const pct = this.probabiliteDefautPercent(pd);
    return (
      new Intl.NumberFormat('fr-FR', {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1,
      }).format(pct) + ' %'
    );
  }

  pdLevelClass(pd?: number): string {
    const pct = this.probabiliteDefautPercent(pd);
    if (pct <= 30) return 'ia-pd--low';
    if (pct <= 60) return 'ia-pd--mid';
    return 'ia-pd--high';
  }

  pdBarWidth(pd?: number): number {
    return Math.min(100, Math.max(0, this.probabiliteDefautPercent(pd)));
  }

  formatFileSize(bytes?: number): string {
    if (!bytes || bytes <= 0) return '—';
    if (bytes < 1024) return `${bytes} o`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} Ko`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
  }

  docTypeLabel(type?: string): string {
    const t = (type || '').toUpperCase();
    const labels: Record<string, string> = {
      CIN: 'Carte d\'identité',
      REVENU: 'Justificatif de revenu',
      RIB: 'RIB',
      BULLETIN: 'Bulletin de salaire',
      ATTESTATION: 'Attestation',
      FACTURE: 'Facture',
    };
    for (const [key, label] of Object.entries(labels)) {
      if (t.includes(key)) return label;
    }
    return type || 'Document';
  }

  docIconKind(doc: DocumentDossierDto): 'pdf' | 'image' | 'file' {
    const ct = (doc.contentType || '').toLowerCase();
    const name = (doc.nomFichier || '').toLowerCase();
    if (ct.includes('pdf') || name.endsWith('.pdf')) return 'pdf';
    if (ct.startsWith('image/') || /\.(png|jpe?g|gif|webp)$/.test(name)) return 'image';
    return 'file';
  }

  get docsProgressPercent(): number {
    if (this.totalDocumentsCount === 0) return 100;
    return Math.round((this.viewedDocumentsCount / this.totalDocumentsCount) * 100);
  }

  get currentDockDocIndex(): number {
    if (!this.dockDoc) return -1;
    return this.documents.findIndex((d) => d.id === this.dockDoc!.id);
  }

  get canOpenPrevDoc(): boolean {
    return this.currentDockDocIndex > 0;
  }

  get canOpenNextDoc(): boolean {
    const idx = this.currentDockDocIndex;
    return idx >= 0 && idx < this.documents.length - 1;
  }

  openAdjacentDoc(delta: number): void {
    const idx = this.currentDockDocIndex;
    if (idx < 0) return;
    const target = this.documents[idx + delta];
    if (target) this.openDoc(target);
  }

  get docsReviewHint(): string {
    if (this.allDocumentsViewed) {
      return this.totalDocumentsCount === 0
        ? 'Aucune pièce justificative requise pour cette demande.'
        : 'Tous les documents ont été consultés. Vous pouvez enregistrer votre décision.';
    }
    return `Consultez chaque document (${this.viewedDocumentsCount}/${this.totalDocumentsCount}) pour activer la décision.`;
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

  statutLabel(statut?: string): string {
    return libelleStatutDemande(statut);
  }

  isDocumentViewed(docId: number): boolean {
    return this.viewedDocumentIds.has(docId);
  }

  setDecision(key: 'ACCEPTER' | 'REFUSER' | 'COMPLEMENTS'): void {
    if (!this.allDocumentsViewed) {
      this.actionError = 'Consultez tous les documents avant de choisir une décision.';
      return;
    }
    this.decision = key;
    this.actionError = '';
  }

  validateDecision(): void {
    if (!this.demande) return;
    if (!this.allDocumentsViewed) {
      this.actionError = `Consultez tous les documents (${this.viewedDocumentsCount}/${this.totalDocumentsCount}).`;
      return;
    }
    if (!this.decision) {
      this.actionError = 'Choisissez une action (accepter, refuser ou demander des compléments).';
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
        this.viewedDocumentIds.add(doc.id);
        this.actionError = '';
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
