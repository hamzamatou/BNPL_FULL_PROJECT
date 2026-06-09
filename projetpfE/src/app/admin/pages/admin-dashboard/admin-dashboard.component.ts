import { CommonModule, DatePipe } from '@angular/common';
import {
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Chart as ChartJS, registerables } from 'chart.js';
import { AdminIconComponent } from '../../../shared/admin-icon/admin-icon.component';
import { ReportingArchivageService } from '../../../services/reporting-archivage.service';
import {
  DashboardReportingDto,
  libelleTypeActionDemande,
  libelleTypeDecision,
} from '../../../models/reporting.models';

ChartJS.register(...registerables);

const UIB_RED = '#d3121f';
const UIB_INK = '#334155';
const UIB_SLATE = '#64748b';
const UIB_AMBER = '#f59e0b';
const UIB_GREEN = '#16a34a';
const UIB_BLUE = '#2563eb';
const UIB_PURPLE = '#8b5cf6';
const UIB_MUTED = '#94a3b8';
const SCORE_MAX = 1000;

const PALETTE = [UIB_RED, UIB_INK, UIB_SLATE, UIB_AMBER, UIB_MUTED, UIB_GREEN, UIB_BLUE, UIB_PURPLE];

const ZONE_COLORS: Record<string, string> = {
  vert: UIB_GREEN,
  orange: UIB_AMBER,
  rouge: UIB_RED,
  inconnu: UIB_MUTED,
};

const STATUT_COLORS: Record<string, string> = {
  'Créée': UIB_MUTED,
  'En attente consentement': UIB_MUTED,
  'Prescoring en cours': UIB_PURPLE,
  Soumise: UIB_BLUE,
  "En cours d'analyse": UIB_AMBER,
  'En cours analyse': UIB_AMBER,
  'En attente compléments': UIB_PURPLE,
  'En attente complément': UIB_PURPLE,
  Acceptée: UIB_GREEN,
  Refusée: UIB_RED,
  'Rejetée (auto)': UIB_RED,
  Annulée: UIB_SLATE,
  Clôturée: UIB_SLATE,
};

type ChartKey =
  | 'actions'
  | 'decisions'
  | 'scoringZone'
  | 'scoringRisque'
  | 'scoringGauge'
  | 'evolution'
  | 'statuts'
  | 'banque'
  | 'commercant'
  | 'acceptRefuse'
  | 'portefeuille'
  | 'activite';

const SCORING_ZONES = [
  { key: 'vert', label: 'Vert', color: UIB_GREEN },
  { key: 'orange', label: 'Orange', color: UIB_AMBER },
  { key: 'rouge', label: 'Rouge', color: UIB_RED },
] as const;

