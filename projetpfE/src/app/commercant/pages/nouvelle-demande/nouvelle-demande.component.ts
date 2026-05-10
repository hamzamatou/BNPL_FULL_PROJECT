import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StepperComponent } from '../../components/stepper/stepper.component';
import { InfosClientComponent } from '../../components/infos-client/infos-client.component';
import { DonneesFinancieresComponent } from '../../components/donnees-financieres/donnees-financieres.component';
import { DocumentsComponent } from '../../components/documents/documents.component';
import { ConsentementComponent } from '../../components/consentement/consentement.component';
import {
  CreationDemandeCompleteRequest,
  DemandeService,
  DocumentMultipart,
} from '../../../services/demande.service';

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
  ],
  templateUrl: './nouvelle-demande.component.html',
  styleUrls: ['./nouvelle-demande.component.css'],
})
export class NouvelleDemandeComponent {
  currentStep = 1;
  maxStep = 4;

  infosClientData: any = {};
  donneesFinancieresData: any = {};
  donneesFinancieresPrefill: any = null;
  documentsData: DocumentMultipart[] = [];
  typeProduit = '';

  isSubmitting = false;
  submitSuccess = false;
  submitErrorMessage = '';

  constructor(private demandeService: DemandeService) {}

  nextStep() {
    if (this.currentStep < this.maxStep) this.currentStep++;
  }

  prevStep() {
    if (this.currentStep > 1 && !this.isSubmitting) this.currentStep--;
  }

  setInfosClient(clientData: any) {
    this.infosClientData = clientData;
    this.donneesFinancieresPrefill = null;

    this.demandeService.getDernierDossierFinancierParCin(clientData.cin).subscribe({
      next: (dossier) => {
        this.donneesFinancieresPrefill = {
          ancienneteEmploiMois: dossier.ancienneteEmploiMois ?? 0,
          revenu: dossier.revenuMensuelNet ?? 0,
          autresRevenus: dossier.autresRevenusMensuels ?? 0,
          loyer: dossier.loyerMensuel ?? 0,
          mensualitesCredits: dossier.mensualitesCredits ?? 0,
          autresChargesFixes: dossier.autresChargesFixes ?? 0,
          credits: dossier.encoursCredits ?? 0,
        };
        this.nextStep();
      },
      error: () => {
        this.donneesFinancieresPrefill = null;
        this.nextStep();
      },
    });
  }

  setDonneesFinancieres(data: any) {
    this.donneesFinancieresData = {
      ancienneteEmploiMois: this.infosClientData.ancienneteEmploiMois ?? data.ancienneteEmploiMois,
      revenuMensuelNet: data.revenu,
      autresRevenusMensuels: data.autresRevenus || 0,
      revenuAnnuel: data.revenuAnnuel || 0,
      encoursCredits: data.credits,
      loyerMensuel: data.loyer || 0,
      aUnLoyer: !!data.aUnLoyer,
      aDesCredits: !!data.aDesCredits,
      mensualitesCredits: data.mensualitesCredits || 0,
      autresChargesFixes: data.autresChargesFixes || 0,
      montant: data.montant,
      dureeMois: data.dureeMois,
      typeProduit: data.objet,
      nombreEnfants: data.nombreEnfants ?? 0,
    };

    this.nextStep();
  }

  setDocuments(documents: DocumentMultipart[], typeProduit: string) {
    this.documentsData = documents;
    this.typeProduit = typeProduit;
    this.donneesFinancieresData.typeProduit = typeProduit;

    this.currentStep = 4;
    this.submitDemande();
  }

  submitDemande(): void {
    if (this.isSubmitting) return;

    const request: CreationDemandeCompleteRequest = {
      ...this.infosClientData,
      ...this.donneesFinancieresData,
      documents: this.documentsData,
    };

    this.isSubmitting = true;
    this.submitSuccess = false;
    this.submitErrorMessage = '';

    this.demandeService.creerDemande(request).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.submitSuccess = true;
      },
      error: (err) => {
        this.isSubmitting = false;
        this.submitSuccess = false;
        this.submitErrorMessage = this.resolveBackendError(err);
      },
    });
  }

  private resolveBackendError(err: any): string {
    const apiMessage =
      err?.error?.message ||
      err?.error?.error ||
      (typeof err?.error === 'string' ? err.error : null) ||
      err?.message;

    if (apiMessage && !String(apiMessage).toLowerCase().includes('internal server error')) {
      return String(apiMessage);
    }

    return 'Une erreur est survenue lors de la création. Veuillez vérifier les champs et les pièces obligatoires.';
  }

  restartFlow() {
    this.resetForm();
  }

  private resetForm() {
    this.currentStep = 1;
    this.infosClientData = {};
    this.donneesFinancieresData = {};
    this.donneesFinancieresPrefill = null;
    this.documentsData = [];
    this.typeProduit = '';
    this.isSubmitting = false;
    this.submitSuccess = false;
    this.submitErrorMessage = '';
  }
}
