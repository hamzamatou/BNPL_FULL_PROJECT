import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';

import { StepperComponent }              from '../../components/stepper/stepper.component';
import { InfosClientComponent }           from '../../components/infos-client/infos-client.component';
import { DonneesFinancieresComponent }    from '../../components/donnees-financieres/donnees-financieres.component';
import { DocumentsComponent }             from '../../components/documents/documents.component';
import { ConsentementComponent }          from '../../components/consentement/consentement.component';
import { AnomaliesPanelComponent }        from '../../components/anomalies-panel/anomalies-panel.component';
import { AlertesPanelComponent }          from '../../components/alertes-panel/alertes-panel.component';
import { RecommandationsModalComponent }  from '../../components/recommandations-modal/recommandations-modal.component';

import {
  DemandeService,
  DocumentMultipart,
  CreationDemandeCompleteRequest,
  CoherenceErreurReponse,
} from '../../../services/demande.service';
import { applyCoherenceCorrections } from '../../../utils/coherence-corrections.util';

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
  anomalies: string[] = [];
  alertes: string[] = [];
  recommandations: string[] = [];
  champsCorriges: string[] = [];
  iaValidee = false;
  recoModalOpen = false;

  isSubmitting = false;
  submitSuccess = false;
  submitErrorMessage = '';

  constructor(private demandeService: DemandeService) {}

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

    this.demandeService.analyseIA(this.buildRequest(), this.documentsData).subscribe({
      next: res => {
        this.isAnalysing = false;
        this.recommandations = res.recommandations ?? [];
        this.alertes = res.alertes ?? [];
        this.appliquerCorrections(res.corrections);
        this.iaValidee = true;
        this.recoModalOpen = (res.recommandations?.length ?? 0) > 0;
      },
      error: (err: HttpErrorResponse) => {
        this.isAnalysing = false;
        this.iaValidee = false;

        if (err.status === 422) {
          const body = err.error as CoherenceErreurReponse;
          this.anomalies = body?.anomalies ?? [];
          this.analyseError = body?.message ?? 'Incohérences détectées';
          this.appliquerCorrections(body?.corrections);
        } else {
          this.analyseError = err.error?.message ?? 'Erreur lors de l\'analyse IA';
        }
      },
    });
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

  get afficherRecommandations(): boolean {
    return this.coherenceOK && this.recommandations.length > 0;
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

    this.demandeService.creerDemande(this.buildRequest(), recommandationsJson).subscribe({
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
    this.recoModalOpen = false;
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
    this.resetIaState();
    this.isSubmitting = false;
    this.submitSuccess = false;
    this.submitErrorMessage = '';
  }
}
