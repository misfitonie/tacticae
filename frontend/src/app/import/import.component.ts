import { Component, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { forkJoin, map } from 'rxjs';
import { Chart, registerables } from 'chart.js';
import { ArmiesService } from '../armies.service';
import { StatsService } from '../stats.service';
import { ArmyList, ParsedUnit, ParsedWeapon, ComputeRequest, ComputeResponse } from '../models';

Chart.register(...registerables);

interface WeaponResult {
  weapon: ParsedWeapon;
  result: ComputeResponse;
}

@Component({
  selector: 'app-import',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule, MatButtonModule, MatIconModule, MatChipsModule, MatProgressSpinnerModule, MatTooltipModule,
  ],
  templateUrl: './import.component.html',
  styleUrl: './import.component.scss',
})
export class ImportComponent implements OnDestroy {
  private armies = inject(ArmiesService);
  private stats = inject(StatsService);

  myArmy: ArmyList | null = null;
  opponentArmy: ArmyList | null = null;
  myDragging = false;
  opponentDragging = false;
  myLoading = false;
  opponentLoading = false;
  error: string | null = null;

  selectedAttacker: ParsedUnit | null = null;
  selectedDefender: ParsedUnit | null = null;
  bestWeapon: WeaponResult | null = null;
  computing = false;
  defenderFnp = 0; // 0 = aucune, sinon seuil X+ (3..6)
  readonly fnpOptions = [0, 6, 5, 4, 3];

  defenderKeywords = new Set<string>();
  readonly keywordOptions = [
    'INFANTRY', 'VEHICLE', 'MONSTER', 'CHARACTER',
    'PSYKER', 'FLY', 'CHAOS', 'IMPERIUM',
  ];

  attackerCharged = false;
  attackerHalfRange = false;

  private chart: Chart | null = null;
  private computeToken = 0;

  get stdDev(): number {
    return this.bestWeapon ? Math.sqrt(this.bestWeapon.result.variance) : 0;
  }

  get myGroups(): { unit: ParsedUnit; qty: number }[] {
    return this.groupUnits(this.myArmy?.units ?? []);
  }

  get opponentGroups(): { unit: ParsedUnit; qty: number }[] {
    return this.groupUnits(this.opponentArmy?.units ?? []);
  }

  weaponGroups(weapons: ParsedWeapon[]): { label: string; weapons: ParsedWeapon[] }[] {
    const ranged = weapons.filter(w => w.range !== 'Melee');
    const melee  = weapons.filter(w => w.range === 'Melee');
    const groups: { label: string; weapons: ParsedWeapon[] }[] = [];
    if (ranged.length) groups.push({ label: 'Tir', weapons: ranged });
    if (melee.length)  groups.push({ label: 'Corps à corps', weapons: melee });
    return groups;
  }

  private groupUnits(units: ParsedUnit[]): { unit: ParsedUnit; qty: number }[] {
    const map = new Map<string, { unit: ParsedUnit; qty: number }>();
    for (const unit of units) {
      const existing = map.get(unit.name);
      if (existing) existing.qty++;
      else map.set(unit.name, { unit, qty: 1 });
    }
    return [...map.values()];
  }

  get breakdown(): {
    attacks: number; hits: number; wounds: number; failedSaves: number; damage: number;
    hitOn: number; woundOn: number; saveOn: number; damageStr: string; attacksStr: string;
  } | null {
    if (!this.bestWeapon || !this.selectedDefender) return null;
    const w = this.bestWeapon.weapon;
    const def = this.selectedDefender;
    const attacks = w.count * this.exprMean(w.attacks);
    const hitOn = w.skill;
    const woundOn = this.woundOn(w.strength, def.toughness);
    const armorSave = Math.min(7, def.save + Math.abs(w.ap));
    const saveOn = Math.min(armorSave, def.invSave ?? 7);
    const pHit = (7 - hitOn) / 6;
    const pWound = (7 - woundOn) / 6;
    const pFailSave = saveOn >= 7 ? 1 : (saveOn - 1) / 6;
    const hits = attacks * pHit;
    const wounds = hits * pWound;
    const failedSaves = wounds * pFailSave;
    return { attacks, hits, wounds, failedSaves, damage: this.bestWeapon.result.mean,
             hitOn, woundOn, saveOn, damageStr: w.damage, attacksStr: this.multiplyExpr(w.attacks, w.count) };
  }

  private exprMean(expr: string | undefined | null): number {
    if (!expr) return 1;
    const e = expr.replace(/\s/g, '').toUpperCase();
    const dice = e.match(/^(\d+)?D(\d+)(?:\+(\d+))?$/);
    if (dice) {
      const k = dice[1] ? parseInt(dice[1]) : 1;
      const n = parseInt(dice[2]);
      const mod = dice[3] ? parseInt(dice[3]) : 0;
      return k * (n + 1) / 2 + mod;
    }
    const constant = e.match(/^(\d+)$/);
    if (constant) return parseInt(e);
    return 1;
  }

  ngOnDestroy() {
    this.chart?.destroy();
  }

