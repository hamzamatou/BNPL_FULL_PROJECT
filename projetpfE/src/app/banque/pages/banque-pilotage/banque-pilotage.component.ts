import { CommonModule, DatePipe } from '@angular/common';
import {
  afterNextRender,
  Component,
  ElementRef,
  inject,
  Injector,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import { forkJoin } from 'rxjs';
import { DemandeService } from '../../../services/demande.service';
import { ReportingArchivageService } from '../../../services/reporting-archivage.service';
import {
  BanqueDashboardDto,
  libelleTypeDecision,
} from '../../../models/reporting.models';

Chart.register(...registerables);

const UIB_RED = '#d3121f';
const UIB_RED_LIGHT = '#f87171';
const UIB_SLATE = '#64748b';
const UIB_NEUTRAL = '#94a3b8';
const UIB_BG = '#f8fafc';

@Component({
  selector: 'app-banque-pilotage',
  standalone: true,
  imports: [CommonModule, DatePipe, RouterLink],
  templateUrl: './banque-pilotage.component.html',
  styleUrls: ['./banque-pilotage.component.css'],
  host: { class: 'page-host' },
})
export class BanquePilotageComponent implements OnInit, OnDestroy {
  @ViewChild('decisionsBarCanvas') decisionsBarCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('repartitionDoughnutCanvas') repartitionDoughnutCanvas?: ElementRef<HTMLCanvasElement>;

  loading = false;
  errorMessage = '';

  dashboard: BanqueDashboardDto | null = null;
  demandesEnCharge = 0;

  readonly libelleDecision = libelleTypeDecision;

  private decisionsBarChart: Chart | null = null;
  private repartitionChart: Chart | null = null;
  private readonly injector = inject(Injector);

  constructor(
    private readonly reportingService: ReportingArchivageService,
    private readonly demandeService: DemandeService
  ) {}

  ngOnInit(): void {
    this.loadDashboard();
  }

  ngOnDestroy(): void {
    this.destroyCharts();
  }

  hasRepartitionData(): boolean {
    return this.getRepartitionEntries().length > 0;
  }

  decisionBadgeClass(type: string): string {
    const t = (type || '').toUpperCase();
    if (t === 'ACCEPTEE') return 'badge-accept';
    if (t === 'REFUSEE') return 'badge-refuse';
    if (t === 'DEMANDE_COMPLEMENTS') return 'badge-complement';
    if (t === 'PRISE_EN_CHARGE') return 'badge-pec';
    return 'badge-neutral';
  }

  private getRepartitionEntries(): { key: string; value: number }[] {
    const map = this.dashboard?.repartitionDecisionsParType;
    if (map) {
      const fromMap = Object.entries(map)
        .filter(([, v]) => v > 0)
        .map(([key, value]) => ({ key, value }));
      if (fromMap.length > 0) return fromMap;
    }
    const d = this.dashboard;
    if (!d) return [];
    return [
      { key: 'PRISE_EN_CHARGE', value: d.prisesEnCharge },
      { key: 'ACCEPTEE', value: d.acceptees },
      { key: 'REFUSEE', value: d.refusees },
      { key: 'DEMANDE_COMPLEMENTS', value: d.complements },
    ].filter((e) => e.value > 0);
  }

  private loadDashboard(): void {
    this.loading = true;
    this.errorMessage = '';
    this.destroyCharts();

    forkJoin({
      dash: this.reportingService.getDashboardBanque(),
      enCharge: this.demandeService.getDemandesAffecteesPourBanque(),
    }).subscribe({
      next: ({ dash, enCharge }) => {
        this.dashboard = dash;
        this.demandesEnCharge = enCharge.length;
        this.loading = false;
        this.scheduleChartRefresh();
      },
      error: (err) => this.handleError(err),
    });
  }

  private scheduleChartRefresh(): void {
    afterNextRender(() => this.refreshCharts(), { injector: this.injector });
  }

  private refreshCharts(): void {
    if (!this.dashboard) return;
    this.buildDecisionsBarChart();
    this.buildRepartitionChart();
  }

  private buildDecisionsBarChart(): void {
    const canvas = this.decisionsBarCanvas?.nativeElement;
    if (!canvas || !this.dashboard) return;

    this.decisionsBarChart?.destroy();
    const d = this.dashboard;

    const config: ChartConfiguration<'bar'> = {
      type: 'bar',
      data: {
        labels: [
          'En charge',
          'Décisions 24 h',
          'Acceptées',
          'Refusées',
          'Compléments',
          'Prises en charge',
        ],
        datasets: [
          {
            label: 'Nombre',
            data: [
              this.demandesEnCharge,
              d.decisions24h,
              d.acceptees,
              d.refusees,
              d.complements,
              d.prisesEnCharge,
            ],
            backgroundColor: [
              UIB_RED,
              '#334155',
              UIB_SLATE,
              UIB_RED_LIGHT,
              UIB_NEUTRAL,
              '#ab0f19',
            ],
            borderRadius: 8,
            borderSkipped: false,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          title: {
            display: true,
            text: 'Synthèse de votre activité',
            color: '#1a2536',
            font: { size: 14, weight: 'bold' },
            padding: { bottom: 12 },
          },
        },
        scales: {
          x: {
            grid: { display: false },
            ticks: { color: '#64748b', font: { size: 11 } },
          },
          y: {
            beginAtZero: true,
            ticks: { stepSize: 1, color: '#64748b' },
            grid: { color: '#f1f5f9' },
          },
        },
      },
    };

    this.decisionsBarChart = new Chart(canvas, config);
  }

  private buildRepartitionChart(): void {
    const canvas = this.repartitionDoughnutCanvas?.nativeElement;
    if (!canvas || !this.dashboard) return;

    this.repartitionChart?.destroy();
    const entries = this.getRepartitionEntries();
    if (entries.length === 0) return;

    const labels = entries.map((e) => libelleTypeDecision(e.key));
    const values = entries.map((e) => e.value);
    const colors = entries.map((e) => this.colorForDecisionType(e.key));

    const config: ChartConfiguration<'doughnut'> = {
      type: 'doughnut',
      data: {
        labels,
        datasets: [
          {
            data: values,
            backgroundColor: colors,
            borderWidth: 2,
            borderColor: '#fff',
            hoverOffset: 6,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '58%',
        plugins: {
          legend: {
            position: 'bottom',
            labels: {
              color: '#475569',
              padding: 14,
              font: { size: 12 },
              usePointStyle: true,
            },
          },
          title: {
            display: true,
            text: 'Répartition par type de décision',
            color: '#1a2536',
            font: { size: 14, weight: 'bold' },
            padding: { bottom: 8 },
          },
        },
      },
    };

    this.repartitionChart = new Chart(canvas, config);
  }

  private colorForDecisionType(type: string): string {
    const t = (type || '').toUpperCase();
    if (t === 'ACCEPTEE') return UIB_SLATE;
    if (t === 'REFUSEE') return UIB_RED;
    if (t === 'DEMANDE_COMPLEMENTS') return UIB_NEUTRAL;
    if (t === 'PRISE_EN_CHARGE') return UIB_RED_LIGHT;
    if (t === 'ROUTAGE') return '#cbd5e1';
    return UIB_BG;
  }

  private destroyCharts(): void {
    this.decisionsBarChart?.destroy();
    this.repartitionChart?.destroy();
    this.decisionsBarChart = null;
    this.repartitionChart = null;
  }

  private handleError(err: unknown): void {
    this.loading = false;
    const body = (err as { error?: { message?: string } })?.error;
    this.errorMessage =
      body?.message ??
      'Impossible de charger les données (vérifiez que reporting-archivage tourne sur le port 8083).';
  }
}
