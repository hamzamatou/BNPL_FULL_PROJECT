import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-consentement',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './consentement.component.html',
  styleUrls: ['./consentement.component.css'],
})
export class ConsentementComponent {
  @Input() status: 'active' | 'completed' | 'pending' = 'pending';
  @Input() submitting = false;
  @Input() success = false;
  @Input() errorMessage = '';

  @Output() prevStep = new EventEmitter<void>();
  @Output() submit = new EventEmitter<void>();
  @Output() restart = new EventEmitter<void>();

  goBack(): void {
    this.prevStep.emit();
  }

  envoyer(): void {
    if (!this.submitting) {
      this.submit.emit();
    }
  }

  restartFlow(): void {
    this.restart.emit();
  }
}
