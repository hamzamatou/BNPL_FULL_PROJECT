import { Injectable } from '@angular/core';
import { CanActivate, CanActivateChild, ActivatedRouteSnapshot, RouterStateSnapshot, Router, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth-service.service';

@Injectable({
  providedIn: 'root',
})
export class RoleGuard implements CanActivate, CanActivateChild {
  constructor(
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | UrlTree {
    const requiredRoles = route.data?.['roles'] as string[] | undefined;
    const requiredRole = (route.data?.['role'] as string | undefined)?.toUpperCase();

    const role = this.authService.getRole()?.toUpperCase();
    if (!role) return this.router.parseUrl('/login');

    if (requiredRoles?.length) {
      const allowed = requiredRoles.map((r) => r.toUpperCase());
      if (allowed.includes(role)) return true;
      return this.homeForRole(role, state.url);
    }

    if (!requiredRole) return true;
    if (role === requiredRole) return true;
    return this.homeForRole(role, state.url);
  }

  canActivateChild(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | UrlTree {
    return this.canActivate(route, state);
  }

  private homeForRole(role: string, attemptedUrl: string): UrlTree {
    if (role === 'ADMIN') return this.router.parseUrl('/admin/dashboard');
    if (role === 'ANALYSTE_BANCAIRE' || role === 'BANQUE') {
      if (attemptedUrl.includes('/admin') || attemptedUrl.includes('/reporting')) {
        return this.router.parseUrl('/banque/pilotage');
      }
      return this.router.parseUrl('/banque/demandes');
    }
    if (role === 'COMMERCANT') return this.router.parseUrl('/mes-demandes');
    return this.router.parseUrl('/login');
  }
}
