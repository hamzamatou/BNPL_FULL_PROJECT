import { CommonModule, DatePipe, NgClass } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { DemandeFinancementDto, DemandeService } from '../../../services/demande.service';
import { ReportingArchivageService } from '../../../services/reporting-archivage.service';
import { User, UserService } from '../../../services/user.service';
import {
  ActionDemandeHistoriqueDto,
  ActionDocumentHistoriqueDto,
  DecisionFinancementHistoriqueDto,
  DossierArchiveDto,
  libelleTypeActionDemande,
  libelleTypeActionDocument,
  libelleTypeDecision,
} from '../../../models/reporting.models';
import { resolveUibLogo, UIB_LOGO_CANDIDATES } from '../../../shared/uib-brand';
import { AdminIconComponent } from '../../../shared/admin-icon/admin-icon.component';
import {
  IaHistoriqueDetails,
  parseIaHistoriqueDetails,
} from '../../../shared/utils/ia-historique-details';

export interface TraceabiliteLigne {
  id: string;
  date: string;
  categorie: 'Action demande' | 'Document' | 'Décision';
  typeLabel: string;
  libelle: string;
  acteur: string;
  acteurRole: string;
  detail: string;
  iaDetails: IaHistoriqueDetails | null;
  statutTransition: string;
}

const ROLE_LABELS: Record<string, string> = {
  COMMERCANT: 'Commerçant',
  ANALYSTE_BANCAIRE: 'Analyste bancaire',
  BANQUE: 'Banque',
  ADMIN: 'Administrateur',
};

@Component({
  selector: 'app-admin-traceabilite-demande',
  standalone: true,
  imports: [CommonModule, NgClass, DatePipe, RouterLink, AdminIconComponent],
  templateUrl: './admin-traceabilite-demande.component.html',
  styleUrls: ['./admin-traceabilite-demande.component.css'],
  host: { class: 'page-host' },
})
export class AdminTraceabiliteDemandeComponent implements OnInit {
  logoSrc = UIB_LOGO_CANDIDATES[0];
  logoFailed = false;
  private logoCandidateIdx = 0;

  demandeId = 0;
  reference = '';
  clientLabel = '—';
  commercantLabel = '—';
  statutDemande = '—';
  source: 'en-cours' | 'archivees' = 'en-cours';
  loading = false;
  errorMessage = '';
  lignes: TraceabiliteLigne[] = [];

