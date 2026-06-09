import { Component, OnDestroy, OnInit } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter, Subscription } from 'rxjs';
import { AuthService } from '../../services/auth-service.service';
import { resolveUibLogo, UIB_LOGO_CANDIDATES } from '../../shared/uib-brand';

export interface AdminNavChild {
  label: string;
  route: string;
  exact?: boolean;
}

export interface AdminNavItem {
  id: string;
  label: string;
  icon: string;
  route?: string;
  exact?: boolean;
  children?: AdminNavChild[];
}

@Component({
  selector: 'app-admin-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './admin-sidebar.component.html',
  styleUrl: './admin-sidebar.component.css',
})
export class AdminSidebarComponent implements OnInit, OnDestroy {
  logoSrc = UIB_LOGO_CANDIDATES[0];
  logoFailed = false;
  private logoCandidateIdx = 0;

  userName = 'Admin';
  userInitial = 'A';

  private navSub?: Subscription;
  private openState: Record<string, boolean> = {
    demandes: true,
    users: true,
  };

  readonly nav: AdminNavItem[] = [
    {
      id: 'dashboard',
      label: 'Tableau de bord',
      icon: 'dashboard',
      route: '/admin/dashboard',
    },
    {
      id: 'demandes',
      label: 'Demandes',
      icon: 'inbox',
      children: [
        { label: 'En cours', route: '/admin/demandes/en-cours' },
        { label: 'Archivées', route: '/admin/demandes/archivees' },
      ],
    },
    {
      id: 'users',
      label: 'Utilisateurs',
      icon: 'users',
      children: [
        { label: 'Créer', route: '/admin/utilisateurs/creer' },
        { label: 'Liste', route: '/admin/utilisateurs', exact: true },
        { label: 'Journal accès', route: '/admin/utilisateurs/acces' },
      ],
    },
  ];

  constructor(
    private readonly authService: AuthService,
    private readonly router: Router
  ) {
    this.initProfile();
    this.syncOpenFromUrl(this.router.url);
  }

  ngOnInit(): void {
    void resolveUibLogo().then((src) => (this.logoSrc = src));
    this.navSub = this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.syncOpenFromUrl(e.urlAfterRedirects));
  }

  ngOnDestroy(): void {
    this.navSub?.unsubscribe();
  }

  toggleGroup(id: string): void {
    this.openState[id] = !this.openState[id];
  }

  isGroupOpen(id: string): boolean {
    return this.openState[id] ?? false;
  }

  isNavActive(item: AdminNavItem): boolean {
    if (item.children?.length) return this.isGroupActive(item);
    if (!item.route) return false;
    const path = this.router.url.split('?')[0];
    return item.exact
      ? path === item.route
      : path === item.route || path.startsWith(item.route + '/');
  }

  isGroupActive(item: AdminNavItem): boolean {
    if (!item.children?.length) return false;
    const path = this.router.url.split('?')[0];
    if (item.id === 'demandes') {
      return path.startsWith('/admin/demandes');
    }
    return item.children.some(
      (c) => path === c.route || path.startsWith(c.route + '/')
    );
  }

  private syncOpenFromUrl(url: string): void {
    const path = url.split('?')[0];
    if (path.includes('/admin/demandes')) {
      this.openState['demandes'] = true;
    }
    if (path.includes('/admin/utilisateurs')) {
      this.openState['users'] = true;
    }
  }

  onLogoError(): void {
    this.logoCandidateIdx += 1;
    if (this.logoCandidateIdx < UIB_LOGO_CANDIDATES.length) {
      this.logoSrc = UIB_LOGO_CANDIDATES[this.logoCandidateIdx];
    } else {
      this.logoFailed = true;
    }
  }

  onLogout(): void {
    this.authService.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login']),
    });
  }

  private initProfile(): void {
    const token = this.authService.getToken();
    if (!token) return;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const email = payload?.sub;
      if (typeof email === 'string' && email.includes('@')) {
        const local = email.split('@')[0];
        this.userName = local.charAt(0).toUpperCase() + local.slice(1);
      }
      this.userInitial = this.userName.charAt(0).toUpperCase() || 'A';
    } catch {
      /* ignore */
    }
  }
}
