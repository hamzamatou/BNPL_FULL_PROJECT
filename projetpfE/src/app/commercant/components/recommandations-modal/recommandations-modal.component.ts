import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { listeIndiqueDemandeConforme } from '../../../shared/utils/recommandations.util';

@Component({
  selector: 'app-recommandations-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './recommandations-modal.component.html',
  styleUrls: ['./recommandations-modal.component.css'],
})
export class RecommandationsModalComponent {
  @Input() recommandations: string[] = [];
  @Input() modalOpen = false;
  @Input() showActions = false;

  get demandeConforme(): boolean {
    return listeIndiqueDemandeConforme(this.recommandations);
  }

  @Output() modalOpenChange = new EventEmitter<boolean>();
  @Output() modifier = new EventEmitter<void>();
  @Output() continuer = new EventEmitter<void>();

  close(): void {
    this.modalOpen = false;
    this.modalOpenChange.emit(false);
  }

  onModifier(): void {
    this.close();
    this.modifier.emit();
  }

  onContinuer(): void {
    this.close();
    this.continuer.emit();
  }

  closeOnBackdrop(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('confirm-overlay')) {
      this.close();
    }
  }
}
