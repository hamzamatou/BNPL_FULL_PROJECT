import { Component, Input } from '@angular/core';

export type AdminIconName =
  | 'plus'
  | 'user-plus'
  | 'arrow-left'
  | 'search'
  | 'lock'
  | 'unlock'
  | 'trash'
  | 'eye'
  | 'edit'
  | 'x'
  | 'check'
  | 'printer'
  | 'trace'
  | 'chevron-left'
  | 'chevron-right'
  | 'save';

@Component({
  selector: 'admin-icon',
  standalone: true,
  templateUrl: './admin-icon.component.html',
  styleUrls: ['./admin-icon.component.css'],
  host: {
    class: 'admin-icon',
    '[class.admin-icon--md]': 'size === "md"',
    'aria-hidden': 'true',
  },
})
export class AdminIconComponent {
  @Input({ required: true }) name!: AdminIconName;
  @Input() size: 'sm' | 'md' = 'sm';
}
