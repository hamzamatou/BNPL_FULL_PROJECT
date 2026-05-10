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
  styleUrls: ['./admin.component.css'],
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

  loadUsers() {
    this.userService.getUsers().subscribe({
      next: (data) => (this.users = data.filter((u) => u.role !== 'ADMIN')),
      error: (err) => console.error('Erreur chargement utilisateurs:', err),
    });
  }

  deleteUser(user: User) {
    if (!user.id) return;

    this.userService.deleteUser(user.id).subscribe({
      next: () => {
        this.loadUsers();
      },
      error: (err) => {
        console.error('Erreur suppression:', err);
      },
    });
  }

  toggleBlockUser(user: User) {
    if (!user.id) return;

    this.userService.toggleBlockUser(user.id).subscribe({
      next: (updated) => {
        user.status = updated.status;
      },
      error: (err) => {
        console.error('Erreur toggle:', err);
      },
    });
  }

  openAddUser() {
    this.showAddUser = true;
  }

  closeAddUser() {
    this.showAddUser = false;
  }

  onUserAdded() {
    this.loadUsers();
    this.closeAddUser();
  }

  viewUser(user: User) {
    if (!user.id) return;
    this.router.navigate(['/admin/user', user.id]);
  }
}
