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
  @Output() restart = new EventEmitter<void>();

  goBack() {
    this.prevStep.emit();
  }

  restartFlow() {
    this.restart.emit();
  }
}
