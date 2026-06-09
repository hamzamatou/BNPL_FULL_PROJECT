import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-alertes-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './alertes-panel.component.html',
  styleUrls: ['./alertes-panel.component.css'],
})
export class AlertesPanelComponent {
  @Input() alertes: string[] = [];
}
