import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatChipsModule } from '@angular/material/chips';
import { StatsService } from '../stats.service';
import { ComputeRequest, ComputeResponse } from '../models';
import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

@Component({
  selector: 'app-calculator',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatCardModule, MatFormFieldModule, MatInputModule,
    MatCheckboxModule, MatButtonModule, MatDividerModule, MatChipsModule,
  ],
  templateUrl: './calculator.component.html',
  styleUrl: './calculator.component.scss',
})
export class CalculatorComponent implements OnInit {
  private stats = inject(StatsService);
  private router = inject(Router);

  form: ComputeRequest = {
    attacks: 10,
    hitOn: 3,
    woundOn: 4,
    saveOn: 3,
    damage: 1,
    critThreshold: 6,
    sustainedHits: 0,
    twinLinked: false,
    lethalHits: false,
    devastatingWounds: false,
    antiTarget: null,
    antiThreshold: 4,
  };

  result: ComputeResponse | null = null;
  loading = false;
  error: string | null = null;
  private chart: Chart | null = null;

  ngOnInit() {
    const state = this.router.getCurrentNavigation()?.extras.state ?? history.state;
    if (state?.attacks) this.prefill(state as Partial<ComputeRequest>);
  }

  get stdDev(): number {
    return this.result ? Math.sqrt(this.result.variance) : 0;
  }

  compute() {
    this.loading = true;
    this.error = null;
    const req = { ...this.form, antiTarget: this.form.antiTarget || null };
    this.stats.compute(req).subscribe({
      next: res => {
        this.result = res;
        this.loading = false;
        setTimeout(() => this.renderChart(), 0);
      },
      error: () => {
        this.error = 'Erreur lors du calcul. Vérifiez les paramètres.';
        this.loading = false;
      },
    });
  }

  prefill(params: Partial<ComputeRequest>) {
    this.form = { ...this.form, ...params };
  }

  private renderChart() {
    if (!this.result) return;

    const sorted = Object.entries(this.result.pmf)
      .map(([k, v]) => ({ x: Number(k), y: v }))
      .sort((a, b) => a.x - b.x);

    const labels = sorted.map(e => String(e.x));
    const data = sorted.map(e => +(e.y * 100).toFixed(2));

    const canvas = document.getElementById('dmgChart') as HTMLCanvasElement;
    if (!canvas) return;

    if (this.chart) this.chart.destroy();

    this.chart = new Chart(canvas, {
      type: 'bar',
      data: {
        labels,
        datasets: [{
          label: 'Probabilité (%)',
          data,
          backgroundColor: 'rgba(99, 102, 241, 0.75)',
          borderColor: 'rgba(99, 102, 241, 1)',
          borderWidth: 1,
          borderRadius: 4,
        }],
      },
      options: {
        responsive: true,
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: ctx => `${(ctx.parsed.y ?? 0).toFixed(2)}%`,
            },
          },
        },
        scales: {
          x: { title: { display: true, text: 'Dégâts' } },
          y: { title: { display: true, text: '%' }, beginAtZero: true },
        },
      },
    });
  }
}