  onMyFileChange(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) this.upload(file, 'my');
  }

  onMyDrop(event: DragEvent) {
    event.preventDefault();
    this.myDragging = false;
    const file = event.dataTransfer?.files[0];
    if (file) this.upload(file, 'my');
  }

  onMyDragOver(event: DragEvent) { event.preventDefault(); this.myDragging = true; }
  onMyDragLeave() { this.myDragging = false; }

  onOpponentFileChange(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) this.upload(file, 'opponent');
  }

  onOpponentDrop(event: DragEvent) {
    event.preventDefault();
    this.opponentDragging = false;
    const file = event.dataTransfer?.files[0];
    if (file) this.upload(file, 'opponent');
  }

  onOpponentDragOver(event: DragEvent) { event.preventDefault(); this.opponentDragging = true; }
  onOpponentDragLeave() { this.opponentDragging = false; }

  resetMyArmy() {
    this.myArmy = null;
    this.selectedAttacker = null;
    this.bestWeapon = null;
  }

  resetOpponentArmy() {
    this.opponentArmy = null;
    this.selectedDefender = null;
    this.bestWeapon = null;
  }

  setDefenderFnp(value: number) {
    this.defenderFnp = value;
    this.computeIfReady();
  }

  toggleDefenderKeyword(kw: string) {
    if (this.defenderKeywords.has(kw)) this.defenderKeywords.delete(kw);
    else this.defenderKeywords.add(kw);
    this.computeIfReady();
  }

  isDefenderKeywordActive(kw: string): boolean {
    return this.defenderKeywords.has(kw);
  }

  toggleCharged() {
    this.attackerCharged = !this.attackerCharged;
    this.computeIfReady();
  }

  toggleHalfRange() {
    this.attackerHalfRange = !this.attackerHalfRange;
    this.computeIfReady();
  }

  private upload(file: File, target: 'my' | 'opponent') {
    if (!file.name.endsWith('.rosz')) {
      this.error = 'Format non supporté. Importez un fichier .rosz BattleScribe.';
      return;
    }
    this.error = null;
    if (target === 'my') {
      this.myLoading = true;
      this.myArmy = null;
      this.selectedAttacker = null;
    } else {
      this.opponentLoading = true;
      this.opponentArmy = null;
      this.selectedDefender = null;
    }
    this.bestWeapon = null;

    this.armies.importRosz(file).subscribe({
      next: army => {
        if (target === 'my') { this.myArmy = army; this.myLoading = false; }
        else { this.opponentArmy = army; this.opponentLoading = false; }
      },
      error: () => {
        this.error = 'Erreur lors du parsing du fichier.';
        if (target === 'my') this.myLoading = false;
        else this.opponentLoading = false;
      },
    });
  }

  selectUnit(unit: ParsedUnit) {
    if (this.selectedAttacker === unit) {
      this.selectedAttacker = null;
      this.bestWeapon = null;
      return;
    }
    if (this.selectedDefender === unit) {
      this.selectedDefender = null;
      this.bestWeapon = null;
      return;
    }
    if (!this.selectedAttacker) {
      this.selectedAttacker = unit;
    } else if (!this.selectedDefender) {
      this.selectedDefender = unit;
    } else {
      // Both slots filled — replace the one on the same army side as the clicked unit.
      const clickedSide = this.armyOf(unit);
      const attackerSide = this.armyOf(this.selectedAttacker);
      if (clickedSide === attackerSide) this.selectedAttacker = unit;
      else this.selectedDefender = unit;
    }
    this.computeIfReady();
  }

  private armyOf(unit: ParsedUnit): 'my' | 'opponent' | null {
    if (this.myArmy?.units.includes(unit)) return 'my';
    if (this.opponentArmy?.units.includes(unit)) return 'opponent';
    return null;
  }

  private computeIfReady() {
    if (!this.selectedAttacker || !this.selectedDefender) return;

    const token = ++this.computeToken;
    this.computing = true;
    const attacker = this.selectedAttacker;
    const defender = this.selectedDefender;

    if (attacker.weapons.length === 0) { this.computing = false; return; }

    const requests = attacker.weapons.map(weapon => {
      const kw = weapon.keywords.map(k => k.toLowerCase());
      const anti = this.bestMatchingAnti(weapon.keywords);
      const req: ComputeRequest = {
        attacks: this.multiplyExpr(weapon.attacks, weapon.count),
        hitOn: weapon.skill,
        woundOn: this.woundOn(weapon.strength, defender.toughness),
        saveOn: Math.min(Math.min(7, defender.save + Math.abs(weapon.ap)), defender.invSave ?? 7),
        damage: this.normalizeExpr(weapon.damage),
        critThreshold: 6,
        sustainedHits: this.extractSustained(kw),
        twinLinked: kw.some(k => k.includes('twin-linked') || k.includes('twin linked')),
        lethalHits: kw.some(k => k.includes('lethal hits')),
        devastatingWounds: kw.some(k => k.includes('devastating wounds')),
        antiTarget: anti?.target ?? null,
        antiThreshold: anti?.threshold ?? 4,
        targetWounds: defender.wounds,
        feelNoPain: this.defenderFnp,
        torrent: kw.some(k => k.includes('torrent')),
        lance: kw.some(k => k.includes('lance')),
        melta: this.extractNumberKeyword(kw, 'melta'),
        cleave: this.extractNumberKeyword(kw, 'cleave'),
        charged: this.attackerCharged,
        halfRange: this.attackerHalfRange,
        targetUnitSize: defender.count,
      };
      return this.stats.compute(req).pipe(map(result => ({ weapon, result }) as WeaponResult));
    });

    forkJoin(requests).subscribe({
      next: results => {
        if (token !== this.computeToken) return;
        this.bestWeapon = results.reduce((best, curr) =>
          curr.result.mean > best.result.mean ? curr : best
        );
        this.computing = false;
        setTimeout(() => this.renderChart(), 0);
      },
      error: () => { if (token === this.computeToken) this.computing = false; },
    });
  }

  private woundOn(s: number, t: number): number {
    if (s >= t * 2) return 2;
    if (s > t) return 3;
    if (s === t) return 4;
    if (s * 2 <= t) return 6;
    return 5;
  }

  private extractSustained(kw: string[]): number {
    for (const k of kw) {
      const m = k.match(/sustained hits\s*(\d+)/);
      if (m) return parseInt(m[1]);
    }
    return 0;
  }

  // Extrait "Melta X" ou "Cleave X" → renvoie X, 0 si absent.
  private extractNumberKeyword(kw: string[], name: string): number {
    const re = new RegExp(`${name}\\s*(\\d+)`);
    for (const k of kw) {
      const m = k.match(re);
      if (m) return parseInt(m[1]);
    }
    return 0;
  }

  // Parse les "Anti-X N+" du weapon, garde celui dont X correspond aux types
  // sélectionnés sur le défenseur, retourne le meilleur (seuil le plus bas).
  private bestMatchingAnti(weaponKeywords: string[]): { target: string; threshold: number } | null {
    if (this.defenderKeywords.size === 0) return null;
    const matches: { target: string; threshold: number }[] = [];
    for (const raw of weaponKeywords) {
      const m = raw.match(/anti[\s-]+([A-Za-z]+)\s*(\d+)\+?/i);
      if (!m) continue;
      const target = m[1].toUpperCase();
      const threshold = parseInt(m[2]);
      if (this.defenderKeywords.has(target)) matches.push({ target, threshold });
    }
    if (matches.length === 0) return null;
    return matches.reduce((best, curr) => curr.threshold < best.threshold ? curr : best);
  }

  // Normalise une expression de dés ("D6", "D6+1", "3", "2D3+1") pour l'API.
  // Repli sur "1" si non parseable (cas "D6+D3", etc. qu'on ne gère pas encore).
  private normalizeExpr(expr: string | undefined | null): string {
    if (!expr) return '1';
    const e = expr.replace(/\s/g, '').toUpperCase();
    if (/^(\d+)?D\d+(\+\d+)?$/.test(e) || /^\d+$/.test(e)) return e;
    return '1';
  }

  // Multiplie une expression de dés par un scalaire (count d'armes identiques).
  // "D6" × 3 = "3D6", "D6+1" × 3 = "3D6+3", "5" × 4 = "20".
  private multiplyExpr(expr: string | undefined | null, count: number): string {
    const e = this.normalizeExpr(expr);
    if (count <= 0) return '0';
    if (count === 1) return e;
    const dice = e.match(/^(\d+)?D(\d+)(?:\+(\d+))?$/);
    if (dice) {
      const k = dice[1] ? parseInt(dice[1]) : 1;
      const n = parseInt(dice[2]);
      const mod = dice[3] ? parseInt(dice[3]) : 0;
      const newK = k * count;
      const newMod = mod * count;
      return `${newK}D${n}${newMod > 0 ? '+' + newMod : ''}`;
    }
    const constant = e.match(/^(\d+)$/);
    if (constant) return String(parseInt(e) * count);
    return e;
  }

  private renderChart() {
    if (!this.bestWeapon) return;
    const sorted = Object.entries(this.bestWeapon.result.pmf)
      .map(([k, v]) => ({ x: Number(k), y: v }))
      .sort((a, b) => a.x - b.x);

    const canvas = document.getElementById('dmgChart') as HTMLCanvasElement;
    if (!canvas) return;
    if (this.chart) this.chart.destroy();

    this.chart = new Chart(canvas, {
      type: 'bar',
      data: {
        labels: sorted.map(e => String(e.x)),
        datasets: [{
          label: 'Probabilité (%)',
          data: sorted.map(e => +(e.y * 100).toFixed(2)),
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
          tooltip: { callbacks: { label: ctx => `${(ctx.parsed.y ?? 0).toFixed(2)}%` } },
        },
        scales: {
          x: { title: { display: true, text: 'Dégâts' } },
          y: { title: { display: true, text: '%' }, beginAtZero: true },
        },
      },
    });
  }
}
