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

  canActivate(route: ActivatedRouteSnapshot, _state: RouterStateSnapshot): boolean | UrlTree {
    const requiredRole = (route.data?.['role'] as string | undefined)?.toUpperCase();
    if (!requiredRole) return true;

    const role = this.normalizeRole(this.authService.getRole());
    if (!role) return this.router.parseUrl('/login');

    if (role === requiredRole) return true;
    if (requiredRole === 'BANQUE' && (role === 'ANALYSTE_BANCAIRE' || role === 'BANQUE')) return true;
    return this.router.parseUrl('/login');
  }

  private normalizeRole(raw: string | null | undefined): string | null {
    if (!raw) return null;
    let r = raw.trim().toUpperCase();
    if (r.startsWith('ROLE_')) r = r.slice('ROLE_'.length);
    return r;
  }

  canActivateChild(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | UrlTree {
    return this.canActivate(route, state);
  }
}

