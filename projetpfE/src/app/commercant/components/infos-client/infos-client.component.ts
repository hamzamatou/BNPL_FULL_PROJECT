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

  nomError = '';
  prenomError = '';
  emailError = '';
  telephoneError = '';
  cinError = '';
  adresseError = '';
  sexeError = '';
  professionError = '';
  employeurError = '';
  typeContratError = '';
  situationFamilialeError = '';
  nombreEnfantsError = '';
  ancienneteError = '';
  dateNaissanceError = '';

  private static readonly AGE_MINIMUM_ANNEES = 20;

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
    const situation = str('situationFamiliale');
    if (situation === 'CELIBATAIRE' || situation === 'MARIE' || situation === 'DIVORCE') {
      this.situationFamiliale = situation;
    }
    const anc = num('ancienneteEmploiMois');
    if (anc !== undefined) this.ancienneteEmploiMois = anc;
    const ne = num('nombreEnfants');
    if (ne !== undefined) this.nombreEnfants = ne;
  }

  /** Date max. sélectionnable : aujourd’hui moins 20 ans (âge minimum). */
  get dateNaissanceMax(): string {
    const d = this.dateLimiteNaissanceMax();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
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
      DIVORCE: 'sit-badge sit-badge--divorce',
    };
    return map[c] ?? 'sit-badge';
  }

  private clearErrors(): void {
    this.nomError = '';
    this.prenomError = '';
    this.emailError = '';
    this.telephoneError = '';
    this.cinError = '';
    this.adresseError = '';
    this.sexeError = '';
    this.professionError = '';
    this.employeurError = '';
    this.typeContratError = '';
    this.situationFamilialeError = '';
    this.nombreEnfantsError = '';
    this.ancienneteError = '';
    this.dateNaissanceError = '';
  }

  private dateLimiteNaissanceMax(): Date {
    const today = new Date();
    return new Date(
      today.getFullYear() - InfosClientComponent.AGE_MINIMUM_ANNEES,
      today.getMonth(),
      today.getDate(),
    );
  }

  private validerDateNaissance(): boolean {
    if (!this.dateNaissance) {
      this.dateNaissanceError = 'Champ obligatoire.';
      return false;
    }
    const dateN = new Date(this.dateNaissance);
    if (Number.isNaN(dateN.getTime())) {
      this.dateNaissanceError = 'Date de naissance invalide.';
      return false;
    }
    const today = new Date();
    today.setHours(23, 59, 59, 999);
    if (dateN > today) {
      this.dateNaissanceError = 'La date de naissance ne peut pas être dans le futur.';
      return false;
    }
    if (dateN > this.dateLimiteNaissanceMax()) {
      this.dateNaissanceError = `Le client doit avoir au moins ${InfosClientComponent.AGE_MINIMUM_ANNEES} ans.`;
      return false;
    }
    return true;
  }

  next() {
    this.clearErrors();

    if (!this.nom.trim()) this.nomError = 'Champ obligatoire.';
    if (!this.prenom.trim()) this.prenomError = 'Champ obligatoire.';
    if (!this.email.trim()) {
      this.emailError = 'Champ obligatoire.';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.email.trim())) {
      this.emailError = 'Adresse e-mail invalide.';
    }
    if (!this.telephone.trim()) {
      this.telephoneError = 'Champ obligatoire.';
    } else if (!/^[2549]\d{7}$/.test(this.telephone.trim())) {
      this.telephoneError = '8 chiffres, commençant par 2, 5, 9 ou 4.';
    }
    if (!this.cin.trim()) {
      this.cinError = 'Champ obligatoire.';
    } else if (!/^\d{8}$/.test(this.cin.trim())) {
      this.cinError = 'Le CIN doit contenir exactement 8 chiffres.';
    }
    if (!this.adresse.trim()) this.adresseError = 'Champ obligatoire.';
    if (!this.sexe.trim()) this.sexeError = 'Champ obligatoire.';
    if (!this.profession.trim()) this.professionError = 'Champ obligatoire.';
    if (!this.employeur.trim()) this.employeurError = 'Champ obligatoire.';
    if (!this.typeContrat) this.typeContratError = 'Champ obligatoire.';
    if (!this.situationFamiliale) this.situationFamilialeError = 'Champ obligatoire.';

    this.validerDateNaissance();

    const anciennete = Number(this.ancienneteEmploiMois);
    if (!Number.isInteger(anciennete) || anciennete < 0) {
      this.ancienneteError = "L'ancienneté doit être un entier en mois (≥ 0).";
    }

    const ne = Number(this.nombreEnfants);
    if (this.shouldShowNombreEnfants && (!Number.isInteger(ne) || ne < 0)) {
      this.nombreEnfantsError = 'Nombre d’enfants invalide (entier ≥ 0).';
    }

    const hasError =
      !!this.nomError ||
      !!this.prenomError ||
      !!this.emailError ||
      !!this.telephoneError ||
      !!this.cinError ||
      !!this.adresseError ||
      !!this.sexeError ||
      !!this.professionError ||
      !!this.employeurError ||
      !!this.typeContratError ||
      !!this.situationFamilialeError ||
      !!this.dateNaissanceError ||
      !!this.ancienneteError ||
      !!this.nombreEnfantsError;

    if (hasError) return;

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