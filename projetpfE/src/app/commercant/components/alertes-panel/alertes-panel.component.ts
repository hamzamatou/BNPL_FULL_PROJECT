import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

/** Alertes non bloquantes (écarts détectés mais dossier poursuivable). */
@Component({
  selector: 'app-alertes-panel',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="alertes-wrapper" *ngIf="alertes && alertes.length > 0">
      <div class="panel-header">
        <div class="header-left">
          <div class="icon-wrapper">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/>
              <line x1="12" y1="9" x2="12" y2="13"/>
              <line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
          </div>
          <div>
            <h3 class="panel-title">Écarts détectés</h3>
            <p class="panel-subtitle">
              Les champs ont été pré-remplis depuis les documents — vérifiez avant de continuer.
            </p>
          </div>
        </div>
      </div>
      <ul class="alertes-list">
        <li *ngFor="let texte of alertes" class="alerte-row">{{ texte }}</li>
      </ul>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .alertes-wrapper {
      margin: 1rem 0;
      border: 1px solid #fcd34d;
      border-radius: 12px;
      background: #fffbeb;
      overflow: hidden;
    }
    .panel-header {
      padding: 1rem 1.25rem;
      border-bottom: 1px solid #fde68a;
    }
    .header-left { display: flex; align-items: flex-start; gap: 12px; }
    .icon-wrapper {
      width: 38px; height: 38px; border-radius: 10px;
      display: flex; align-items: center; justify-content: center;
      background: #fef3c7; color: #d97706; flex-shrink: 0;
    }
    .panel-title { margin: 0; font-size: 15px; font-weight: 600; color: #92400e; }
    .panel-subtitle { margin: 4px 0 0; font-size: 12px; color: #b45309; line-height: 1.4; }
    .alertes-list { list-style: none; margin: 0; padding: 0.5rem 0; }
    .alerte-row {
      padding: 0.6rem 1.25rem;
      font-size: 14px;
      color: #78350f;
      border-bottom: 1px solid #fef3c7;
    }
    .alerte-row:last-child { border-bottom: none; }
  `],
})
export class AlertesPanelComponent {
  @Input() alertes: string[] = [];
}
