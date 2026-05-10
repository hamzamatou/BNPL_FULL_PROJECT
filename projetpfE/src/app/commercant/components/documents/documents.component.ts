import { Component, Input, Output, EventEmitter } from '@angular/core';
import { DocumentMultipart } from '../../../services/demande.service';
import { NgFor, NgIf } from '@angular/common';

@Component({
  selector: 'app-documents',
  standalone: true,
  imports: [NgIf, NgFor],
  templateUrl: './documents.component.html',
  styleUrls: ['./documents.component.css'],
})
export class DocumentsComponent {
  @Input() status: 'pending' | 'active' | 'completed' = 'pending';
  @Input() aUnLoyer = false;
  @Input() montant = 0;
  @Output() nextStep = new EventEmitter<{ documents: DocumentMultipart[]; typeProduit: string }>();
  @Output() prevStep = new EventEmitter<void>();

  uploadedFiles: DocumentMultipart[] = [];
  typeProduit: string = 'BNPL';

  readonly MAX_SIZE = 10 * 1024 * 1024;

  onFile(event: any, typeDocument: string) {
    const file: File = event.target.files[0];
    if (!file) return;

    if (file.size > this.MAX_SIZE) {
      alert(`Le fichier ${file.name} dépasse la taille maximale de 10MB !`);
      return;
    }

    const existingIndex = this.uploadedFiles.findIndex((f) => f.typeDocument === typeDocument);
    if (existingIndex !== -1) {
      this.uploadedFiles[existingIndex] = { typeDocument, file };
    } else {
      this.uploadedFiles.push({ typeDocument, file });
    }
  }

  get requiresDevis(): boolean {
    return Number(this.montant) > 10000;
  }

  get requiredDocumentTypes(): string[] {
    const required = ['cin', 'fiche_paie_m1', 'fiche_paie_m2', 'fiche_paie_m3', 'attestation_travail'];
    if (this.aUnLoyer) required.push('justificatif_loyer');
    if (this.requiresDevis) required.push('devis');
    return required;
  }

  isUploaded(typeDocument: string): boolean {
    return this.uploadedFiles.some((f) => f.typeDocument === typeDocument);
  }

  private missingRequiredDocs(): string[] {
    return this.requiredDocumentTypes.filter((t) => !this.isUploaded(t));
  }

  goNext() {
    const missing = this.missingRequiredDocs();
    if (missing.length > 0) {
      alert('Veuillez charger toutes les pièces obligatoires avant de continuer.');
      return;
    }

    const validDocs = this.uploadedFiles.filter((d) => d.file.size <= this.MAX_SIZE);
    if (validDocs.length < this.uploadedFiles.length) {
      alert('Certains fichiers dépassent 10MB et ne seront pas envoyés !');
    }

    this.nextStep.emit({ documents: validDocs, typeProduit: this.typeProduit });
  }
}
