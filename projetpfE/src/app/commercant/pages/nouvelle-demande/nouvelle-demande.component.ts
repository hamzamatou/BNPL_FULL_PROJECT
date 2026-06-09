import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { StepperComponent }              from '../../components/stepper/stepper.component';
import { InfosClientComponent }           from '../../components/infos-client/infos-client.component';
import { DonneesFinancieresComponent }    from '../../components/donnees-financieres/donnees-financieres.component';
import { DocumentsComponent }             from '../../components/documents/documents.component';
import { ConsentementComponent }          from '../../components/consentement/consentement.component';
import { AnomaliesPanelComponent }        from '../../components/anomalies-panel/anomalies-panel.component';
import { AlertesPanelComponent }          from '../../components/alertes-panel/alertes-panel.component';
import { RecommandationsModalComponent }  from '../../components/recommandations-modal/recommandations-modal.component';
import { normaliserRecommandationsApresAnalyse } from '../../../shared/utils/recommandations.util';

import {
  DemandeService,
  DocumentMultipart,
  CreationDemandeCompleteRequest,
  CoherenceErreurReponse,
  SituationFamilialeCode,
} from '../../../services/demande.service';
import { applyCoherenceCorrections } from '../../../utils/coherence-corrections.util';
import {
  CoherenceAnomalie,
  normalizeCoherenceAnomalies,
} from '../../../models/coherence-anomalie.model';

/**
 *  Step 1–3  Formulaire
 *  Step 4    Analyse IA → recommandations (popup) + possibilité de corriger
 *  Step 5    Consentement → création BDD
 *  Step 6    Succès
 */
@Component({
  selector: 'app-nouvelle-demande',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    StepperComponent,
    InfosClientComponent,
    DonneesFinancieresComponent,
    DocumentsComponent,
    ConsentementComponent,
    AnomaliesPanelComponent,
    AlertesPanelComponent,
    RecommandationsModalComponent,
  ],
  templateUrl: './nouvelle-demande.component.html',
  styleUrls: ['./nouvelle-demande.component.css'],
})
export class NouvelleDemandeComponent {

  currentStep = 1;

  infosClientData: Record<string, unknown> = {};
  donneesFinancieresData: Record<string, unknown> = {};
  donneesFinancieresPrefill: Record<string, unknown> | null = null;
  documentsData: DocumentMultipart[] = [];
  typeProduit = '';

  isAnalysing = false;
  analyseError = '';
  anomalies: CoherenceAnomalie[] = [];
  alertes: string[] = [];
  recommandations: string[] = [];
  champsCorriges: string[] = [];
  iaValidee = false;
  processInstanceId = '';
  analysisSessionId = '';
  anomaliesModalOpen = false;
  recommandationsModalOpen = false;

  isSubmitting = false;
  submitSuccess = false;
  submitErrorMessage = '';

  constructor(private demandeService: DemandeService) {}

  /** Typage explicite pour le template (infosClientData est un Record). */
  get clientSituationFamiliale(): SituationFamilialeCode | '' {
    const v = this.infosClientData['situationFamiliale'];
    if (v === null || v === undefined || v === '') return '';
    return String(v) as SituationFamilialeCode;
  }

  get clientNombreEnfants(): number {
    const v = this.infosClientData['nombreEnfants'];
    const n = Number(v);
    return Number.isFinite(n) ? Math.trunc(n) : 0;
  }

  get clientAncienneteEmploiMois(): number {
    const v = this.infosClientData['ancienneteEmploiMois'];
    const n = Number(v);
    return Number.isFinite(n) ? Math.trunc(n) : 0;
  }

  /** Transmis à l'étape documents (loyer / devis conditionnels). */
  get documentsAUnLoyer(): boolean {
    const v = this.donneesFinancieresData['aUnLoyer'];
    return v === true || v === 'true';
  }

  get documentsMontant(): number {
    const n = Number(this.donneesFinancieresData['montant'] ?? 0);
    return Number.isFinite(n) ? n : 0;
  }

