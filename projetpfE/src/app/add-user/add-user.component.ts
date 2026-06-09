import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { UserService } from '../services/user.service';
import { FormsModule } from '@angular/forms';
import { AdminIconComponent } from '../shared/admin-icon/admin-icon.component';

@Component({
  selector: 'app-add-user',
  standalone: true,
  imports: [FormsModule, AdminIconComponent],
  templateUrl: './add-user.component.html',
  styleUrls: ['./add-user.component.css']
})
export class AddUserComponent implements OnInit {

  /** `page` = page dédiée (header externe) ; `inline` = panneau dans la liste */
  @Input() mode: 'page' | 'inline' = 'page';

  @Output() close = new EventEmitter<void>();
  @Output() refresh = new EventEmitter<void>();

  selectedRole = '';
  commercant = this.getEmptyCommercant();
  analyste = this.getEmptyAnalyste();
  banques: any[] = [];
  showAddBank = false;
  newBank = this.getEmptyBank();

  formError: string | null = null;
  toast: { message: string; type: 'success' | 'error' } | null = null;
  submitting = false;
  addingBank = false;
  loadingBanques = false;

  constructor(private userService: UserService) {}

  ngOnInit(): void {
    this.loadBanques();
  }

  getEmptyCommercant() {
    return { nomMagasin: '', ice: '', telephone: '', adresse: '', email: '', password: '' };
  }

  getEmptyAnalyste() {
    return { nom: '', prenom: '', poste: '', email: '', telephone: '', password: '', banqueId: '' };
  }

  getEmptyBank() {
    return { nomBanque: '', codeBanque: '', email: '', telephone: '', adresse: '' };
  }

  showToast(message: string, type: 'success' | 'error') {
    this.toast = { message, type };
    setTimeout(() => this.toast = null, 4000);
  }

  selectRole(role: string) {
    if (this.selectedRole === role) return;
    this.selectedRole = role;
    this.commercant = this.getEmptyCommercant();
    this.analyste = this.getEmptyAnalyste();
    this.formError = null;
    this.toast = null;
    this.showAddBank = false;
  }

  loadBanques() {
    this.loadingBanques = true;
    this.userService.getBanques().subscribe({
      next: (data) => {
        this.banques = data;
        this.loadingBanques = false;
      },
      error: () => {
        this.loadingBanques = false;
        this.showToast('Impossible de charger les banques.', 'error');
      }
    });
  }

  validateAnalyste(): string | null {
    if (!this.analyste.nom.trim())       return 'Le nom est obligatoire.';
    if (!this.analyste.prenom.trim())    return 'Le prénom est obligatoire.';
    if (!this.analyste.poste.trim())     return 'Le poste est obligatoire.';
    if (!this.analyste.email.trim())     return "L'e-mail est obligatoire.";
    if (!this.analyste.telephone.trim()) return 'Le téléphone est obligatoire.';
    if (!this.analyste.password.trim())  return 'Le mot de passe est obligatoire.';
    if (!this.analyste.banqueId)         return 'Veuillez sélectionner une banque.';
    return null;
  }

  validateCommercant(): string | null {
    if (!this.commercant.nomMagasin.trim()) return 'Le nom du magasin est obligatoire.';
    if (!this.commercant.ice.trim())        return "L'ICE est obligatoire.";
    if (!this.commercant.email.trim())      return "L'e-mail est obligatoire.";
    if (!this.commercant.telephone.trim())  return 'Le téléphone est obligatoire.';
    if (!this.commercant.password.trim())   return 'Le mot de passe est obligatoire.';
    return null;
  }

  submit() {
    this.formError = null;

    if (!this.selectedRole) {
      this.formError = 'Veuillez choisir un type de compte.';
      return;
    }

    if (this.selectedRole === 'ANALYSTE') {
      const error = this.validateAnalyste();
      if (error) { this.formError = error; return; }

      this.submitting = true;
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
        next: () => {
          this.submitting = false;
          this.success('Analyste bancaire créé avec succès.');
        },
        error: (err) => {
          this.submitting = false;
          this.formError = err?.error?.error || "Erreur lors de la création de l'analyste.";
        }
      });
      return;
    }

    if (this.selectedRole === 'COMMERCANT') {
      const error = this.validateCommercant();
      if (error) { this.formError = error; return; }

      this.submitting = true;
      const payload = { ...this.commercant, role: 'COMMERCANT' };

      this.userService.addUser(payload).subscribe({
        next: () => {
          this.submitting = false;
          this.success('Commerçant créé avec succès.');
        },
        error: (err) => {
          this.submitting = false;
          this.formError = err?.error?.error || 'Erreur lors de la création du commerçant.';
        }
      });
    }
  }

  success(msg: string) {
    this.showToast(msg, 'success');
    setTimeout(() => {
      this.refresh.emit();
      this.close.emit();
    }, 1200);
  }

  toggleAddBank() {
    this.showAddBank = !this.showAddBank;
    if (!this.showAddBank) {
      this.newBank = this.getEmptyBank();
    }
  }

  addBank() {
    if (!this.newBank.nomBanque.trim() || !this.newBank.codeBanque.trim()) {
      this.formError = 'Nom et code banque sont obligatoires.';
      return;
    }

    this.addingBank = true;
    this.userService.createBanque(this.newBank).subscribe({
      next: (res: any) => {
        this.banques.push(res);
        this.analyste.banqueId = res.id;
        this.showAddBank = false;
        this.newBank = this.getEmptyBank();
        this.addingBank = false;
        this.formError = null;
        this.showToast('Banque ajoutée avec succès.', 'success');
      },
      error: (err) => {
        this.addingBank = false;
        this.formError = err?.error?.error || "Erreur lors de l'ajout de la banque.";
      }
    });
  }
}
