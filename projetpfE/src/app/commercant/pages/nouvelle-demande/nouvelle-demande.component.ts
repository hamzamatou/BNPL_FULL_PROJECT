import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';

import { StepperComponent }              from '../../components/stepper/stepper.component';
import { InfosClientComponent }           from '../../components/infos-client/infos-client.component';
import { DonneesFinancieresComponent }    from '../../components/donnees-financieres/donnees-financieres.component';
import { DocumentsComponent }             from '../../components/documents/documents.component';
import { ConsentementComponent }          from '../../components/consentement/consentement.component';
import { AnomaliesPanelComponent }        from '../../components/anomalies-panel/anomalies-panel.component';
import { RecommandationsModalComponent }  from '../../components/recommandations-modal/recommandations-modal.component';

import {
  DemandeService,
  DocumentMultipart,
  CreationDemandeCompleteRequest,
  CoherenceErreurReponse,
} from '../../../services/demande.service';

/**
 * Flux IA corrigé :
 *
 *  Step 1  Infos client      → getDernierDossierFinancierParCin (pré-remplissage)
 *  Step 2  Données financières
 *  Step 3  Documents
 *  Step 4  Analyse IA        → POST /analyse-ia
 *                               ├─ 422 → anomalies affichées, boucle sur step 4
 *                               └─ 200 → popup recommandations
 *                                        bouton "Continuer" → step 5
 *  Step 5  Consentement      → POST /creation-complete (persiste reco + envoie email)
 *                               └─ 201 → step 6 succès
 *  Step 6  Succès
 *
 * Invariant : on n'envoie JAMAIS l'email de consentement
 * tant que la cohérence n'est pas validée.
 */
@Component({
  selector: 'app-nouvelle-demande',
  standalone: true,
  imports: [
    CommonModule,
    StepperComponent,
    InfosClientComponent,
    DonneesFinancieresComponent,
    DocumentsComponent,
    ConsentementComponent,
    AnomaliesPanelComponent,
    RecommandationsModalComponent,
  ],
  templateUrl: './nouvelle-demande.component.html',
  styleUrls: ['./nouvelle-demande.component.css'],
})
export class NouvelleDemandeComponent {

  currentStep = 1;

  // ── Données des étapes ───────────────────────────────────────────────────
  infosClientData: any          = {};
  donneesFinancieresData: any   = {};
  donneesFinancieresPrefill: any = null;
  documentsData: DocumentMultipart[] = [];
  typeProduit = '';

  // ── État step 4 : Analyse IA ─────────────────────────────────────────────
  isAnalysing    = false;
  analyseError   = '';
  anomalies: string[]       = [];   // anomalies bloquantes → boucle step 4
  recommandations: string[] = [];   // recommandations → popup

  // ── État step 5 : Création ───────────────────────────────────────────────
  isSubmitting       = false;
  submitSuccess      = false;
  submitErrorMessage = '';

  constructor(private demandeService: DemandeService) {}

  // ── STEP 1 : infos client ────────────────────────────────────────────────

  setInfosClient(clientData: any): void {
    this.infosClientData = clientData;

    this.demandeService.getDernierDossierFinancierParCin(clientData.cin).subscribe({
      next:  dossier => { this.donneesFinancieresPrefill = dossier; this.currentStep = 2; },
      error: ()      => { this.donneesFinancieresPrefill = null;    this.currentStep = 2; },
    });
  }

  // ── STEP 2 : données financières ─────────────────────────────────────────

  setDonneesFinancieres(data: any): void {
    this.donneesFinancieresData = { ...data, nombreEnfants: data.nombreEnfants ?? 0 };
    this.currentStep = 3;
  }

  // ── STEP 3 : documents → déclenche l'analyse IA ──────────────────────────