  get stepperStep(): number {
    if (this.currentStep >= 5) return 4;
    return this.currentStep;
  }

  setInfosClient(clientData: Record<string, unknown>): void {
    this.infosClientData = clientData;
    this.resetIaState();

    const cin = clientData['cin'];
    if (!cin) {
      this.donneesFinancieresPrefill = null;
      this.currentStep = 2;
      return;
    }

    this.demandeService.getDernierDossierFinancierParCin(String(cin)).subscribe({
      next: dossier => {
        this.donneesFinancieresPrefill = dossier as unknown as Record<string, unknown>;
        this.currentStep = 2;
      },
      error: () => {
        this.donneesFinancieresPrefill = null;
        this.currentStep = 2;
      },
    });
  }

  setDonneesFinancieres(data: Record<string, unknown>): void {
    this.donneesFinancieresData = { ...data, nombreEnfants: data['nombreEnfants'] ?? 0 };
    this.resetIaState();
    this.currentStep = 3;
  }

  setDocuments(documents: DocumentMultipart[], typeProduit: string): void {
    this.documentsData = documents;
    this.typeProduit = typeProduit;
    this.donneesFinancieresData['typeProduit'] = typeProduit;
    this.resetIaState();
    this.currentStep = 4;
    this.lancerAnalyseIA();
  }

  lancerAnalyseIA(): void {
    if (this.isAnalysing) return;

    this.isAnalysing = true;
    this.analyseError = '';
    this.anomalies = [];
    this.alertes = [];
    this.recommandations = [];
    this.champsCorriges = [];
    this.iaValidee = false;

    const request = this.buildRequest();
    const docs = this.documentsData;
    const processId = this.processInstanceId || undefined;

    this.demandeService.verifierCoherence(request, docs, processId).subscribe({
      next: coherenceRes => {
        if (coherenceRes.processInstanceId) {
          this.processInstanceId = coherenceRes.processInstanceId;
        }
        if (coherenceRes.analysisSessionId) {
          this.analysisSessionId = coherenceRes.analysisSessionId;
        }
        this.alertes = coherenceRes.alertes ?? [];
        this.appliquerCorrections(coherenceRes.corrections);

        if (coherenceRes.recommandations != null) {
          this.finaliserAnalyseOk(coherenceRes.recommandations);
          return;
        }

        // Rétrocompat : ancien backend sans recommandations dans /coherence
        this.demandeService
          .obtenirRecommandations(
            this.processInstanceId || undefined,
            this.analysisSessionId || undefined
          )
          .subscribe({
            next: recoRes => {
              if (recoRes.processInstanceId) {
                this.processInstanceId = recoRes.processInstanceId;
              }
              this.finaliserAnalyseOk(recoRes.recommandations ?? []);
            },
            error: (err: HttpErrorResponse) => {
              this.isAnalysing = false;
              this.iaValidee = false;
              this.analyseError =
                err.error?.message ?? 'Erreur lors de la génération des recommandations';
            },
          });
      },
      error: (err: HttpErrorResponse) => {
        this.isAnalysing = false;
        this.iaValidee = false;
        if (err.status === 422) {
          const body = err.error as CoherenceErreurReponse;
          this.anomalies = normalizeCoherenceAnomalies(body?.anomalies);
          this.anomaliesModalOpen = this.anomalies.length > 0;
          this.analyseError = body?.message ?? 'Incohérences détectées';
          this.appliquerCorrections(body?.corrections);
        } else {
          this.analyseError = err.error?.message ?? 'Erreur lors de l\'analyse de cohérence';
        }
      },
    });
  }

  private finaliserAnalyseOk(recommandations: string[]): void {
    this.isAnalysing = false;
    this.recommandations = normaliserRecommandationsApresAnalyse(recommandations);
    this.iaValidee = true;
    this.anomaliesModalOpen = false;
    this.recommandationsModalOpen = true;
  }

