import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { NgIf } from '@angular/common';
import { AuthService } from '../services/auth-service.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, NgIf],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css'],
})
export class LoginComponent {
  email = '';
  password = '';
  errorMessage = '';
  loading = false;
  logoPath: string = 'assets/image.png';

  constructor(
    private router: Router,
    private authService: AuthService
  ) {}

  login() {
    this.errorMessage = '';

    const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,4}$/;
    if (!emailRegex.test(this.email)) {
      this.errorMessage = 'Veuillez entrer un email valide';
      return;
    }

    this.loading = true;
    this.authService.login(this.email, this.password).subscribe({
      next: (res) => {
        this.loading = false;

        if (!res.token) {
          this.errorMessage = 'Email ou mot de passe incorrect';
          return;
        }

        const role =
          this.normalizeRole(res.role) ?? this.normalizeRole(this.authService.getRole());

        if (res.status === 'CREATED') {
          this.router.navigate(['/activate-account', res.token]);
          return;
        }

        if (role === 'ADMIN') this.router.navigate(['/admin']);
        else if (role === 'COMMERCANT') this.router.navigate(['/commercant']);
        else if (role === 'BANQUE' || role === 'ANALYSTE_BANCAIRE') this.router.navigate(['/banque']);
        else if (role === 'CLIENT')
          this.errorMessage = 'Les comptes client ne se connectent pas ici (parcours BNPL).';
        else this.errorMessage = `Rôle inconnu (${String(res.role ?? 'vide')})`;
      },
      error: (err) => {
        this.loading = false;
        const backendError =
          err?.error?.error ??
          err?.error?.message ??
          (typeof err?.error === 'string' ? err.error : undefined);

        this.errorMessage = backendError || 'Erreur serveur (400)';
      },
    });
  }

  private normalizeRole(raw: unknown): string | null {
    if (raw == null) return null;
    const s = String(raw).trim();
    if (!s) return null;
    let up = s.toUpperCase();
    if (up.startsWith('ROLE_')) {
      up = up.slice('ROLE_'.length);
    }
    return up;
  }
}
