import { Component, OnInit } from '@angular/core';

import { FormsModule } from '@angular/forms';

import { CommonModule } from '@angular/common';

import { Router, RouterLink } from '@angular/router';
import { UserService, User } from '../services/user.service';
import { AdminIconComponent } from '../shared/admin-icon/admin-icon.component';

@Component({
  selector: 'app-admin-portal',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AdminIconComponent],

  templateUrl: './admin.component.html',

  styleUrls: ['./admin.component.css'],

  host: { class: 'page-host' },

})

export class AdminPortalComponent implements OnInit {

  users: User[] = [];



  constructor(

    private userService: UserService,

    private router: Router

  ) {}



  ngOnInit() {

    this.loadUsers();

  }



  loadUsers() {

    this.userService.getUsers().subscribe({

      next: data => this.users = data.filter(u => u.role !== 'ADMIN'),

      error: err => console.error('Erreur chargement utilisateurs:', err)

    });

  }



  deleteUser(user: User) {

    if (!user.id) return;



    this.userService.deleteUser(user.id).subscribe({

      next: () => this.loadUsers(),

      error: err => console.error('Erreur suppression:', err)

    });

  }



  toggleBlockUser(user: User) {

    if (!user.id) return;



    this.userService.toggleBlockUser(user.id).subscribe({

      next: updated => { user.status = updated.status; },

      error: err => console.error('Erreur toggle:', err)

    });

  }



  viewUser(user: User) {

    void this.router.navigate(['/admin/user', user.id]);

  }

}

