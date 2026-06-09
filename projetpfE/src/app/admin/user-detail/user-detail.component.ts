import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { UserService } from '../../services/user.service';
import { FormsModule } from '@angular/forms';
import { AdminIconComponent } from '../../shared/admin-icon/admin-icon.component';

@Component({
  selector: 'app-user-detail',
  standalone: true,
  imports: [FormsModule, AdminIconComponent],
  templateUrl: './user-detail.component.html',
  styleUrls: ['./user-detail.component.css'],
})
export class UserDetailComponent implements OnInit {

  user: any;
  today = new Date().toLocaleDateString('fr-FR');
  currentYear = new Date().getFullYear();

  editMode = false;
  editForm: any = {};
  toast: { type: 'success' | 'error', message: string } | null = null;

  constructor(
    private route: ActivatedRoute,
    private userService: UserService,
    private router: Router
  ) {}

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.userService.getUsers().subscribe(users => {
      this.user = users.find(u => u.id === id);
    });
  }

  toggleEdit() {
    // Copie profonde des données actuelles dans le formulaire
    this.editForm = { ...this.user };
    this.editMode = true;
  }

  cancelEdit() {
    this.editMode = false;
    this.editForm = {};
  }

  saveEdit() {
    this.userService.updateUser(this.editForm).subscribe({
      next: (updated) => {
        this.user = { ...this.user, ...this.editForm };
        this.editMode = false;
        this.showToast('success', 'Utilisateur mis à jour avec succès.');
      },
      error: () => {
        this.showToast('error', 'Erreur lors de la mise à jour.');
      }
    });
  }

  showToast(type: 'success' | 'error', message: string) {
    this.toast = { type, message };
    setTimeout(() => this.toast = null, 3500);
  }

  goBack() {
    void this.router.navigate(['/admin/utilisateurs']);
  }

  formatRef(id: number): string {
    return String(id).padStart(6, '0');
  }
}