  continuerApresRecommandations(): void {
    this.recommandationsModalOpen = false;
    this.goToConsent();
  }

  private appliquerCorrections(corrections: Record<string, unknown> | undefined): void {
    const result = applyCoherenceCorrections(
      corrections,
      this.infosClientData,
      this.donneesFinancieresData
    );
    this.infosClientData = result.infosClientData;
    this.donneesFinancieresData = result.donneesFinancieresData;
    this.donneesFinancieresPrefill = result.donneesFinancieresPrefill;
    this.champsCorriges = result.champsCorriges;
  }

  get coherenceOK(): boolean {
    return this.iaValidee && !this.isAnalysing && this.anomalies.length === 0 && !this.analyseError;
  }

  goToConsent(): void {
    if (!this.coherenceOK) return;
    this.currentStep = 5;
  }

  modifierFormulaire(): void {
    const cinCorrige = this.champsCorriges.some(c => c.toLowerCase().includes('cin'));
    this.resetIaState();
    this.currentStep = cinCorrige ? 1 : 2;
  }

  submitDemande(): void {
    if (this.isSubmitting || !this.coherenceOK) return;

    this.isSubmitting = true;
    this.submitErrorMessage = '';

    const recommandationsJson = JSON.stringify(this.recommandations);

    this.demandeService.creerDemande(
      this.buildRequest(),
      recommandationsJson,
      this.processInstanceId || undefined
    ).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.submitSuccess = true;
        this.currentStep = 6;
      },
      error: (err: HttpErrorResponse) => {
        this.isSubmitting = false;
        this.submitErrorMessage = err.error?.message ?? 'Erreur lors de la création';
      },
    });
  }

  private buildRequest(): CreationDemandeCompleteRequest {
    const finances = this.donneesFinancieresData;
    return {
      ...(this.infosClientData as object),
      revenuMensuelNet: Number(finances['revenu'] ?? finances['revenuMensuelNet'] ?? 0),
      autresRevenusMensuels: Number(finances['autresRevenus'] ?? finances['autresRevenusMensuels'] ?? 0),
      revenuAnnuel: Number(finances['revenuAnnuel'] ?? 0),
      encoursCredits: Number(finances['credits'] ?? finances['encoursCredits'] ?? 0),
      loyerMensuel: Number(finances['loyer'] ?? finances['loyerMensuel'] ?? 0),
      mensualitesCredits: Number(finances['mensualitesCredits'] ?? 0),
      autresChargesFixes: Number(finances['autresChargesFixes'] ?? 0),
      montant: Number(finances['montant'] ?? 0),
      dureeMois: Number(finances['dureeMois'] ?? 24),
      typeProduit: String(finances['typeProduit'] ?? this.typeProduit ?? ''),
      documents: this.documentsData,
    } as CreationDemandeCompleteRequest;
  }

  private resetIaState(): void {
    this.isAnalysing = false;
    this.analyseError = '';
    this.anomalies = [];
    this.alertes = [];
    this.recommandations = [];
    this.champsCorriges = [];
    this.iaValidee = false;
    this.processInstanceId = '';
    this.analysisSessionId = '';
    this.anomaliesModalOpen = false;
    this.recommandationsModalOpen = false;
  }

  prevStep(): void {
    if (this.currentStep > 1 && !this.isSubmitting && !this.isAnalysing) {
      if (this.currentStep === 4 || this.currentStep === 5) {
        this.resetIaState();
        this.submitErrorMessage = '';
      }
      this.currentStep--;
    }
  }

  restartFlow(): void {
    this.currentStep = 1;
    this.infosClientData = {};
    this.donneesFinancieresData = {};
    this.donneesFinancieresPrefill = null;
    this.documentsData = [];
    this.typeProduit = '';
    this.processInstanceId = '';
    this.analysisSessionId = '';
    this.resetIaState();
    this.isSubmitting = false;
    this.submitSuccess = false;
    this.submitErrorMessage = '';
  }
}
