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
import {
  classifyOtpVerifyError,
  extractHttpErrorMessage,
  OTP_EXPIRED_MESSAGE,
  type OtpVerifyErrorKind,
} from '../../../shared/utils/http-error.util';

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
  otpErrorKind: OtpVerifyErrorKind | null = null;

  otpSecondsLeft = 300;
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

  readonly flowSteps = [
    { label: 'Identité' },
    { label: 'OTP' },
    { label: 'Consentement' },
  ] as const;

  private readonly stepOrder: UiStep[] = ['identity', 'otp', 'confirm', 'done'];

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
          this.successMessage = 'OTP envoye par email (valide 5 minutes).';
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
          const msg = extractHttpErrorMessage(err, "Impossible d'envoyer le code OTP. Veuillez réessayer.");
          if (fromResend || this.step === 'otp') {
            this.otpInlineError = msg;
          } else {
            this.errorMessage = msg;
          }
        },
      });
  }

  verifyOtp(): void {
    this.consentError = '';
    this.otpInlineError = '';
    this.otpErrorKind = null;
    if (this.otpExpired) {
      this.otpErrorKind = 'expired';
      this.otpInlineError = OTP_EXPIRED_MESSAGE;
      return;
    }
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
          const otpError = classifyOtpVerifyError(err);
          this.otpErrorKind = otpError.kind;
          this.otpInlineError = otpError.message;
          if (otpError.kind === 'incorrect') {
            this.clearOtpInputsFocusFirst();
          }
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

    if (
      !this.acceptCGF ||
      !this.acceptCentraleRisques ||
      !this.acceptDataProcessing ||
      !this.acceptMarketingOptional
    ) {
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
        this.consentError = extractHttpErrorMessage(
          err,
          'Impossible de confirmer le consentement. Veuillez réessayer.'
        );
      },
    });
  }

  onOtpDigitInput(index: number, value: string): void {
    if (this.otpExpired) {
      return;
    }
    this.otpInlineError = '';
    this.otpErrorKind = null;
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
    if (this.otpExpired) {
      ev.preventDefault();
      return;
    }
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
    if (this.otpExpired) {
      return;
    }
    this.otpInlineError = '';
    this.otpErrorKind = null;
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
    this.otpSecondsLeft = 300;
    this.otpDigits = Array(6).fill('');
    this.otp = '';
    this.otpInlineError = '';
    this.otpErrorKind = null;
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

  get otpExpired(): boolean {
    return this.otpSecondsLeft <= 0;
  }

  get canResendOtp(): boolean {
    return this.otpExpired && !this.resendCooldown && !this.loading;
  }

  backToIdentity(): void {
    this.stopOtpCountdown();
    this.step = 'identity';
    this.otpInlineError = '';
    this.otpErrorKind = null;
    this.otpDigits = Array(6).fill('');
    this.otp = '';
  }

  currentStepNumber(): number {
    if (this.step === 'done') {
      return this.flowSteps.length;
    }
    const idx = this.stepOrder.indexOf(this.step);
    return idx >= 0 ? idx + 1 : 1;
  }

  stepState(index: number): 'done' | 'active' | 'pending' {
    const current = this.stepOrder.indexOf(this.step);
    if (index < current) {
      return 'done';
    }
    if (index === current) {
      return 'active';
    }
    return 'pending';
  }

  progressPercent(): number {
    if (this.step === 'done') {
      return 100;
    }
    const current = this.stepOrder.indexOf(this.step);
    if (current <= 0) {
      return 12;
    }
    return Math.min(100, ((current + 0.5) / this.flowSteps.length) * 100);
  }
}
