import { Component, EventEmitter, Output, Input, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SituationFamilialeCode } from '../../../services/demande.service';

type DonneesFinancieresPrefill = {
  ancienneteEmploiMois?: number;
  revenu?: number;
  autresRevenus?: number;
  loyer?: number;
  mensualitesCredits?: number;
  autresChargesFixes?: number;
  credits?: number;
};

@Component({
  selector: 'app-donnees-financieres',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './donnees-financieres.component.html',
  styleUrls: ['./donnees-financieres.component.css'],
})
export class DonneesFinancieresComponent implements OnChanges {
  @Input() status: 'active' | 'completed' | 'pending' = 'pending';

  @Input() situationFamiliale: SituationFamilialeCode | '' = '';
  @Input() nombreEnfantsClient = 0;
  @Input() ancienneteEmploiMoisClient = 0;

  @Input() prefill: DonneesFinancieresPrefill | null = null;

  revenu!: number;
  autresRevenus!: number;
  revenuAnnuel!: number;
  revenuAnnuelModifie = false;
  aUnLoyer = false;
  aDesCredits = false;
  loyer!: number;
  mensualitesCredits!: number;
  autresChargesFixes!: number;
  credits!: number;

  montant!: number;
  dureeMois!: number;
  objet!: string;

  formError = '';
  revenuError = '';
  autresRevenusError = '';
  revenuAnnuelError = '';
  loyerError = '';
  mensualitesCreditsError = '';
  autresChargesFixesError = '';
  creditsError = '';
  montantError = '';
  nombreEnfants = 0;

  @Output() nextStep = new EventEmitter<any>();
  @Output() prevStep = new EventEmitter<void>();

  constructor() {
    this.revenu = 0;
    this.autresRevenus = 0;
    this.revenuAnnuel = 0;
    this.loyer = 0;
    this.mensualitesCredits = 0;
    this.autresChargesFixes = 0;
    this.credits = 0;
    this.montant = 0;
    this.dureeMois = 24;
    this.objet = '';
  }

  get showEnfants(): boolean {
    return this.situationFamiliale !== '' && this.situationFamiliale !== 'CELIBATAIRE';
  }

  get revenuTotal(): number {
    const r = Number(this.revenu);
    const a = Number(this.autresRevenus);
    const rr = Number.isFinite(r) ? r : 0;
    const aa = Number.isFinite(a) ? a : 0;
    return rr + aa;
  }

  get chargesMensuelles(): number {
    const l = this.aUnLoyer ? Number(this.loyer) : 0;
    const m = this.aDesCredits ? Number(this.mensualitesCredits) : 0;
    const o = Number(this.autresChargesFixes);
    const e = this.showEnfants ? this.nombreEnfants * 250 : 0;
    return (
      (Number.isFinite(l) ? l : 0) +
      (Number.isFinite(m) ? m : 0) +
      (Number.isFinite(o) ? o : 0) +
      e
    );
  }

  /** Mensualités des crédits déjà en cours (hors BNPL demandé). */
  get mensualitesCreditsMensuelles(): number {
    return this.aDesCredits ? (Number.isFinite(Number(this.mensualitesCredits)) ? Number(this.mensualitesCredits) : 0) : 0;
  }

  /** Mensualité du financement BNPL demandé (montant / durée). */
  get mensualiteBnpl(): number {
    const m = Number(this.montant);
    const d = Number(this.dureeMois);
    if (!Number.isFinite(m) || m <= 0 || !Number.isFinite(d) || d <= 0) return 0;
    return m / d;
  }

  /**
   * Taux d'endettement type BCT (indicatif) :
   * (mensualités crédits existants + mensualité BNPL) / revenus nets mensuels totaux.
   * Hors loyer/charges vie : ils ne sont pas dans le ratio réglementaire 40 %.
   */
  get tauxEndettementPct(): number {
    if (this.revenuTotal <= 0) return 0;
    const totalMensualitesCredit = this.mensualitesCreditsMensuelles + this.mensualiteBnpl;
    return Math.min(100, Math.round((totalMensualitesCredit / this.revenuTotal) * 100));
  }

  /** Charge globale vie + crédits (hors définition BCT) — pour info / cohérence interne. */
  get tauxChargesGlobalesPct(): number {
    if (this.revenuTotal <= 0) return 0;
    return Math.min(100, Math.round((this.chargesMensuelles / this.revenuTotal) * 100));
  }