  setDocuments(documents: DocumentMultipart[], typeProduit: string): void {
    this.documentsData = documents;
    this.typeProduit   = typeProduit;
    this.donneesFinancieresData.typeProduit = typeProduit;

    // Passer directement à l'analyse IA (step 4)
    this.currentStep = 4;
    this.lancerAnalyseIA();
  }

  // ── STEP 4 : analyse IA ───────────────────────────────────────────────────

  /**
   * Appelle POST /analyse-ia.
   * Résultat 422 → anomalies affichées, le bouton "Relancer l'analyse" permet de réessayer.
   * Résultat 200 → recommandations stockées, bouton "Continuer" déblocqué.
   */
  lancerAnalyseIA(): void {
    if (this.isAnalysing) return;

    this.isAnalysing    = true;
    this.analyseError   = '';
    this.anomalies      = [];
    this.recommandations = [];

    const request: CreationDemandeCompleteRequest = {
      ...this.infosClientData,
      ...this.donneesFinancieresData,
      documents: this.documentsData,
    };

    this.demandeService.analyseIA(request, this.documentsData).subscribe({
      next: (res) => {
        this.isAnalysing     = false;
        this.recommandations = res.recommandations ?? [];
        // anomalies vide → cohérence OK → afficher le popup puis débloquer "Continuer"
      },

      error: (err: HttpErrorResponse) => {
        this.isAnalysing = false;

        if (err.status === 422) {
          const body = err.error as CoherenceErreurReponse;
          this.anomalies    = body?.anomalies ?? [];
          this.analyseError = body?.message   ?? 'Incohérences détectées';
        } else {
          this.analyseError = err.error?.message ?? 'Erreur lors de lsanalyse IA';
        }
      },
    });
  }

  /** Vrai quand la cohérence est validée (pas d'anomalie, analyse terminée). */
  get coherenceOK(): boolean {
    return !this.isAnalysing && this.anomalies.length === 0 && this.analyseError === '';
  }

  // ── STEP 4 → 5 : le commerçant accepte les recommandations ───────────────

  goToConsent(): void {
    if (!this.coherenceOK) return;
    this.currentStep = 5;
  }

  // ── STEP 5 : création de la demande + email consentement ──────────────────

  submitDemande(): void {
    if (this.isSubmitting) return;

    this.isSubmitting       = true;
    this.submitErrorMessage = '';

    const request: CreationDemandeCompleteRequest = {
      ...this.infosClientData,
      ...this.donneesFinancieresData,
      documents: this.documentsData,
    };

    // Transmettre les recommandations déjà calculées au step 4
    const recommandationsJson = JSON.stringify(this.recommandations);

    this.demandeService.creerDemande(request, recommandationsJson).subscribe({
      next: () => {
        this.isSubmitting  = false;
        this.submitSuccess = true;
        this.currentStep   = 6;
      },

      error: (err: HttpErrorResponse) => {
        this.isSubmitting = false;
        this.submitErrorMessage = err.error?.message ?? 'Erreur lors de la création';
      },
    });
  }

  // ── Navigation ───────────────────────────────────────────────────────────

  prevStep(): void {
    if (this.currentStep > 1 && !this.isSubmitting && !this.isAnalysing) {
      // Retour depuis step 4 → revenir aux documents (step 3)
      if (this.currentStep === 4) {
        this.anomalies       = [];
        this.recommandations = [];
        this.analyseError    = '';
      }
      this.currentStep--;
    }
  }

  // ── Réinitialisation ─────────────────────────────────────────────────────

  restartFlow(): void {
    this.currentStep               = 1;
    this.infosClientData           = {};
    this.donneesFinancieresData    = {};
    this.donneesFinancieresPrefill = null;
    this.documentsData             = [];
    this.typeProduit               = '';
    this.isAnalysing               = false;
    this.analyseError              = '';
    this.anomalies                 = [];
    this.recommandations           = [];
    this.isSubmitting              = false;
    this.submitSuccess             = false;
    this.submitErrorMessage        = '';
  }
}