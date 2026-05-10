import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { UserService } from '../services/user.service';
import { FormsModule } from '@angular/forms';
import { NgClass, NgFor, NgIf } from '@angular/common';

@Component({
  selector: 'app-add-user',
  standalone: true,
  imports: [FormsModule, NgIf, NgFor,NgClass],
  templateUrl: './add-user.component.html',
  styleUrls: ['./add-user.component.css']
})
export class AddUserComponent implements OnInit {

  @Output() close = new EventEmitter<void>();
  @Output() refresh = new EventEmitter<void>();

  selectedRole: string = '';
  commercant = this.getEmptyCommercant();
  analyste = this.getEmptyAnalyste();
  banques: any[] = [];
  showAddBank = false;
  newBank = this.getEmptyBank();

  // ── Notification ──
  toast: { message: string; type: 'success' | 'error' } | null = null;

  constructor(private userService: UserService) {}

  ngOnInit(): void {
    this.loadBanques();
  }

  // ================= FACTORIES =================

  getEmptyCommercant() {
    return { nomMagasin: '', ice: '', telephone: '', adresse: '', email: '', password: '' };
  }

  getEmptyAnalyste() {
    return { nom: '', prenom: '', poste: '', email: '', telephone: '', password: '', banqueId: '' };
  }

  getEmptyBank() {
    return { nomBanque: '', codeBanque: '', email: '', telephone: '', adresse: '' };
  }

  // ================= TOAST =================

  showToast(message: string, type: 'success' | 'error') {
    this.toast = { message, type };
    setTimeout(() => this.toast = null, 4000);
  }

  // ================= ROLE CHANGE =================

  onRoleChange() {
    this.commercant = this.getEmptyCommercant();
    this.analyste = this.getEmptyAnalyste();
    this.toast = null;
  }

  // ================= LOAD =================

  loadBanques() {
    this.userService.getBanques().subscribe({
      next: (data) => this.banques = data,
      error: () => this.showToast("Impossible de charger les banques.", 'error')
    });
  }

  // ================= VALIDATION =================

  validateAnalyste(): string | null {
    if (!this.analyste.nom.trim())       return "Le nom est obligatoire.";
    if (!this.analyste.prenom.trim())    return "Le prénom est obligatoire.";
    if (!this.analyste.poste.trim())     return "Le poste est obligatoire.";
    if (!this.analyste.email.trim())     return "L'email est obligatoire.";
    if (!this.analyste.telephone.trim()) return "Le téléphone est obligatoire.";
    if (!this.analyste.password.trim())  return "Le mot de passe est obligatoire.";
    if (!this.analyste.banqueId)         return "Veuillez sélectionner une banque.";
    return null;
  }

  validateCommercant(): string | null {
    if (!this.commercant.nomMagasin.trim()) return "Le nom du magasin est obligatoire.";
    if (!this.commercant.ice.trim())        return "L'ICE est obligatoire.";
    if (!this.commercant.email.trim())      return "L'email est obligatoire.";
    if (!this.commercant.telephone.trim())  return "Le téléphone est obligatoire.";
    if (!this.commercant.password.trim())   return "Le mot de passe est obligatoire.";
    return null;
  }

  // ================= SUBMIT =================

  submit() {
    if (!this.selectedRole) {
      this.showToast("Veuillez choisir un rôle.", 'error');
      return;
    }

    if (this.selectedRole === 'ANALYSTE') {
      const error = this.validateAnalyste();
      if (error) { this.showToast(error, 'error'); return; }

      const payload = {
        nom:       this.analyste.nom,
        prenom:    this.analyste.prenom,
        poste:     this.analyste.poste,
        email:     this.analyste.email,
        telephone: this.analyste.telephone,
        password:  this.analyste.password,
        banqueId:  this.analyste.banqueId
      };

      this.userService.createAnalyste(payload).subscribe({
        next: () => this.success("Analyste bancaire créé avec succès."),
        error: (err) => this.showToast(
          err?.error?.error || "Erreur lors de la création de l'analyste.", 'error'
        )
      });
      return;
    }

    if (this.selectedRole === 'COMMERCANT') {
      const error = this.validateCommercant();
      if (error) { this.showToast(error, 'error'); return; }

      const payload = { ...this.commercant, role: "COMMERCANT" };

      this.userService.addUser(payload).subscribe({
        next: () => this.success("Commerçant créé avec succès."),
        error: (err) => this.showToast(
          err?.error?.error || "Erreur lors de la création du commerçant.", 'error'
        )
      });
      return;
    }
  }

  // ================= SUCCESS =================

  success(msg: string) {
    this.showToast(msg, 'success');
    setTimeout(() => {
      this.refresh.emit();
      this.close.emit();
    }, 1500);
  }

  // ================= BANK =================

  toggleAddBank() {
    this.showAddBank = !this.showAddBank;
  }

  addBank() {
    if (!this.newBank.nomBanque.trim() || !this.newBank.codeBanque.trim()) {
      this.showToast("Nom et code banque sont obligatoires.", 'error');
      return;
    }

    this.userService.createBanque(this.newBank).subscribe({
      next: (res: any) => {
        this.banques.push(res);
        this.analyste.banqueId = res.id;
        this.showAddBank = false;
        this.newBank = this.getEmptyBank();
        this.showToast("Banque ajoutée avec succès.", 'success');
      },
      error: (err) => this.showToast(
        err?.error?.error || "Erreur lors de l'ajout de la banque.", 'error'
      )
    });
  }
}