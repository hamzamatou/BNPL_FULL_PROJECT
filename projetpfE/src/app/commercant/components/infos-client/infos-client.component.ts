import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import {
  libelleSituationFamiliale,
  SituationFamilialeCode,
} from '../../../services/demande.service';

@Component({
  selector: 'app-infos-client',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './infos-client.component.html',
  styleUrls: ['./infos-client.component.css']
})
export class InfosClientComponent implements OnChanges {
  @Input() status: 'active' | 'completed' | 'pending' = 'active';

  /** Données à réafficher après corrections IA (retour step 4 → step 1). */
  @Input() initialData: Record<string, unknown> | null = null;

  // Tous les champs obligatoires du client
  nom: string = '';
  prenom: string = '';
  email: string = '';
  telephone: string = '';
  cin: string = '';
  adresse: string = '';
  sexe: string = '';
  profession: string = '';
  employeur: string = '';
  typeContrat: 'CDI' | 'CDD' | '' = '';
  dateNaissance: string = '';
  ancienneteEmploiMois: number | null = 0;
  situationFamiliale: SituationFamilialeCode | '' = '';
  nombreEnfants: number | null = 0;

  formError = '';
  telephoneError = '';
  cinError = '';
  ancienneteError = '';
  dateNaissanceError = '';

  @Output() nextStep = new EventEmitter<any>();

  get shouldShowNombreEnfants(): boolean {
    return this.situationFamiliale !== '' && this.situationFamiliale !== 'CELIBATAIRE';
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['initialData'] && this.initialData) {
      this.patchFromInitial(this.initialData);
    }
  }

  private patchFromInitial(data: Record<string, unknown>): void {
    const str = (k: string) => (data[k] != null ? String(data[k]) : undefined);
    const num = (k: string) => {
      const v = data[k];
      if (v === null || v === undefined || v === '') return undefined;
      const n = Number(v);
      return Number.isFinite(n) ? n : undefined;
    };

    if (str('nom')) this.nom = str('nom')!;
    if (str('prenom')) this.prenom = str('prenom')!;
    if (str('email')) this.email = str('email')!;
    if (str('telephone')) this.telephone = str('telephone')!;
    if (str('cin')) this.cin = str('cin')!;
    if (str('adresse')) this.adresse = str('adresse')!;
    if (str('sexe')) this.sexe = str('sexe')!;
    if (str('profession')) this.profession = str('profession')!;
    if (str('employeur')) this.employeur = str('employeur')!;
    if (str('typeContrat')) this.typeContrat = str('typeContrat') as 'CDI' | 'CDD' | '';
    if (str('dateNaissance')) this.dateNaissance = str('dateNaissance')!;
    if (str('situationFamiliale')) {
      this.situationFamiliale = str('situationFamiliale') as SituationFamilialeCode;
    }
    const anc = num('ancienneteEmploiMois');
    if (anc !== undefined) this.ancienneteEmploiMois = anc;
    const ne = num('nombreEnfants');
    if (ne !== undefined) this.nombreEnfants = ne;
  }

  libelleSituation(): string {
    return libelleSituationFamiliale(this.situationFamiliale);
  }

  situationBadgeClass(): string {
    const c = this.situationFamiliale;
    if (!c) return '';
    const map: Record<string, string> = {
      CELIBATAIRE: 'sit-badge sit-badge--celib',
      MARIE: 'sit-badge sit-badge--marie',
      PACSE: 'sit-badge sit-badge--pacse',
      DIVORCE: 'sit-badge sit-badge--divorce',
      VEUF: 'sit-badge sit-badge--veuf',
      CONCUBINAGE: 'sit-badge sit-badge--concubinage',
    };
    return map[c] ?? 'sit-badge';
  }

  next() {
    this.formError = '';
    this.telephoneError = '';
    this.cinError = '';
    this.ancienneteError = '';
    this.dateNaissanceError = '';

    const requiredMissing =
      !this.nom.trim() ||
      !this.prenom.trim() ||
      !this.email.trim() ||
      !this.telephone.trim() ||
      !this.cin.trim() ||
      !this.adresse.trim() ||
      !this.sexe.trim() ||
      !this.profession.trim() ||
      !this.employeur.trim() ||
      !this.typeContrat ||
      !this.dateNaissance ||
      !this.situationFamiliale;

    if (requiredMissing) {
      this.formError = 'Veuillez remplir tous les champs obligatoires.';
      return;
    }

    const cinDigits = this.cin.trim();
    if (!/^\d{8}$/.test(cinDigits)) {
      this.cinError = 'Le CIN doit contenir exactement 8 chiffres.';
      return;
    }

    const telDigits = this.telephone.trim();
    if (!/^[2549]\d{7}$/.test(telDigits)) {
      this.telephoneError = 'Le téléphone doit contenir 8 chiffres et commencer par 2, 5, 9 ou 4.';
      return;
    }

    const anciennete = Number(this.ancienneteEmploiMois);
    if (!Number.isInteger(anciennete) || anciennete < 0) {
      this.ancienneteError = "L'ancienneté doit être un entier en mois (>= 0).";
      return;
    }

    const dateN = new Date(this.dateNaissance);
    if (!this.dateNaissance || Number.isNaN(dateN.getTime()) || dateN > new Date()) {
      this.dateNaissanceError = 'Date de naissance invalide.';
      return;
    }

    const ne = Number(this.nombreEnfants);
    if (this.shouldShowNombreEnfants && (!Number.isInteger(ne) || ne < 0)) {
      this.formError = 'Le nombre d’enfants doit être un entier >= 0.';
      return;
    }

    // On émet toutes les données au parent
    this.nextStep.emit({
      nom: this.nom.trim(),
      prenom: this.prenom.trim(),
      email: this.email.trim(),
      telephone: this.telephone.trim(),
      cin: this.cin.trim(),
      adresse: this.adresse.trim(),
      sexe: this.sexe.trim(),
      profession: this.profession.trim(),
      employeur: this.employeur.trim(),
      typeContrat: this.typeContrat,
      dateNaissance: this.dateNaissance,
      ancienneteEmploiMois: anciennete,
      situationFamiliale: this.situationFamiliale as SituationFamilialeCode,
      nombreEnfants: this.shouldShowNombreEnfants ? ne : 0,
    });

    // Marquer comme complété
    this.status = 'completed';
  }
}