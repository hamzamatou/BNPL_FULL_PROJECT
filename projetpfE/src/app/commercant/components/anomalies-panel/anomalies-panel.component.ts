import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Affiche les anomalies de cohérence retournées par le backend Java.
 *
 * Le backend retourne HTTP 422 avec :
 *   { message: string, anomalies: string[] }
 *
 * Ce composant reçoit directement le tableau string[] (anomalies brutes du service Python).
 * Chaque chaîne est affichée telle quelle — pas de mapping vers un objet structuré.
 */
@Component({
  selector: 'app-anomalies-panel',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="anomalies-wrapper" *ngIf="anomalies && anomalies.length > 0">

      <div class="panel-header">
        <div class="header-left">
          <div class="icon-wrapper">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round">
              <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/>
              <line x1="12" y1="9" x2="12" y2="13"/>
              <line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
          </div>
          <div>
            <h3 class="panel-title">Anomalies détectées</h3>
            <p class="panel-subtitle">
              {{ anomalies.length }} incohérence{{ anomalies.length > 1 ? 's' : '' }}
              — la demande ne peut pas être soumise
            </p>
          </div>
        </div>
        <span class="count-badge">{{ anomalies.length }}</span>
      </div>

      <ul class="anomalies-list">
        <li *ngFor="let texte of anomalies; let i = index" class="anomalie-row">
          <div class="row-indicator"></div>
          <div class="row-content">
            <p class="anomalie-message">{{ texte }}</p>
          </div>
        </li>
      </ul>

      <div class="panel-footer">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="2"
             stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        <span>Veuillez corriger le dossier et le soumettre à nouveau.</span>
      </div>

    </div>
  `,
  styles: [`
    :host { display: block; }

    .anomalies-wrapper {
      margin: 1.5rem 0;
      border: 1px solid #f5c6c6;
      border-radius: 12px;
      background: #fff;
      overflow: hidden;
      animation: slideDown 0.3s cubic-bezier(0.16,1,0.3,1);
    }

    @keyframes slideDown {
      from { opacity: 0; transform: translateY(-10px); }
      to   { opacity: 1; transform: translateY(0); }
    }

    .panel-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1rem 1.25rem;
      background: #fff5f5;
      border-bottom: 1px solid #f5c6c6;
    }

    .header-left { display: flex; align-items: center; gap: 12px; }

    .icon-wrapper {
      width: 38px; height: 38px; border-radius: 10px;
      display: flex; align-items: center; justify-content: center;
      background: #fee2e2; color: #dc2626; flex-shrink: 0;
    }

    .panel-title {
      margin: 0; font-size: 15px; font-weight: 600;
      color: #7f1d1d; letter-spacing: -0.01em;
    }
    .panel-subtitle { margin: 2px 0 0; font-size: 12px; color: #991b1b; }

    .count-badge {
      font-size: 12px; font-weight: 700; padding: 3px 9px;
      border-radius: 999px; background: #fee2e2; color: #b91c1c;
    }

    .anomalies-list { list-style: none; margin: 0; padding: 0.5rem 0; }

    .anomalie-row {
      display: flex; align-items: stretch;
      padding: 0.75rem 1.25rem;
      gap: 14px;
      border-bottom: 1px solid #fef2f2;
      animation: rowIn 0.25s ease both;
    }
    .anomalie-row:last-child { border-bottom: none; }
    .anomalie-row:hover { background: #fef2f2; }

    @keyframes rowIn {
      from { opacity: 0; transform: translateX(-4px); }
      to   { opacity: 1; transform: translateX(0); }
    }

    .row-indicator {
      width: 3px; border-radius: 99px; flex-shrink: 0;
      background: #ef4444; min-height: 32px;
    }

    .row-content { flex: 1; min-width: 0; }

    .anomalie-message {
      margin: 0; font-size: 14px; color: #1f2937; line-height: 1.5;
    }

    .panel-footer {
      display: flex; align-items: flex-start; gap: 8px;
      padding: 0.75rem 1.25rem;
      background: #fff5f5;
      border-top: 1px solid #f5c6c6;
      font-size: 12px; color: #6b7280; line-height: 1.5;
    }

    @media (prefers-color-scheme: dark) {
      .anomalies-wrapper  { background: #1a1a1a; border-color: #7f1d1d; }
      .panel-header       { background: #2d1515; border-color: #7f1d1d; }
      .panel-title        { color: #fca5a5; }
      .panel-subtitle     { color: #f87171; }
      .icon-wrapper       { background: #450a0a; color: #f87171; }
      .count-badge        { background: #450a0a; color: #fca5a5; }
      .anomalie-row       { border-color: #2d1515; }
      .anomalie-row:hover { background: #2d1515; }
      .anomalie-message   { color: #e5e7eb; }
      .panel-footer       { background: #2d1515; border-color: #7f1d1d; color: #9ca3af; }
    }
  `]
})
export class AnomaliesPanelComponent {
  /** Tableau de chaînes brutes retourné par le backend : { anomalies: string[] } */
  @Input() anomalies: string[] = [];
}