import { Component } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { NavbarComponent } from "./shared/navbar/navbar.component";
import { filter } from 'rxjs';
import { NgClass, NgIf } from '@angular/common';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, NavbarComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'frontend';
  showShell = true;

  constructor(private readonly router: Router) {
    this.updateShellVisibility(this.router.url);
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe((event) => this.updateShellVisibility((event as NavigationEnd).urlAfterRedirects));
  }

private updateShellVisibility(url: string): void {
  const path = url.split('?')[0];
  const noShellRoutes = [
    '/login',
    '/verify-otp',
    '/activate-account',
    '/action-client'
  ];
  this.showShell = !noShellRoutes.some(r => path.startsWith(r));
}
  

}
