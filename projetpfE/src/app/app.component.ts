import { Component } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { NavbarComponent } from './shared/navbar/navbar.component';
import { filter } from 'rxjs';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, NavbarComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent {
  showShell = true;
  isAdminArea = false;

  constructor(private readonly router: Router) {
    this.updateLayout(this.router.url);
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe((event) => {
        const nav = event as NavigationEnd;
        const wasShell = this.showShell;
        this.updateLayout(nav.urlAfterRedirects);
        if (!wasShell && this.showShell && !this.isAdminArea) {
          void this.router.navigateByUrl(nav.urlAfterRedirects, { replaceUrl: true });
        }
      });
  }

  private updateLayout(url: string): void {
    const path = url.split('?')[0];
    const noShellRoutes = ['/login', '/verify-otp', '/activate-account', '/action-client'];
    this.showShell = !noShellRoutes.some((r) => path.startsWith(r));
    this.isAdminArea = path.startsWith('/admin');
  }
}
