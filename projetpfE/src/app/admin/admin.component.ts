import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgClass, NgFor, NgIf } from '@angular/common';
import { UserService, User } from '../services/user.service';
import { AddUserComponent } from '../add-user/add-user.component';
import { Router } from '@angular/router';
@Component({
  selector: 'app-admin-portal',
  standalone: true,
  imports: [FormsModule, NgIf, NgFor, NgClass, AddUserComponent],
  templateUrl: './admin.component.html',
  styleUrls: ['./admin.component.css']
})
export class AdminPortalComponent implements OnInit {
  users: User[] = [];
  activeTab = 'overview';
  showAddUser = false;

 constructor(
    private userService: UserService,
    private router: Router
  ) {}
  ngOnInit() {
    this.loadUsers();
  }

  setTab(tab: string) {
    this.activeTab = tab;
  }

  // 🔹 Charger tous les utilisateurs
  loadUsers() {
  this.userService.getUsers().subscribe({
    next: data => this.users = data.filter(u => u.role !== 'ADMIN'),
    error: err => console.error('Erreur chargement utilisateurs:', err)
  });
}
deleteUser(user: User) {
  console.log("USER:", user); // debug

  if (!user.id) return; // ✅ correction

  this.userService.deleteUser(user.id).subscribe({
    next: () => {
      console.log("✅ Utilisateur supprimé");
      this.loadUsers();
    },
    error: err => {
      console.error("❌ Erreur suppression:", err);
    }
  });
}

// 🔹 Bloquer / débloquer
toggleBlockUser(user: User) {
  console.log("USER:", user); // debug

  if (!user.id) return; // ✅ correction

  this.userService.toggleBlockUser(user.id).subscribe({
    next: updated => {
      console.log("✅ Statut modifié");
      user.status = updated.status;
    },
    error: err => {
      console.error("❌ Erreur toggle:", err);
    }
  });
}

  // 🔹 Afficher le formulaire AddUser
  openAddUser() {
    this.showAddUser = true;
  }

  // 🔹 Masquer le formulaire AddUser
  closeAddUser() {
    this.showAddUser = false;
  }

  // 🔹 Rafraîchir la liste après ajout d'utilisateur
  onUserAdded() {
    this.loadUsers();
    this.closeAddUser();
  }
  viewUser(user: any) {
  this.router.navigate(['/admin/user', user.id]);
}
}