import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  OnDestroy,
  QueryList,
  ViewChildren,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ActionClientService } from '../../../services/action-client.service';

type UiStep = 'identity' | 'otp' | 'confirm' | 'done';

@Component({
  selector: 'app-action-client',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './action-client.component.html',
  styleUrl: './action-client.component.css',
})
export class ActionClientComponent implements OnDestroy {
  token = '';
  /** Référence métier de la demande (plusieurs demandes possibles pour un même client). Passée en query ?ref= ou ?referenceDemande= */
  referenceDemande = '';
  /** E-mail brut pour affichage masqué (optionnel, ex. ?email= depuis le lien magique). */
  private emailFromQuery = '';

  nom = '';
  prenom = '';
  cin = '';
  otp = '';

  step: UiStep = 'identity';
  loading = false;
  errorMessage = '';
  successMessage = '';
  otpInlineError = '';

  otpSecondsLeft = 120;
  private otpTimer: ReturnType<typeof setInterval> | null = null;
  otpDigits: string[] = Array(6).fill('');
  otpIndices = [0, 1, 2, 3, 4, 5];
  resendCooldown = false;

  @ViewChildren('otpCell') otpCells!: QueryList<ElementRef<HTMLInputElement>>;

  acceptCGF = false;
  acceptCentraleRisques = false;
  acceptDataProcessing = false;
  acceptMarketingOptional = false;
  consentError = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly actionClientService: ActionClientService
  ) {
    const q = this.route.snapshot.queryParamMap;
    this.token = q.get('token') ?? '';
    this.referenceDemande = q.get('referenceDemande') ?? q.get('ref') ?? '';
    this.emailFromQuery = this.safeDecodeParam(q.get('email'));
    if (!this.token) {
      this.errorMessage = 'Lien invalide: token manquant.';
    }
  }

  ngOnDestroy(): void {
    this.stopOtpCountdown();
  }

  private safeDecodeParam(raw: string | null): string {
    if (!raw) {
      return '';
    }
    const t = raw.trim();
    try {
      return decodeURIComponent(t);
    } catch {
      return t;
    }
  }

  get maskedEmailLabel(): string {
    const raw = this.emailFromQuery;
    if (!raw || !raw.includes('@')) {
      return '';
    }
    const [local, domain] = raw.split('@');
    if (!domain) {
      return raw;
    }
    const head = local.slice(0, 2) || '•';
    return `${head}•••@${domain}`;
  }

  sendOtp(fromResend = false): void {
    if (!this.token || !this.nom || !this.prenom || !this.cin) {
      this.errorMessage = 'Nom, prenom et CIN sont obligatoires.';
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.otpInlineError = '';

    this.actionClientService
      .sendOtp(this.token, this.nom, this.prenom, this.cin, this.referenceDemande || undefined)
      .subscribe({
        next: () => {
          this.loading = false;
          this.step = 'otp';
          this.successMessage = 'OTP envoye par email (valide 10 minutes).';
          this.startOtpCountdown();
          if (fromResend) {
            this.resendCooldown = true;
            setTimeout(() => {
              this.resendCooldown = false;
            }, 3000);
          }
        },
        error: (err) => {
          this.loading = false;
          this.errorMessage =
            err?.error?.message || err?.error?.error || err?.message || 'Echec envoi OTP.';
        },
      });
  }

  verifyOtp(): void {
    this.consentError = '';
    this.otpInlineError = '';
    if (!this.token || !this.otp) {
      this.otpInlineError = 'Code OTP obligatoire.';
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';

    this.actionClientService
      .verifyOtp(this.token, this.otp, this.referenceDemande || undefined)
      .subscribe({
        next: () => {
          this.loading = false;
          this.step = 'confirm';
          this.successMessage = 'OTP valide.';
        },
        error: (err) => {
          this.loading = false;
          this.otpInlineError =
            err?.error?.message || err?.error?.error || err?.message || 'OTP invalide.';
          this.clearOtpInputsFocusFirst();
        },
      });
  }

  confirmConsent(): void {
    if (!this.token) {
      this.errorMessage = 'Token manquant.';
      return;
    }

    this.consentError = '';
    this.errorMessage = '';

    if (!this.acceptCGF || !this.acceptCentraleRisques || !this.acceptDataProcessing) {
      this.consentError =
        'Vous devez cocher toutes les cases obligatoires pour confirmer le consentement.';
      return;
    }

    this.loading = true;
    this.successMessage = '';

    this.actionClientService.confirmConsent(this.token).subscribe({
      next: () => {
        this.loading = false;
        this.step = 'done';
        this.successMessage = 'Consentement confirme. Votre demande a ete soumise.';
      },
      error: (err) => {
        this.loading = false;
        this.errorMessage =
          err?.error?.message ||
          err?.error?.error ||
          err?.message ||
          'Echec confirmation consentement.';
      },
    });
  }

  onOtpDigitInput(index: number, value: string): void {
    this.otpInlineError = '';
    const v = (value ?? '').toString().replace(/\D/g, '').slice(0, 1);
    this.otpDigits[index] = v;
    this.otp = this.otpDigits.join('');
    if (v && index < 5) {
      setTimeout(() => {
        const cells = this.otpCells?.toArray();
        cells[index + 1]?.nativeElement.focus();
      });
    }
  }

  onOtpKeydown(index: number, ev: KeyboardEvent): void {
    if (ev.key === 'Backspace' && !this.otpDigits[index] && index > 0) {
      ev.preventDefault();
      this.otpDigits[index - 1] = '';
      this.otp = this.otpDigits.join('');
      const cells = this.otpCells.toArray();
      cells[index - 1]?.nativeElement.focus();
    }
  }

  onOtpPaste(ev: ClipboardEvent): void {
    ev.preventDefault();
    this.otpInlineError = '';
    const text = (ev.clipboardData?.getData('text') ?? '').replace(/\D/g, '').slice(0, 6);
    const cells = this.otpCells.toArray();
    for (let i = 0; i < 6; i++) {
      this.otpDigits[i] = text[i] ?? '';
    }
    this.otp = this.otpDigits.join('');
    const last = Math.min(Math.max(text.length - 1, 0), 5);
    cells[last]?.nativeElement.focus();
  }

  resendOtp(): void {
    if (!this.canResendOtp) {
      return;
    }
    this.otpInlineError = '';
    this.sendOtp(true);
  }

  private clearOtpInputsFocusFirst(): void {
    this.otpDigits = Array(6).fill('');
    this.otp = '';
    setTimeout(() => this.otpCells?.first?.nativeElement.focus());
  }

  private startOtpCountdown(): void {
    this.stopOtpCountdown();
    this.otpSecondsLeft = 120;
    this.otpDigits = Array(6).fill('');
    this.otp = '';
    this.resendCooldown = false;

    this.otpTimer = setInterval(() => {
      this.otpSecondsLeft = Math.max(0, this.otpSecondsLeft - 1);
      if (this.otpSecondsLeft <= 0) {
        this.stopOtpCountdown();
      }
    }, 1000);

    setTimeout(() => this.otpCells?.first?.nativeElement.focus());
  }

  private stopOtpCountdown(): void {
    if (this.otpTimer) {
      clearInterval(this.otpTimer);
      this.otpTimer = null;
    }
  }

  get otpTimerLabel(): string {
    if (this.otpSecondsLeft <= 0) {
      return 'expiré';
    }
    const m = Math.floor(this.otpSecondsLeft / 60);
    const s = this.otpSecondsLeft % 60;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }

  get otpAllFilled(): boolean {
    return this.otpDigits.every((d) => d.length === 1);
  }

  get canResendOtp(): boolean {
    return this.otpSecondsLeft <= 0 && !this.resendCooldown && !this.loading;
  }

  backToIdentity(): void {
    this.stopOtpCountdown();
    this.step = 'identity';
    this.otpInlineError = '';
    this.otpDigits = Array(6).fill('');
    this.otp = '';
  }
}
