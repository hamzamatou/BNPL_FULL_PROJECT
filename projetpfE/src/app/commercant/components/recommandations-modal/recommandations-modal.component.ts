import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Affiche les recommandations financières retournées par le service Python
 * (service_recommendation.py → RecommandationResult.recommandations).
 *
 * L'entité Java Recommandation.recommandationsJson est un JSON string → string[].
 * Ce composant reçoit le tableau string[] déjà parsé par le composant parent.
 *
 * Chaque élément est une phrase de conseil (texte libre du LLM ou fallback),
 * ex: "Option 1 : réduire le montant demandé à 4800 TND sur 12 mois."
 */
@Component({
  selector: 'app-recommandations-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <button
      *ngIf="recommandations.length > 0 && !modalOpen"
      class="reco-trigger-btn"
      (click)="open()">
      <div class="btn-icon-wrap">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="2"
             stroke-linecap="round" stroke-linejoin="round">
          <path d="M9 18h6"/><path d="M10 22h4"/>
          <path d="M12 2a7 7 0 017 7 7 7 0 01-3.5 6.05V17a1 1 0 01-1 1h-5a1 1 0 01-1-1v-1.95A7 7 0 015 9a7 7 0 017-7z"/>
        </svg>
      </div>
      <div class="btn-text">
        <span class="btn-label">Recommandations IA</span>
        <span class="btn-sub">
          {{ recommandations.length }} conseil{{ recommandations.length > 1 ? 's' : '' }}
          d'optimisation du dossier
        </span>
      </div>
      <span class="btn-count">{{ recommandations.length }}</span>
    </button>

    <div class="modal-backdrop" *ngIf="modalOpen" (click)="closeOnBackdrop($event)">
      <div class="modal-container" role="dialog" aria-modal="true" aria-labelledby="reco-title">

        <button class="modal-close" (click)="close()" aria-label="Fermer">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
               stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6"  y1="6" x2="18" y2="18"/>
          </svg>
        </button>

        <div class="modal-header">
          <div class="modal-header-icon">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="1.8"
                 stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 18h6"/><path d="M10 22h4"/>
              <path d="M12 2a7 7 0 017 7 7 7 0 01-3.5 6.05V17a1 1 0 01-1 1h-5a1 1 0 01-1-1v-1.95A7 7 0 015 9a7 7 0 017-7z"/>
            </svg>
          </div>
          <div>
            <h2 id="reco-title" class="modal-title">Recommandations IA</h2>
            <p class="modal-subtitle">
              {{ recommandations.length }} conseil{{ recommandations.length > 1 ? 's' : '' }}
              générés par l'analyse du dossier
            </p>
          </div>
        </div>

        <div class="modal-body">
          <div
            *ngFor="let texte of recommandations; let i = index"
            class="reco-card"
            [style.animation-delay]="(i * 50) + 'ms'">
            <div class="reco-number">{{ i + 1 }}</div>
            <p class="reco-texte">{{ texte }}</p>
          </div>
        </div>

        <div class="modal-footer">
          <p class="footer-note">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8"  x2="12"   y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            Ces recommandations sont des conseils d'optimisation, pas une décision finale.
          </p>
          <ng-container *ngIf="showActions; else simpleClose">
            <button type="button" class="btn-secondary" (click)="onModifier()">Modifier le formulaire</button>
            <button type="button" class="btn-primary" (click)="onContinuer()">Continuer</button>
          </ng-container>
          <ng-template #simpleClose>
            <button type="button" class="btn-primary" (click)="close()">Compris</button>
          </ng-template>
        </div>

      </div>
    </div>
  `,
  styles: [`
    :host { display: block; }

    .reco-trigger-btn {
      display: flex; align-items: center; gap: 14px;
      width: 100%; padding: 1rem 1.25rem;
      margin: 1rem 0;
      background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
      border: 1.5px solid #fcd34d; border-radius: 12px;
      cursor: pointer; text-align: left;
      transition: box-shadow 0.2s, transform 0.15s;
    }
    .reco-trigger-btn:hover {
      box-shadow: 0 4px 16px rgba(245,158,11,0.18);
      transform: translateY(-1px);
    }
    .btn-icon-wrap {
      width: 42px; height: 42px; border-radius: 10px;
      background: #fef3c7; border: 1px solid #fcd34d;
      display: flex; align-items: center; justify-content: center;
      flex-shrink: 0; color: #d97706;
    }
    .btn-text { flex: 1; min-width: 0; }
    .btn-label { display: block; font-size: 14px; font-weight: 600; color: #78350f; }
    .btn-sub   { display: block; font-size: 12px; color: #92400e; margin-top: 2px; }
    .btn-count {
      font-size: 13px; font-weight: 700;
      background: #f59e0b; color: #fff;
      padding: 3px 10px; border-radius: 999px; flex-shrink: 0;
    }

    .modal-backdrop {
      position: fixed; inset: 0; z-index: 1000;
      background: rgba(0,0,0,0.55); backdrop-filter: blur(4px);
      display: flex; align-items: center; justify-content: center;
      padding: 1.5rem;
      animation: fadeIn 0.2s ease;
    }
    @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

    .modal-container {
      position: relative;
      background: #fff; border-radius: 20px;
      width: 100%; max-width: 560px; max-height: 90vh;
      display: flex; flex-direction: column; overflow: hidden;
      box-shadow: 0 25px 60px rgba(0,0,0,0.2);
      animation: slideUp 0.3s cubic-bezier(0.16,1,0.3,1);
    }
    @keyframes slideUp {
      from { opacity: 0; transform: translateY(20px) scale(0.97); }
      to   { opacity: 1; transform: translateY(0) scale(1); }
    }

    .modal-close {
      position: absolute; top: 1rem; right: 1rem; z-index: 1;
      width: 32px; height: 32px; border-radius: 8px;
      background: #f3f4f6; border: none; cursor: pointer;
      display: flex; align-items: center; justify-content: center;
      color: #6b7280; transition: background 0.15s;
    }
    .modal-close:hover { background: #e5e7eb; color: #111827; }

    .modal-header {
      display: flex; align-items: center; gap: 14px;
      padding: 1.5rem 1.5rem 1rem;
      background: linear-gradient(135deg, #fffbeb 0%, #fef9ee 100%);
      border-bottom: 1px solid #fde68a; flex-shrink: 0;
    }
    .modal-header-icon {
      width: 48px; height: 48px; border-radius: 14px;
      background: #fef3c7; border: 1.5px solid #fcd34d;
      display: flex; align-items: center; justify-content: center;
      flex-shrink: 0; color: #d97706;
    }
    .modal-title   { margin: 0; font-size: 18px; font-weight: 700; color: #78350f; }
    .modal-subtitle { margin: 3px 0 0; font-size: 13px; color: #92400e; }

    .modal-body {
      padding: 1rem 1.25rem;
      overflow-y: auto; flex: 1;
    }

    .reco-card {
      position: relative;
      display: flex; align-items: flex-start; gap: 14px;
      background: #fff; border: 1px solid #e5e7eb;
      border-left: 4px solid #f59e0b;
      border-radius: 10px; padding: 1rem;
      margin-bottom: 0.75rem;
      animation: cardIn 0.3s ease both;
      transition: box-shadow 0.2s;
    }
    .reco-card:hover { box-shadow: 0 3px 12px rgba(0,0,0,0.07); }
    .reco-card:last-child { margin-bottom: 0; }

    @keyframes cardIn {
      from { opacity: 0; transform: translateY(6px); }
      to   { opacity: 1; transform: translateY(0); }
    }

    .reco-number {
      font-size: 12px; font-weight: 700; color: #f59e0b;
      background: #fef3c7; border-radius: 50%;
      width: 24px; height: 24px;
      display: flex; align-items: center; justify-content: center;
      flex-shrink: 0; margin-top: 1px;
    }

    .reco-texte {
      margin: 0; font-size: 14px; color: #1f2937;
      line-height: 1.6; flex: 1;
    }

    .modal-footer {
      display: flex; align-items: center; justify-content: space-between; gap: 12px;
      padding: 1rem 1.5rem;
      background: #f9fafb; border-top: 1px solid #e5e7eb; flex-shrink: 0;
    }
    .footer-note {
      display: flex; align-items: flex-start; gap: 6px;
      margin: 0; font-size: 12px; color: #9ca3af; line-height: 1.5; flex: 1;
    }
    .btn-primary {
      padding: 0.55rem 1.5rem;
      background: #f59e0b; color: #fff;
      border: none; border-radius: 8px;
      font-size: 14px; font-weight: 600; cursor: pointer; flex-shrink: 0;
      transition: background 0.15s;
    }
    .btn-primary:hover { background: #d97706; }
    .btn-secondary {
      padding: 0.55rem 1rem;
      background: #fff; color: #374151;
      border: 1px solid #d1d5db; border-radius: 8px;
      font-size: 14px; font-weight: 600; cursor: pointer; flex-shrink: 0;
    }
    .btn-secondary:hover { background: #f3f4f6; }

    @media (prefers-color-scheme: dark) {
      .reco-trigger-btn    { background: linear-gradient(135deg,#2d1f00,#3d2b00); border-color: #854d0e; }
      .btn-icon-wrap       { background: #3d2b00; border-color: #854d0e; color: #fbbf24; }
      .btn-label           { color: #fcd34d; }
      .btn-sub             { color: #d97706; }
      .modal-container     { background: #1a1a1a; }
      .modal-header        { background: linear-gradient(135deg,#2d1f00,#3d2b00); border-color: #854d0e; }
      .modal-header-icon   { background: #3d2b00; border-color: #854d0e; color: #fbbf24; }
      .modal-title         { color: #fcd34d; }
      .modal-subtitle      { color: #d97706; }
      .reco-card           { background: #242424; border-color: #374151; border-left-color: #d97706; }
      .reco-texte          { color: #e5e7eb; }
      .reco-number         { background: #3d2b00; color: #fbbf24; }
      .modal-close         { background: #374151; color: #9ca3af; }
      .modal-close:hover   { background: #4b5563; color: #f9fafb; }
      .modal-footer        { background: #141414; border-color: #374151; }
    }
  `]
})
export class RecommandationsModalComponent {
  /** Tableau de phrases de conseil — string[] parsé depuis Recommandation.recommandationsJson */
  @Input() recommandations: string[] = [];
  @Input() modalOpen = false;
  /** Si true, footer = Modifier + Continuer (étape analyse avant création). */
  @Input() showActions = false;
  @Output() modalOpenChange = new EventEmitter<boolean>();
  @Output() modifier = new EventEmitter<void>();
  @Output() continuer = new EventEmitter<void>();

  open()  { this.modalOpen = true;  this.modalOpenChange.emit(true);  }
  close() { this.modalOpen = false; this.modalOpenChange.emit(false); }

  onModifier(): void {
    this.close();
    this.modifier.emit();
  }

  onContinuer(): void {
    this.close();
    this.continuer.emit();
  }

  closeOnBackdrop(event: MouseEvent) {
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) this.close();
  }
}