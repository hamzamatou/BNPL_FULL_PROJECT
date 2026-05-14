import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../services/auth-service.service';

@Component({
  selector: 'app-navbar',
  imports: [RouterLink, RouterLinkActive],
  standalone: true,
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.css'
})
export class NavbarComponent {
  roleLabel = '';
  userName = '';

  constructor(private readonly authService: AuthService) {
    this.initFromToken();
  }

  private initFromToken(): void {
    const role = this.authService.getRole();
    this.roleLabel = this.mapRole(role);

    const token = this.authService.getToken();
    if (!token) {
      this.userName = '';
      return;
    }

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const email = payload?.sub;
      if (typeof email === 'string' && email.includes('@')) {
        this.userName = email.split('@')[0];
      } else if (typeof email === 'string') {
        this.userName = email;
      } else {
        this.userName = '';
      }
    } catch {
      this.userName = '';
    }
  }

  private mapRole(role: string | null): string {
    if (!role) return '';
    const r = role.toUpperCase();
    if (r === 'ADMIN') return 'Admin';
    if (r === 'COMMERCANT') return 'Commerçant';
    if (r === 'BANQUE') return 'Banque';
    if (r === 'CLIENT') return 'Client';
    return role;
  }
}
