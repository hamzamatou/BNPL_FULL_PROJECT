import { Component } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AddUserComponent } from '../../../add-user/add-user.component';

import { AdminIconComponent } from '../../../shared/admin-icon/admin-icon.component';

@Component({
  selector: 'app-admin-utilisateurs-creer',
  standalone: true,
  imports: [AddUserComponent, RouterLink, AdminIconComponent],
  templateUrl: './admin-utilisateurs-creer.component.html',
  styleUrls: ['./admin-utilisateurs-creer.component.css'],
  host: { class: 'page-host' },
})
export class AdminUtilisateursCreerComponent {
  constructor(private readonly router: Router) {}

  onCreated(): void {
    void this.router.navigate(['/admin/utilisateurs']);
  }

  onClose(): void {
    void this.router.navigate(['/admin/utilisateurs']);
  }
}