  private userById = new Map<number, User>();
  private userByEmail = new Map<string, User>();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly reportingService: ReportingArchivageService,
    private readonly demandeService: DemandeService,
    private readonly userService: UserService
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('demandeId'));
    if (Number.isNaN(id) || id <= 0) {
      this.errorMessage = 'Identifiant de demande invalide.';
      return;
    }
    this.demandeId = id;
    const src = this.route.snapshot.queryParamMap.get('source');
    if (src === 'archivees') this.source = 'archivees';
    void resolveUibLogo().then((src) => (this.logoSrc = src));
    this.load();
  }

  onLogoError(): void {
    this.logoCandidateIdx += 1;
    if (this.logoCandidateIdx < UIB_LOGO_CANDIDATES.length) {
      this.logoSrc = UIB_LOGO_CANDIDATES[this.logoCandidateIdx];
    } else {
      this.logoFailed = true;
    }
  }

  get backLink(): string {
    return this.source === 'archivees'
      ? '/admin/demandes/archivees'
      : '/admin/demandes/en-cours';
  }

  get backLabel(): string {
    return this.source === 'archivees' ? 'Demandes archivées' : 'Demandes en cours';
  }

  get generatedAt(): string {
    return new Date().toLocaleString('fr-FR');
  }

  asApiDate(value: unknown): Date | null {
    if (value == null || value === '') return null;
    if (typeof value === 'string') {
      const parsed = new Date(value);
      return Number.isNaN(parsed.getTime()) ? null : parsed;
    }
    return null;
  }

  categorieClass(cat: TraceabiliteLigne['categorie']): string {
    if (cat === 'Décision') return 'cat-decision';
    if (cat === 'Document') return 'cat-document';
    return 'cat-action';
  }

  iconClass(cat: TraceabiliteLigne['categorie']): string {
    if (cat === 'Décision') return 'icon-decision';
    if (cat === 'Document') return 'icon-document';
    return 'icon-action';
  }

  imprimerAudit(): void {
    const printWindow = window.open('', '_blank', 'width=900,height=700');
    if (!printWindow) return;
    printWindow.document.write(this.buildPrintDocument());
    printWindow.document.close();
    printWindow.focus();
    setTimeout(() => {
      printWindow.print();
      printWindow.close();
    }, 400);
  }

  private load(): void {
    this.loading = true;
    this.errorMessage = '';
    const filters = { demandeId: this.demandeId, page: 0, size: 500 };

    forkJoin({
      actions: this.reportingService.getActionsDemandes(filters),
      documents: this.reportingService.getActionsDocuments(filters),
      decisions: this.reportingService.getDecisions(filters),
      users: this.userService.getUsers(),
      demandes: this.demandeService.getDemandesAdminEnCours().pipe(catchError(() => of([]))),
      archives: this.reportingService
        .getArchives({ page: 0, size: 500 })
        .pipe(catchError(() => of({ content: [] as DossierArchiveDto[], totalElements: 0 }))),
    }).subscribe({
      next: ({ actions, documents, decisions, users, demandes, archives }) => {
        this.indexUsers(users);
        this.applyDemandeMeta(demandes, archives.content);
        const firstRef =
          actions.content[0]?.referenceDemande ||
          documents.content[0]?.referenceDemande ||
          decisions.content[0]?.referenceDemande;
        this.reference = firstRef || `DEM-${this.demandeId}`;
        this.lignes = this.mergeTimeline(actions.content, documents.content, decisions.content);
        this.inferCommercantFromActions(actions.content);
        this.inferCommercantFromLignes();
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        const body = (err as { error?: { message?: string } })?.error;
        this.errorMessage =
          body?.message ??
          'Impossible de charger la traçabilité (vérifiez reporting-archivage sur le port 8083).';
      },
    });
  }

  private indexUsers(users: User[]): void {
    this.userById.clear();
    this.userByEmail.clear();
    for (const u of users) {
      if (u.id != null) this.userById.set(Number(u.id), u);
      if (u.email) this.userByEmail.set(u.email.toLowerCase(), u);
    }
  }

  private inferCommercantFromActions(actions: ActionDemandeHistoriqueDto[]): void {
    if (this.commercantLabel && this.commercantLabel !== '—') return;

    const commercantAction =
      actions.find((a) => a.typeAction === 'CREATION') ||
      actions.find((a) => (a.acteurRole || '').toUpperCase() === 'COMMERCANT');

    if (!commercantAction) return;

    const email = commercantAction.acteurEmail?.trim();
    if (email) {
      const u = this.userByEmail.get(email.toLowerCase());
      this.commercantLabel = u ? this.formatUserNameOnly(u) : this.formatEmailAsName(email);
      return;
    }

    if (commercantAction.acteurUserId != null) {
      const label = this.resolveCommercantLabel(commercantAction.acteurUserId);
      if (label !== '—') this.commercantLabel = label;
    }
  }

  private formatUserNameOnly(u: User): string {
    if (u.nomMagasin?.trim()) return u.nomMagasin.trim();
    const name = [u.prenom, u.nom].filter(Boolean).join(' ').trim();
    return name || u.email;
  }

  private formatEmailAsName(email: string): string {
    const local = email.split('@')[0] || email;
    return local.charAt(0).toUpperCase() + local.slice(1);
  }

  private applyDemandeMeta(
    demandes: DemandeFinancementDto[],
    archives: DossierArchiveDto[]
  ): void {
    const d = demandes.find((x) => x.id === this.demandeId);
    if (d) {
      this.reference = d.referenceDemande || this.reference;
      this.statutDemande = d.statut || '—';
      this.clientLabel =
        [d.clientPrenom, d.clientNom].filter(Boolean).join(' ').trim() || d.clientCin || '—';
      this.commercantLabel = this.resolveCommercantLabel(d.commercantUserId);
      return;
    }
    const a = archives.find((x) => x.demandeId === this.demandeId);
    if (a) {
      this.reference = a.referenceDemande || this.reference;
      this.statutDemande = a.statutFinal || 'ARCHIVÉE';
      this.clientLabel = a.cinClient || '—';
    }
  }

  private resolveCommercantLabel(commercantUserId?: number): string {
    if (commercantUserId == null) return '—';
    const u = this.userById.get(Number(commercantUserId));
    if (!u) return '—';
    return this.formatUserNameOnly(u);
  }

  private inferCommercantFromLignes(): void {
    if (this.commercantLabel && this.commercantLabel !== '—') return;
    const ligne = this.lignes.find(
      (l) => l.categorie === 'Action demande' && l.acteurRole === 'Commerçant'
    );
    if (ligne?.acteur) {
      const raw = ligne.acteur.split(' (')[0].trim() || ligne.acteur;
      this.commercantLabel = raw.includes('@') ? this.formatEmailAsName(raw) : raw;
    }
  }

  private formatUser(u: User): string {
    const name = [u.prenom, u.nom].filter(Boolean).join(' ').trim();
    return name ? `${name} (${u.email})` : u.email;
  }

  private resolveActeur(
    email: string | null | undefined,
    userId: number | null | undefined,
    role: string | null | undefined
  ): { acteur: string; acteurRole: string } {
    const roleLabel = role ? ROLE_LABELS[role] || role : 'Système';
    if (email) {
      const u = this.userByEmail.get(email.toLowerCase());
      return { acteur: u ? this.formatUser(u) : email, acteurRole: u?.role ? ROLE_LABELS[u.role] || u.role : roleLabel };
    }
    if (userId != null && this.userById.has(userId)) {
      const u = this.userById.get(userId)!;
      return { acteur: this.formatUser(u), acteurRole: ROLE_LABELS[u.role] || u.role };
    }
    if (role) {
      return { acteur: roleLabel, acteurRole: roleLabel };
    }
    return { acteur: 'Plateforme BNPL', acteurRole: 'Automatique' };
  }

  private mergeTimeline(
    actions: ActionDemandeHistoriqueDto[],
    documents: ActionDocumentHistoriqueDto[],
    decisions: DecisionFinancementHistoriqueDto[]
  ): TraceabiliteLigne[] {
    const rows: TraceabiliteLigne[] = [];

    for (const a of actions) {
      const { acteur, acteurRole } = this.resolveActeur(a.acteurEmail, a.acteurUserId, a.acteurRole);
      const iaDetails = parseIaHistoriqueDetails(a.detailsJson);
      rows.push({
        id: `a-${a.id}`,
        date: a.dateAction,
        categorie: 'Action demande',
        typeLabel: libelleTypeActionDemande(a.typeAction),
        libelle: a.libelle || '—',
        acteur,
        acteurRole,
        detail: iaDetails?.detail || a.detailsJson || '—',
        iaDetails,
        statutTransition: [a.statutAvant, a.statutApres].filter(Boolean).join(' → ') || '—',
      });
    }

    for (const d of documents) {
      const { acteur, acteurRole } = this.resolveActeur(d.acteurEmail, d.acteurUserId, d.acteurRole);
      rows.push({
        id: `d-${d.id}`,
        date: d.dateAction,
        categorie: 'Document',
        typeLabel: libelleTypeActionDocument(d.typeAction),
        libelle: d.libelle || d.objectKey || '—',
        acteur,
        acteurRole,
        detail: d.objectKey || d.typeDocument || '—',
        iaDetails: null,
        statutTransition: '—',
      });
    }

    for (const dec of decisions) {
      const { acteur, acteurRole } = this.resolveActeur(dec.acteurEmail, dec.acteurUserId, dec.acteurRole);
      rows.push({
        id: `c-${dec.id}`,
        date: dec.dateDecision,
        categorie: 'Décision',
        typeLabel: libelleTypeDecision(dec.typeDecision),
        libelle: dec.libelle || '—',
        acteur,
        acteurRole,
        detail: dec.etapeWorkflow || dec.detailsJson || '—',
        iaDetails: null,
        statutTransition: '—',
      });
    }

    return rows.sort((a, b) => (a.date || '').localeCompare(b.date || ''));
  }

  private buildPrintDocument(): string {
    const rowsHtml = this.lignes
      .map(
        (l) => `
      <tr>
        <td>${this.escape(this.formatDate(l.date))}</td>
        <td>${this.escape(l.categorie)}</td>
        <td>${this.escape(l.typeLabel)}</td>
        <td>${this.escape(l.libelle)}</td>
        <td>${this.escape(l.acteur)}</td>
        <td>${this.escape(l.acteurRole)}</td>
        <td>${this.escape(l.statutTransition)}</td>
      </tr>`
      )
      .join('');

    return `<!DOCTYPE html>
<html lang="fr">
<head>
  <meta charset="utf-8"/>
  <title>Traçabilité ${this.escape(this.reference)} — UIB BNPL</title>
  <style>
    * { box-sizing: border-box; }
    body { font-family: 'Segoe UI', Arial, sans-serif; color: #1a2536; margin: 0; padding: 32px 40px; }
    .header { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 3px solid #d3121f; padding-bottom: 20px; margin-bottom: 24px; }
    .brand img { height: 48px; }
    .brand-tag { font-size: 11px; font-weight: 700; color: #d3121f; letter-spacing: 0.12em; text-transform: uppercase; margin-top: 6px; }
    .doc-meta { text-align: right; font-size: 12px; color: #64748b; }
    .doc-title { font-size: 22px; font-weight: 700; margin: 0 0 6px; }
    .doc-sub { font-size: 13px; color: #64748b; margin: 0; }
    .info-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 28px; }
    .info-box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px 14px; }
    .info-label { font-size: 10px; font-weight: 700; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.06em; }
    .info-value { font-size: 14px; font-weight: 600; margin-top: 4px; }
    table { width: 100%; border-collapse: collapse; font-size: 11px; }
    th { background: #0f1824; color: #fff; padding: 10px 8px; text-align: left; font-weight: 600; }
    td { padding: 9px 8px; border-bottom: 1px solid #e2e8f0; vertical-align: top; }
    tr:nth-child(even) td { background: #f8fafc; }
    .footer { margin-top: 28px; padding-top: 16px; border-top: 1px solid #e2e8f0; font-size: 10px; color: #94a3b8; text-align: center; }
    @media print { body { padding: 16px; } }
  </style>
</head>
<body>
  <div class="header">
    <div class="brand">
      <img src="${window.location.origin}${this.logoSrc}" alt="UIB"/>
      <div class="brand-tag">UIB · BNPL · Document d'audit</div>
    </div>
    <div class="doc-meta">
      <div>Généré le ${this.escape(this.generatedAt)}</div>
      <div>Confidentiel — usage audit interne</div>
    </div>
  </div>
  <h1 class="doc-title">Rapport de traçabilité — ${this.escape(this.reference)}</h1>
  <p class="doc-sub">Cycle de vie complet de la demande de financement</p>
  <div class="info-grid">
    <div class="info-box"><div class="info-label">N° demande</div><div class="info-value">#${this.demandeId}</div></div>
    <div class="info-box"><div class="info-label">Client</div><div class="info-value">${this.escape(this.clientLabel)}</div></div>
    <div class="info-box"><div class="info-label">Commerçant</div><div class="info-value">${this.escape(this.commercantLabel)}</div></div>
    <div class="info-box"><div class="info-label">Statut</div><div class="info-value">${this.escape(this.statutDemande)}</div></div>
    <div class="info-box"><div class="info-label">Événements</div><div class="info-value">${this.lignes.length}</div></div>
    <div class="info-box"><div class="info-label">Référence</div><div class="info-value">${this.escape(this.reference)}</div></div>
  </div>
  <table>
    <thead>
      <tr>
        <th>Date</th><th>Catégorie</th><th>Type</th><th>Libellé</th>
        <th>Acteur</th><th>Rôle</th><th>Transition statut</th>
      </tr>
    </thead>
    <tbody>${rowsHtml || '<tr><td colspan="7">Aucun événement</td></tr>'}</tbody>
  </table>
  <div class="footer">
    UIB — Union Internationale de Banques · Plateforme BNPL · Document généré automatiquement pour audit et conformité.
  </div>
</body>
</html>`;
  }

  private formatDate(value: string): string {
    const d = this.asApiDate(value);
    return d ? d.toLocaleString('fr-FR') : '—';
  }

  private escape(value: string): string {
    return value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
}
