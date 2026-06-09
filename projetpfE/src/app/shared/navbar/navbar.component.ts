import { Component, OnInit } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../services/auth-service.service';

/** Votre logo PNG/JPG — src/assets/ ou src/assets/images/ (servi sous /assets/…) */
const LOGO_CANDIDATES = [
  '/assets/uib-logo.png',
  '/assets/images/uib-logo.png',
  '/assets/uib-logo.jpg',
  '/assets/images/uib-logo.jpg',
  '/assets/images/logo.png',
];

export interface NavbarLink {
  label: string;
  route: string;
  icon: 'dashboard' | 'inbox' | 'folder' | 'history' | 'users' | 'audit' | 'plus';
  exact?: boolean;
}

@Component({
  selector: 'app-navbar',
  imports: [RouterLink, RouterLinkActive],
  standalone: true,
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.css',
})
export class NavbarComponent implements OnInit {
  logoSrc = LOGO_CANDIDATES[0];

  roleLabel = '';
  userName = '';
  userInitial = '?';
  mobileMenuOpen = false;

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router
  ) {
    this.initFromToken();
  }

  ngOnInit(): void {
    void this.resolveLogo();
  }

  get navLinks(): NavbarLink[] {
    switch (this.roleLabel) {
      case 'Commerçant':
        return [
          { label: 'Mes demandes', route: '/mes-demandes', icon: 'folder' },
          { label: 'Créer une demande', route: '/commercant', icon: 'plus' },
        ];
      case 'Admin':
        return [];
      case 'Banque':
        return [
          { label: 'Tableau de bord', route: '/banque/pilotage', icon: 'dashboard' },
          { label: 'Demandes', route: '/banque/demandes', icon: 'inbox' },
          { label: 'Prise en charge', route: '/banque/affectees', icon: 'folder' },
          { label: 'Historique demandes', route: '/banque/mes-demandes', icon: 'history' },
        ];
      default:
        return [];
    }
  }

  get hasNav(): boolean {
    return this.navLinks.length > 0;
  }

  toggleMobileMenu(): void {
    this.mobileMenuOpen = !this.mobileMenuOpen;
  }

  closeMobileMenu(): void {
    this.mobileMenuOpen = false;
  }

  onLogout(event: Event): void {
    event.preventDefault();
    this.closeMobileMenu();
    this.authService.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login']),
    });
  }

  homeLink(): string {
    if (this.roleLabel === 'Commerçant') return '/mes-demandes';
    if (this.roleLabel === 'Admin') return '/admin/dashboard';
    if (this.roleLabel === 'Banque') return '/banque/pilotage';
    return '/login';
  }

  private async resolveLogo(): Promise<void> {
    for (const src of LOGO_CANDIDATES) {
      if (await this.imageExists(src)) {
        this.logoSrc = src;
        return;
      }
    }
  }

  private imageExists(src: string): Promise<boolean> {
    return new Promise((resolve) => {
      const img = new Image();
      img.onload = () => resolve(true);
      img.onerror = () => resolve(false);
      img.src = src;
    });
  }

  private initFromToken(): void {
    const role = this.authService.getRole();
    this.roleLabel = this.mapRole(role);

    const token = this.authService.getToken();
    if (!token) {
      this.userName = '';
      this.userInitial = '?';
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
      this.userInitial = this.userName ? this.userName.charAt(0).toUpperCase() : '?';
    } catch {
      this.userName = '';
      this.userInitial = '?';
    }
  }

  private mapRole(role: string | null): string {
    if (!role) return '';
    const r = role.toUpperCase();
    if (r === 'ADMIN') return 'Admin';
    if (r === 'COMMERCANT') return 'Commerçant';
    if (r === 'ANALYSTE_BANCAIRE' || r === 'BANQUE') return 'Banque';
    if (r === 'CLIENT') return 'Client';
    return role;
  }
}
