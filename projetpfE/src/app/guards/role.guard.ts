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
    const requiredRoles = route.data?.['roles'] as string[] | undefined;
    const requiredRole = (route.data?.['role'] as string | undefined)?.toUpperCase();

    const role = this.authService.getRole()?.toUpperCase();
    if (!role) return this.router.parseUrl('/login');

    if (requiredRoles?.length) {
      const allowed = requiredRoles.map((r) => r.toUpperCase());
      if (allowed.includes(role)) return true;
      return this.router.parseUrl('/login');
    }

    if (!requiredRole) return true;
    if (role === requiredRole) return true;
    return this.router.parseUrl('/login');
  }

  canActivateChild(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | UrlTree {
    return this.canActivate(route, state);
  }
}