  get sousSeuilEndettement(): boolean {
    return this.tauxEndettementPct <= 40;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['nombreEnfantsClient']) {
      this.nombreEnfants = Math.max(0, Number(this.nombreEnfantsClient || 0));
    }
    if (changes['prefill'] && this.prefill) {
      const p = this.prefill;
      if (p.revenu !== undefined) this.revenu = p.revenu;
      if (p.autresRevenus !== undefined) this.autresRevenus = p.autresRevenus;
      if (p.loyer !== undefined) {
        this.loyer = p.loyer;
        this.aUnLoyer = p.loyer > 0;
      }
      if (p.mensualitesCredits !== undefined) {
        this.mensualitesCredits = p.mensualitesCredits;
        this.aDesCredits = p.mensualitesCredits > 0 || (p.credits ?? 0) > 0;
      }
      if (p.autresChargesFixes !== undefined) this.autresChargesFixes = p.autresChargesFixes;
      if (p.credits !== undefined) this.credits = p.credits;
    }
    this.recalculerRevenuAnnuel();
  }

  next() {
    this.formError = '';
    this.revenuError = '';
    this.autresRevenusError = '';
    this.revenuAnnuelError = '';
    this.loyerError = '';
    this.mensualitesCreditsError = '';
    this.autresChargesFixesError = '';
    this.creditsError = '';
    this.montantError = '';

    const isFiniteNumber = (v: any): boolean =>
      v !== null && v !== undefined && v !== '' && Number.isFinite(Number(v));

    const requireField = (v: any, setError: (msg: string) => void, msg: string) => {
      if (!isFiniteNumber(v)) setError(msg);
    };

    requireField(this.revenu, (m) => (this.revenuError = m), 'Champ obligatoire.');
    requireField(this.revenuAnnuel, (m) => (this.revenuAnnuelError = m), 'Champ obligatoire.');
    if (this.aUnLoyer) {
      requireField(this.loyer, (m) => (this.loyerError = m), 'Champ obligatoire.');
    }
    if (this.aDesCredits) {
      requireField(this.credits, (m) => (this.creditsError = m), 'Champ obligatoire.');
      requireField(this.mensualitesCredits, (m) => (this.mensualitesCreditsError = m), 'Champ obligatoire.');
    }
    requireField(this.autresChargesFixes, (m) => (this.autresChargesFixesError = m), 'Champ obligatoire.');
    requireField(this.montant, (m) => (this.montantError = m), 'Champ obligatoire.');

    if (!this.objet || !this.objet.trim()) {
      this.formError = 'Veuillez remplir les champs obligatoires.';
    }

    const toNum = (v: any): number => Number(v);

    if (isFiniteNumber(this.revenu) && toNum(this.revenu) < 0) {
      this.revenuError = 'Doit être >= 0.';
    }
    if (isFiniteNumber(this.autresRevenus) && toNum(this.autresRevenus) < 0) {
      this.autresRevenusError = 'Doit être >= 0.';
    }
    if (isFiniteNumber(this.revenuAnnuel) && toNum(this.revenuAnnuel) < 0) {
      this.revenuAnnuelError = 'Doit être >= 0.';
    }
    if (this.aUnLoyer && isFiniteNumber(this.loyer) && toNum(this.loyer) < 0) {
      this.loyerError = 'Doit être >= 0.';
    }
    if (this.aDesCredits && isFiniteNumber(this.mensualitesCredits) && toNum(this.mensualitesCredits) < 0) {
      this.mensualitesCreditsError = 'Doit être >= 0.';
    }
    if (isFiniteNumber(this.autresChargesFixes) && toNum(this.autresChargesFixes) < 0) {
      this.autresChargesFixesError = 'Doit être >= 0.';
    }
    if (this.aDesCredits && isFiniteNumber(this.credits) && toNum(this.credits) < 0) {
      this.creditsError = 'Doit être >= 0.';
    }
    if (isFiniteNumber(this.montant) && toNum(this.montant) < 0) {
      this.montantError = 'Doit être >= 0.';
    }

    const hasAnyError =
      !!this.revenuError ||
      !!this.autresRevenusError ||
      !!this.revenuAnnuelError ||
      !!this.loyerError ||
      !!this.mensualitesCreditsError ||
      !!this.autresChargesFixesError ||
      !!this.creditsError ||
      !!this.montantError ||
      !!this.formError;

    if (hasAnyError) return;

    const revenuTotal = toNum(this.revenu) + (isFiniteNumber(this.autresRevenus) ? toNum(this.autresRevenus) : 0);
    const loyer = this.aUnLoyer ? toNum(this.loyer) : 0;
    const chargeEnfants = this.showEnfants ? this.nombreEnfants * 250 : 0;
    const mensualites = this.aDesCredits ? toNum(this.mensualitesCredits) : 0;
    const credits = this.aDesCredits ? toNum(this.credits) : 0;
    const chargesMensuelles = loyer + mensualites + toNum(this.autresChargesFixes) + chargeEnfants;
    if (chargesMensuelles > revenuTotal) {
      this.formError = 'La somme des charges mensuelles ne doit pas dépasser le revenu mensuel.';
      return;
    }

    const duree = Number(this.dureeMois);
    if (!Number.isFinite(duree) || duree < 0) {
      this.formError = 'Durée invalide.';
      return;
    }

    const neFinal = this.showEnfants ? Number(this.nombreEnfants) : 0;

    this.nextStep.emit({
      ancienneteEmploiMois: Number(this.ancienneteEmploiMoisClient || 0),
      revenu: toNum(this.revenu),
      autresRevenus: isFiniteNumber(this.autresRevenus) ? toNum(this.autresRevenus) : 0,
      revenuAnnuel: toNum(this.revenuAnnuel),
      aUnLoyer: this.aUnLoyer,
      aDesCredits: this.aDesCredits,
      loyer: loyer,
      mensualitesCredits: mensualites,
      autresChargesFixes: toNum(this.autresChargesFixes),
      credits: credits,
      montant: toNum(this.montant),
      dureeMois: duree,
      objet: this.objet.trim(),
      nombreEnfants: neFinal,
    });
  }

  onRevenuMensuelChanged(): void {
    if (!this.revenuAnnuelModifie) this.recalculerRevenuAnnuel();
  }

  onAutresRevenusChanged(): void {
    if (!this.revenuAnnuelModifie) this.recalculerRevenuAnnuel();
  }

  onRevenuAnnuelChanged(): void {
    this.revenuAnnuelModifie = true;
  }

  private recalculerRevenuAnnuel(): void {
    const base = (Number(this.revenu) || 0) + (Number(this.autresRevenus) || 0);
    this.revenuAnnuel = Math.max(0, Math.round(base * 12));
  }

  prev() {
    this.prevStep.emit();
  }
}
