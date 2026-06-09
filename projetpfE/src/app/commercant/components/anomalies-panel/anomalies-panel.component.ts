import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  CoherenceAnomalie,
  formatTnd,
} from '../../../models/coherence-anomalie.model';

@Component({
  selector: 'app-anomalies-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './anomalies-panel.component.html',
  styleUrls: ['./anomalies-panel.component.css'],
})
export class AnomaliesPanelComponent {
  @Input() anomalies: CoherenceAnomalie[] = [];
  @Input() modalOpen = false;

  @Output() modalOpenChange = new EventEmitter<boolean>();
  @Output() modifier = new EventEmitter<void>();

  formatTnd = formatTnd;

  close(): void {
    this.modalOpen = false;
    this.modalOpenChange.emit(false);
  }

  onModifier(): void {
    this.close();
    this.modifier.emit();
  }

  closeOnBackdrop(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('confirm-overlay')) {
      this.close();
    }
  }

  isRevenuAnomaly(item: CoherenceAnomalie): boolean {
    return item.code === 'COH_REVENU_DIFF' && !!item.details?.revenu_extrait;
  }

  libelleChamp(code: string | undefined): string {
    const map: Record<string, string> = {
      COH_REVENU_DIFF: 'Revenu mensuel net',
      COH_CIN_DIFF: 'CIN',
      COH_LOYER_DIFF: 'Loyer mensuel',
      COH_ANCIENNETE_DIFF: 'Ancienneté emploi',
      COH_DEVIS_DIFF: 'Montant du devis',
      COH_CIN_MISMATCH: 'CIN',
    };
    return (code && map[code]) || 'Écart détecté';
  }
}
