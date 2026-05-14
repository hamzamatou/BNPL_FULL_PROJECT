import { Component, OnInit, OnDestroy, ViewChildren, QueryList, ElementRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { NgIf, NgFor } from '@angular/common';
import { AuthService } from '../services/auth-service.service';

@Component({
  selector: 'app-otp-verify',
  standalone: true,
  imports: [FormsModule, NgIf, NgFor, RouterLink],
  templateUrl: './otp-verify.component.html',
  styleUrls: ['./otp-verify.component.css']
})
export class OtpVerifyComponent implements OnInit, OnDestroy {

  // ── État OTP ──
  digits: string[] = ['', '', '', '', '', ''];
  get otpCode(): string { return this.digits.join(''); }

  errorMessage = '';
  loading = false;

  // ── Timer 5 min ──
  private timerSeconds = 300;
  private timerInterval: any;

  get formattedTimer(): string {
    const m = Math.floor(this.timerSeconds / 60);
    const s = this.timerSeconds % 60;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }

  get timerExpired(): boolean { return this.timerSeconds <= 0; }

  // ── Cooldown renvoi ──
  resendCooldown = 0;
  private cooldownInterval: any;

  // ── Références aux inputs ──
  @ViewChildren('digitInput') digitInputs!: QueryList<ElementRef<HTMLInputElement>>;

  get maskedEmail(): string {
    const email = this.authService.getPendingEmail() ?? '';
    const [local, domain] = email.split('@');
    if (!local || !domain) return email;
    return `${local.slice(0, 2)}***@${domain}`;
  }

  constructor(private router: Router, private authService: AuthService) {}

  ngOnInit() {
    if (!this.authService.getPendingEmail()) {
      this.router.navigate(['/login']);
      return;
    }
    this.startTimer();
  }

  // ── Saisie chiffre par chiffre ──
  onDigitInput(event: Event, index: number) {
  const input = event.target as HTMLInputElement;
  const raw = input.value.replace(/\D/g, '');

  // Coller 6 chiffres d'un coup (ex: depuis email)
  if (raw.length > 1) {
    const chars = raw.slice(0, 6).split('');
    chars.forEach((c, i) => { this.digits[i] = c; });
    // Remplir les cases restantes si collage partiel
    for (let i = chars.length; i < 6; i++) { this.digits[i] = ''; }
    this.focusInput(Math.min(chars.length, 5));
    return;
  }

  // Saisie normale : 1 chiffre
  const digit = raw.slice(-1); // garde uniquement le dernier chiffre saisi
  this.digits[index] = digit;
  input.value = digit;         // force la valeur affichée

  // Avancer automatiquement à la case suivante si un chiffre est saisi
  if (digit && index < 5) {
    this.focusInput(index + 1);
  }
}

onKeyDown(event: KeyboardEvent, index: number) {
  if (event.key === 'Backspace') {
    event.preventDefault();

    if (this.digits[index]) {
      // La case courante a un chiffre → l'effacer
      this.digits[index] = '';
      const input = this.digitInputs.toArray()[index];
      if (input) input.nativeElement.value = '';
    } else if (index > 0) {
      // Case courante vide → reculer et effacer la précédente
      this.digits[index - 1] = '';
      const prev = this.digitInputs.toArray()[index - 1];
      if (prev) prev.nativeElement.value = '';
      this.focusInput(index - 1);
    }

  } else if (event.key === 'ArrowLeft' && index > 0) {
    event.preventDefault();
    this.focusInput(index - 1);

  } else if (event.key === 'ArrowRight' && index < 5) {
    event.preventDefault();
    this.focusInput(index + 1);

  } else if (event.key === 'Delete') {
    event.preventDefault();
    this.digits[index] = '';
    const input = this.digitInputs.toArray()[index];
    if (input) input.nativeElement.value = '';
  }
}
  private focusInput(index: number) {
    const arr = this.digitInputs.toArray();
    if (arr[index]) arr[index].nativeElement.focus();
  }
  verifyOtp() {
    if (this.otpCode.length !== 6) {
      this.errorMessage = 'Le code doit contenir 6 chiffres';
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    const email = this.authService.getPendingEmail()!;

    this.authService.verifyOtp(email, this.otpCode).subscribe({
      next: (res) => {
        this.loading = false;
        this.authService.clearPendingEmail();
        this.stopTimer();

        if (res.status === 'CREATED') {
          this.router.navigate(['/activate-account', res.token]);
          return;
        }

        const role = this.normalizeRole(res.role);
        if      (role === 'ADMIN')            this.router.navigate(['/admin']);
        else if (role === 'COMMERCANT')       this.router.navigate(['/commercant']);
        else if (role === 'ANALYSTE_BANCAIRE') this.router.navigate(['/banque']);
        else this.errorMessage = `Rôle inconnu : ${String(res.role)}`;
      },
      error: (err) => {
        this.loading = false;
        this.digits = ['', '', '', '', '', ''];
        setTimeout(() => this.focusInput(0));
        this.errorMessage = err?.error?.error ?? 'Code invalide ou expiré';
      }
    });
  }

  resendOtp() {
    if (this.resendCooldown > 0 || this.loading) return;

    const email = this.authService.getPendingEmail()!;
    this.authService.resendOtp(email).subscribe({
      next: () => {
        this.digits = ['', '', '', '', '', ''];
        this.errorMessage = '';
        this.timerSeconds = 300;
        if (!this.timerInterval) this.startTimer();
        this.startResendCooldown(60);
        setTimeout(() => this.focusInput(0));
      },
      error: () => {
        this.errorMessage = 'Erreur lors du renvoi du code';
      }
    });
  }

  // ── Timer ──
  private startTimer() {
    this.timerInterval = setInterval(() => {
      this.timerSeconds--;
      if (this.timerSeconds <= 0) {
        this.timerSeconds = 0;
        this.stopTimer();
      }
    }, 1000);
  }

  private stopTimer() {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
      this.timerInterval = null;
    }
  }

  // ── Cooldown bouton "Renvoyer" ──
  private startResendCooldown(seconds: number) {
    this.resendCooldown = seconds;
    this.cooldownInterval = setInterval(() => {
      this.resendCooldown--;
      if (this.resendCooldown <= 0) {
        this.resendCooldown = 0;
        clearInterval(this.cooldownInterval);
      }
    }, 1000);
  }

  private normalizeRole(raw: unknown): string | null {
    if (raw == null) return null;
    let up = String(raw).trim().toUpperCase();
    if (up.startsWith('ROLE_')) up = up.slice(5);
    return up;
  }

  ngOnDestroy() {
    this.stopTimer();
    clearInterval(this.cooldownInterval);
  }
}