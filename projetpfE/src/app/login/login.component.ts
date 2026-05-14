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
  styleUrls: ['./login.component.css']
})
export class LoginComponent {
  email = '';
  password = '';
  errorMessage = '';
  loading = false;
  logoPath: string = 'assets/image.png';

  constructor(private router: Router, private authService: AuthService) {}

  login() {
  this.errorMessage = '';

  // Vérification email
  const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,4}$/;
  if(!emailRegex.test(this.email)) {
    this.errorMessage = 'Veuillez entrer un email valide';
    return;
  }

  this.loading = true;
  this.loading = true;
  this.authService.login(this.email, this.password).subscribe({
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