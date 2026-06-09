import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { NgIf } from '@angular/common';
import { AuthService } from '../services/auth-service.service';

/** Fichier attendu : src/assets/images/login-logo.png (ou .jpg / .webp) */
const LOGIN_LOGO_CANDIDATES = [
  '/assets/images/login-logo.png',
  '/assets/images/login-logo.jpg',
  '/assets/images/login-logo.webp',
  '/assets/images/login-logo.svg',
];

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, NgIf],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {
  email = '';
  password = '';
  showPassword = false;
  errorMessage = '';
  loading = false;
  loginLogoSrc = LOGIN_LOGO_CANDIDATES[0];
  loginLogoVisible = true;

  constructor(private router: Router, private authService: AuthService) {}

  ngOnInit(): void {
    void this.resolveLoginLogo();
  }

  togglePasswordVisibility(): void {
    this.showPassword = !this.showPassword;
  }

  onLoginLogoError(): void {
    const idx = LOGIN_LOGO_CANDIDATES.indexOf(this.loginLogoSrc);
    const next = LOGIN_LOGO_CANDIDATES[idx + 1];
    if (next) {
      this.loginLogoSrc = next;
      return;
    }
    this.loginLogoVisible = false;
  }

  private async resolveLoginLogo(): Promise<void> {
    for (const src of LOGIN_LOGO_CANDIDATES) {
      if (await this.imageExists(src)) {
        this.loginLogoSrc = src;
        this.loginLogoVisible = true;
        return;
      }
    }
    this.loginLogoVisible = false;
  }

  private imageExists(src: string): Promise<boolean> {
    return new Promise((resolve) => {
      const img = new Image();
      img.onload = () => resolve(true);
      img.onerror = () => resolve(false);
      img.src = src;
    });
  }

  login() {
  this.errorMessage = '';

  const email = this.email.trim().toLowerCase();

  // Vérification email
  const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}$/i;
  if(!emailRegex.test(email)) {
    this.errorMessage = 'Veuillez entrer un email valide';
    return;
  }

  this.loading = true;
  this.authService.login(email, this.password).subscribe({
    next: (res) => {
      this.loading = false;
      if (res.otpRequired) {
        this.router.navigate(['/verify-otp']); // ← redirection OTP
      } else {
        this.errorMessage = 'Réponse inattendue du serveur';
      }
    },
    error: (err) => {
      this.loading = false;
      this.errorMessage = err?.error?.error ?? 'Erreur serveur';
    }
  });
}


  /** Même logique que côté sécurité : comparaison sur rôle métier en majuscules. */
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