const SCORING_RISQUES = [
  { key: 'faible', label: 'Risque Faible', color: UIB_GREEN },
  { key: 'moyen', label: 'Risque Moyen', color: UIB_AMBER },
  { key: 'eleve', label: 'Risque Élevé', color: UIB_RED },
] as const;

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, DatePipe, RouterLink, AdminIconComponent],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.css'],
  host: { class: 'page-host' },
})
export class AdminDashboardComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild('actionsBarCanvas') actionsBarCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('decisionsDoughnutCanvas') decisionsDoughnutCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('scoringZoneCanvas') scoringZoneCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('scoringRisqueCanvas') scoringRisqueCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('scoringGaugeCanvas') scoringGaugeCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('evolutionLineCanvas') evolutionLineCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('statutDoughnutCanvas') statutDoughnutCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('banqueBarCanvas') banqueBarCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('commercantBarCanvas') commercantBarCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('acceptRefusePieCanvas') acceptRefusePieCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('portefeuilleDoughnutCanvas') portefeuilleDoughnutCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('activiteBarCanvas') activiteBarCanvas?: ElementRef<HTMLCanvasElement>;

  loading = false;
  errorMessage = '';
  dashboard: DashboardReportingDto | null = null;

  readonly libelleAction = libelleTypeActionDemande;
  readonly libelleDecision = libelleTypeDecision;
  readonly SCORE_MAX = SCORE_MAX;

  private readonly charts = new Map<ChartKey, ChartJS>();
  private viewReady = false;
  private chartsBuilt = false;

  constructor(
    private readonly reportingService: ReportingArchivageService,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.load();
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.tryBuildCharts();
  }

  ngOnDestroy(): void {
    this.destroyCharts();
  }

  num(value: number | null | undefined): number {
    return value ?? 0;
  }

  hasMapData(map: Record<string, number> | undefined): boolean {
    return this.repartitionEntries(map).length > 0;
  }

  repartitionBarWidth(value: number, map: Record<string, number> | undefined): number {
    if (!map || value <= 0) return 0;
    const max = Math.max(...Object.values(map), 1);
    return Math.round((value / max) * 100);
  }

  hasStatutData(): boolean {
    return this.repartitionEntries(this.resolveStatutMap()).length > 0;
  }

  hasAcceptRefuseData(): boolean {
    const d = this.dashboard;
    if (!d) return false;
    return d.demandesAcceptees > 0 || d.demandesRefusees > 0;
  }

  hasRisqueData(): boolean {
    const d = this.dashboard;
    if (!d) return false;
    return d.prescoringRisqueFaible + d.prescoringRisqueMoyen + d.prescoringRisqueEleve > 0;
  }

  hasScoringZoneData(): boolean {
    return this.zoneScoringEntries().some((z) => z.value > 0);
  }

  formatScore(value: number | null | undefined): string {
    const n = value ?? 0;
    return Number.isInteger(n) ? String(n) : n.toFixed(1);
  }

  zoneScoringEntries(): { key: string; label: string; value: number; pct: number; color: string }[] {
    const map = this.dashboard?.repartitionPrescoringParZone ?? {};
    const entries = SCORING_ZONES.map((z) => ({
      ...z,
      value: this.zoneMapValue(map, z.key),
    }));
    const total = entries.reduce((s, e) => s + e.value, 0);
    return entries.map((e) => ({
      ...e,
      pct: total > 0 ? Math.round((1000 * e.value) / total) / 10 : 0,
    }));
  }

  risqueScoringEntries(): { key: string; label: string; value: number; color: string }[] {
    const d = this.dashboard;
    const values: Record<string, number> = {
      faible: d?.prescoringRisqueFaible ?? 0,
      moyen: d?.prescoringRisqueMoyen ?? 0,
      eleve: d?.prescoringRisqueEleve ?? 0,
    };
    return SCORING_RISQUES.map((r) => ({ ...r, value: values[r.key] ?? 0 }));
  }

  hasPortefeuilleData(): boolean {
    const d = this.dashboard;
    if (!d) return false;
    return d.demandesEnCoursAnalyse > 0 || d.demandesCloturees > 0;
  }

  repartitionEntries(map: Record<string, number> | undefined): { key: string; value: number }[] {
    if (!map) return [];
    return Object.entries(map)
      .filter(([, v]) => v > 0)
      .map(([key, value]) => ({ key, value }))
      .sort((a, b) => b.value - a.value);
  }

  asApiDate(value: unknown): Date | null {
    if (value == null || value === '') return null;
    if (Array.isArray(value)) {
      const [y, m, d, h = 0, min = 0, s = 0, nano = 0] = value as number[];
      if (y == null || m == null || d == null) return null;
      return new Date(y, m - 1, d, h, min, s, Math.floor(nano / 1_000_000));
    }
    if (typeof value === 'string') {
      const parsed = new Date(value);
      return Number.isNaN(parsed.getTime()) ? null : parsed;
    }
    if (typeof value === 'number') {
      const parsed = new Date(value);
      return Number.isNaN(parsed.getTime()) ? null : parsed;
    }
    return null;
  }

  private resolveStatutMap(): Record<string, number> {
    const map = this.dashboard?.repartitionStatuts ?? {};
    if (Object.keys(map).length > 0) return map;
    const d = this.dashboard;
    if (!d) return {};
    const fallback: Record<string, number> = {};
    if (d.demandesEnCoursAnalyse > 0) fallback['En cours analyse'] = d.demandesEnCoursAnalyse;
    if (d.demandesAcceptees > 0) fallback['Acceptée'] = d.demandesAcceptees;
    if (d.demandesRefusees > 0) fallback['Refusée'] = d.demandesRefusees;
    if (d.demandesCloturees > 0) fallback['Clôturée'] = d.demandesCloturees;
    return fallback;
  }

  private load(): void {
    this.loading = true;
    this.errorMessage = '';
    this.destroyCharts();
    this.reportingService.getDashboard().subscribe({
      next: (dash) => {
        this.dashboard = dash;
        this.loading = false;
        this.chartsBuilt = false;
        this.cdr.detectChanges();
        this.tryBuildCharts();
      },
      error: (err) => this.handleError(err),
    });
  }

  private destroyCharts(): void {
    for (const chart of this.charts.values()) chart.destroy();
    this.charts.clear();
    this.chartsBuilt = false;
  }

  private tryBuildCharts(attempt = 0): void {
    if (!this.dashboard || this.loading || !this.viewReady || this.chartsBuilt) return;
    if (!this.actionsBarCanvas?.nativeElement || !this.scoringZoneCanvas?.nativeElement) {
      if (attempt < 20) window.setTimeout(() => this.tryBuildCharts(attempt + 1), 50);
      return;
    }
    this.buildActionsBar();
    this.buildDecisionsDoughnut();
    this.buildScoringZoneDoughnut();
    this.buildScoringRisquePolar();
    this.buildScoringGauge();
    this.buildEvolutionLine();
    this.buildStatutDoughnut();
    this.buildBanqueHorizontalBar();
    this.buildCommercantBar();
    this.buildAcceptRefusePie();
    this.buildPortefeuilleDoughnut();
    this.buildActiviteBar();
    this.chartsBuilt = true;
  }

  private palette(i: number): string {
    return PALETTE[i % PALETTE.length];
  }

  private zoneColor(zone: string): string {
    return ZONE_COLORS[zone.toLowerCase()] ?? this.palette(0);
  }

  private statutColor(label: string): string {
    return STATUT_COLORS[label] ?? UIB_MUTED;
  }

  private titlePlugin(text: string) {
    return {
      display: true,
      text,
      color: '#0f1824',
      font: { size: 13, weight: 'bold' as const },
      padding: { bottom: 8 },
    };
  }

  private legendBottom() {
    return {
      position: 'bottom' as const,
      labels: { usePointStyle: true, padding: 8, font: { size: 10 }, color: UIB_INK },
    };
  }

  private setChart(key: ChartKey, canvas: HTMLCanvasElement | undefined, config: object): void {
    if (!canvas) return;
    this.charts.get(key)?.destroy();
    this.charts.set(key, new ChartJS(canvas, config as ConstructorParameters<typeof ChartJS>[1]));
  }

  private buildActionsBar(): void {
    const entries = this.repartitionEntries(this.dashboard?.repartitionActionsParType);
    if (entries.length === 0) return;
    this.setChart('actions', this.actionsBarCanvas?.nativeElement, {
      type: 'bar',
      data: {
        labels: entries.map((e) => libelleTypeActionDemande(e.key)),
        datasets: [{
          label: 'Actions',
          data: entries.map((e) => e.value),
          backgroundColor: UIB_RED,
          borderRadius: 6,
          borderSkipped: false,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          title: this.titlePlugin('Actions sur les demandes'),
        },
        scales: {
          x: { grid: { display: false }, ticks: { color: UIB_SLATE, font: { size: 10 }, maxRotation: 45 } },
          y: { beginAtZero: true, ticks: { stepSize: 1, color: UIB_SLATE }, grid: { color: '#f1f5f9' } },
        },
      },
    });
  }

  private buildDecisionsDoughnut(): void {
    const entries = this.repartitionEntries(this.dashboard?.repartitionDecisionsParType);
    if (entries.length === 0) return;
    this.setChart('decisions', this.decisionsDoughnutCanvas?.nativeElement, {
      type: 'doughnut',
      data: {
        labels: entries.map((e) => libelleTypeDecision(e.key)),
        datasets: [{
          data: entries.map((e) => e.value),
          backgroundColor: entries.map((_, i) => this.palette(i)),
          borderWidth: 2,
          borderColor: '#fff',
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '58%',
        plugins: {
          legend: this.legendBottom(),
          title: this.titlePlugin('Décisions financement'),
        },
      },
    });
  }

  private zoneMapValue(map: Record<string, number>, zone: string): number {
    const entry = Object.entries(map).find(([k]) => k.toLowerCase() === zone);
    return entry ? entry[1] : 0;
  }

  private buildScoringZoneDoughnut(): void {
    const entries = this.zoneScoringEntries().filter((e) => e.value > 0);
    if (entries.length === 0) return;
    const pctValues = entries.map((e) => e.pct);
    this.setChart('scoringZone', this.scoringZoneCanvas?.nativeElement, {
      type: 'doughnut',
      data: {
        labels: entries.map((e) => e.label),
        datasets: [{
          data: entries.map((e) => e.value),
          backgroundColor: entries.map((e) => e.color),
          borderWidth: 2,
          borderColor: '#fff',
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '62%',
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: (ctx: { label?: string; parsed?: number; dataIndex?: number }) => {
                const pct = pctValues[ctx.dataIndex ?? 0] ?? 0;
                return ` ${ctx.label}: ${ctx.parsed ?? 0} (${pct}%)`;
              },
            },
          },
        },
      },
      plugins: [{
        id: 'zonePctLabels',
        afterDraw(chart: unknown) {
          const c = chart as {
            ctx: CanvasRenderingContext2D;
            getDatasetMeta: (i: number) => { data: Array<{ x: number; y: number; startAngle?: number; endAngle?: number; outerRadius?: number }> };
          };
          const { ctx } = c;
          const meta = c.getDatasetMeta(0);
          meta.data.forEach((arc, idx) => {
            const pct = pctValues[idx];
            if (pct == null || pct <= 0) return;
            const angle = ((arc.startAngle ?? 0) + (arc.endAngle ?? 0)) / 2;
            const radius = ((arc.outerRadius ?? 0) + (arc.outerRadius ?? 0) * 0.62) / 2;
            const x = arc.x + Math.cos(angle) * radius * 0.85;
            const y = arc.y + Math.sin(angle) * radius * 0.85;
            ctx.save();
            ctx.fillStyle = '#fff';
            ctx.font = 'bold 11px system-ui, sans-serif';
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText(`${pct}%`, x, y);
            ctx.restore();
          });
        },
      }],
    });
  }

  private buildScoringRisquePolar(): void {
    const entries = this.risqueScoringEntries();
    this.setChart('scoringRisque', this.scoringRisqueCanvas?.nativeElement, {
      type: 'polarArea',
      data: {
        labels: ['Faible', 'Moyen', 'Élevé'],
        datasets: [{
          data: entries.map((e) => e.value),
          backgroundColor: [
            'rgba(22, 163, 74, 0.7)',
            'rgba(245, 158, 11, 0.7)',
            'rgba(211, 18, 31, 0.7)',
          ],
          borderColor: [UIB_GREEN, UIB_AMBER, UIB_RED],
          borderWidth: 2,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          r: {
            beginAtZero: true,
            max: 100,
            ticks: {
              stepSize: 25,
              color: UIB_SLATE,
              backdropColor: 'transparent',
              font: { size: 9 },
            },
            grid: { color: '#e2e8f0' },
            pointLabels: { font: { size: 11, weight: '600' }, color: UIB_INK },
          },
        },
      },
    });
  }

  private buildScoringGauge(): void {
    const d = this.dashboard;
    if (!d) return;
    const score = Math.max(0, Math.min(SCORE_MAX, d.scoreMoyenPrescoring ?? 0));
    const rest = SCORE_MAX - score;
    this.setChart('scoringGauge', this.scoringGaugeCanvas?.nativeElement, {
      type: 'doughnut',
      data: {
        datasets: [{
          data: [score, rest],
          backgroundColor: [UIB_BLUE, '#e2e8f0'],
          borderWidth: 0,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        circumference: 180,
        rotation: 270,
        cutout: '78%',
        plugins: { legend: { display: false }, tooltip: { enabled: false } },
      },
    });
  }

  private formatShortDate(iso: string): string {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' });
  }

  private buildEvolutionLine(): void {
    const d = this.dashboard;
    if (!d) return;
    const entries = Object.entries(d.evolutionDemandesParJour ?? {});
    const labels = entries.length > 0 ? entries.map(([day]) => this.formatShortDate(day)) : ['—'];
    const data = entries.length > 0 ? entries.map(([, v]) => v) : [0];
    this.setChart('evolution', this.evolutionLineCanvas?.nativeElement, {
      type: 'line',
      data: {
        labels,
        datasets: [{
          label: 'Créations',
          data,
          borderColor: UIB_BLUE,
          backgroundColor: 'rgba(37, 99, 235, 0.12)',
          fill: true,
          tension: 0.35,
          pointRadius: 3,
          pointBackgroundColor: UIB_BLUE,
          pointBorderColor: '#fff',
          pointBorderWidth: 2,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          title: this.titlePlugin('Évolution des demandes (30 jours)'),
        },
        scales: {
          x: { grid: { display: false }, ticks: { maxTicksLimit: 8, color: UIB_SLATE, font: { size: 10 } } },
          y: { beginAtZero: true, ticks: { stepSize: 1, color: UIB_SLATE }, grid: { color: '#f1f5f9' } },
        },
      },
    });
  }

  private buildStatutDoughnut(): void {
    const entries = this.repartitionEntries(this.resolveStatutMap());
    if (entries.length === 0) return;
    this.setChart('statuts', this.statutDoughnutCanvas?.nativeElement, {
      type: 'doughnut',
      data: {
        labels: entries.map((e) => e.key),
        datasets: [{
          data: entries.map((e) => e.value),
          backgroundColor: entries.map((e) => this.statutColor(e.key)),
          borderWidth: 2,
          borderColor: '#fff',
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '58%',
        plugins: {
          legend: this.legendBottom(),
          title: this.titlePlugin('Répartition par statut'),
        },
      },
    });
  }

  private buildBanqueHorizontalBar(): void {
    const map = this.dashboard?.tauxAcceptationParBanque ?? {};
    const labels = Object.keys(map);
    const data = Object.values(map);
    if (labels.length === 0) return;
    this.setChart('banque', this.banqueBarCanvas?.nativeElement, {
      type: 'bar',
      data: {
        labels,
        datasets: [{
          label: 'Taux %',
          data,
          backgroundColor: UIB_PURPLE,
          borderRadius: 6,
          borderSkipped: false,
        }],
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          title: this.titlePlugin("Taux d'acceptation par banque"),
        },
        scales: {
          x: {
            beginAtZero: true,
            max: 100,
            ticks: { callback: (v: string | number) => `${v} %`, color: UIB_SLATE },
            grid: { color: '#f1f5f9' },
          },
          y: { grid: { display: false }, ticks: { color: UIB_SLATE, font: { size: 10 } } },
        },
      },
    });
  }

  private buildCommercantBar(): void {
    const entries = this.repartitionEntries(this.dashboard?.demandesParCommercant);
    if (entries.length === 0) return;
    this.setChart('commercant', this.commercantBarCanvas?.nativeElement, {
      type: 'bar',
      data: {
        labels: entries.map((e) => e.key),
        datasets: [{
          label: 'Demandes',
          data: entries.map((e) => e.value),
          backgroundColor: UIB_BLUE,
          borderRadius: 6,
          borderSkipped: false,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          title: this.titlePlugin('Demandes par commerçant'),
        },
        scales: {
          x: { grid: { display: false }, ticks: { color: UIB_SLATE, font: { size: 9 }, maxRotation: 45 } },
          y: { beginAtZero: true, ticks: { stepSize: 1, color: UIB_SLATE }, grid: { color: '#f1f5f9' } },
        },
      },
    });
  }

  private buildAcceptRefusePie(): void {
    const d = this.dashboard;
    if (!d || (d.demandesAcceptees <= 0 && d.demandesRefusees <= 0)) return;
    this.setChart('acceptRefuse', this.acceptRefusePieCanvas?.nativeElement, {
      type: 'pie',
      data: {
        labels: ['Acceptées', 'Refusées'],
        datasets: [{
          data: [d.demandesAcceptees, d.demandesRefusees],
          backgroundColor: [UIB_GREEN, UIB_RED],
          borderWidth: 2,
          borderColor: '#fff',
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: this.legendBottom(),
          title: this.titlePlugin('Acceptées vs Refusées'),
        },
      },
    });
  }

  private buildPortefeuilleDoughnut(): void {
    const d = this.dashboard;
    if (!d || !this.hasPortefeuilleData()) return;
    this.setChart('portefeuille', this.portefeuilleDoughnutCanvas?.nativeElement, {
      type: 'doughnut',
      data: {
        labels: ['En cours analyse', 'Clôturées'],
        datasets: [{
          data: [d.demandesEnCoursAnalyse, d.demandesCloturees],
          backgroundColor: [UIB_AMBER, UIB_INK],
          borderWidth: 2,
          borderColor: '#fff',
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '58%',
        plugins: {
          legend: this.legendBottom(),
          title: this.titlePlugin('État du portefeuille'),
        },
      },
    });
  }

  private buildActiviteBar(): void {
    const d = this.dashboard;
    if (!d) return;
    this.setChart('activite', this.activiteBarCanvas?.nativeElement, {
      type: 'bar',
      data: {
        labels: ['Ce mois', 'Total'],
        datasets: [{
          label: 'Demandes',
          data: [d.demandesCeMois, d.demandesTotal],
          backgroundColor: [UIB_RED, UIB_INK],
          borderRadius: 6,
          borderSkipped: false,
        }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          title: this.titlePlugin('Activité — ce mois vs total'),
        },
        scales: {
          x: { grid: { display: false }, ticks: { color: UIB_SLATE } },
          y: { beginAtZero: true, ticks: { stepSize: 1, color: UIB_SLATE }, grid: { color: '#f1f5f9' } },
        },
      },
    });
  }

  private handleError(err: unknown): void {
    this.loading = false;
    this.dashboard = null;
    if (err instanceof Error && err.message.includes('Token JWT')) {
      this.errorMessage = 'Session expirée — reconnectez-vous.';
      return;
    }
    const body = (err as { error?: { message?: string } })?.error;
    this.errorMessage =
      body?.message ??
      'Impossible de charger le tableau de bord (vérifiez reporting-archivage sur le port 8083).';
  }
}
