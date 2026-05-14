import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { ArmiesService } from '../armies.service';
import { ArmyList, ParsedUnit, ParsedWeapon } from '../models';

@Component({
  selector: 'app-import',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule, MatButtonModule, MatIconModule,
    MatExpansionModule, MatChipsModule, MatDividerModule,
  ],
  templateUrl: './import.component.html',
  styleUrl: './import.component.scss',
})
export class ImportComponent {
  private armies = inject(ArmiesService);
  private router = inject(Router);

  army: ArmyList | null = null;
  loading = false;
  error: string | null = null;
  dragging = false;

  onFileChange(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (file) this.upload(file);
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    this.dragging = false;
    const file = event.dataTransfer?.files[0];
    if (file) this.upload(file);
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    this.dragging = true;
  }

  onDragLeave() {
    this.dragging = false;
  }

  private upload(file: File) {
    if (!file.name.endsWith('.rosz')) {
      this.error = 'Format non supporté. Importez un fichier .rosz BattleScribe.';
      return;
    }
    this.loading = true;
    this.error = null;
    this.army = null;
    this.armies.importRosz(file).subscribe({
      next: army => { this.army = army; this.loading = false; },
      error: () => { this.error = 'Erreur lors du parsing du fichier.'; this.loading = false; },
    });
  }

  sendToCalc(unit: ParsedUnit, weapon: ParsedWeapon) {
    const hitOn = weapon.skill;
    const woundOn = this.woundOn(weapon.strength, unit.toughness);
    const saveOn = Math.min(7, unit.save + Math.abs(weapon.ap));
    const damage = parseInt(weapon.damage) || 1;
    const kw = weapon.keywords.map(k => k.toLowerCase());

    this.router.navigate(['/calculator'], { state: {
      attacks: weapon.count,
      hitOn,
      woundOn,
      saveOn,
      damage,
      critThreshold: 6,
      sustainedHits: this.extractSustained(kw),
      twinLinked: kw.some(k => k.includes('twin-linked') || k.includes('twin linked')),
      lethalHits: kw.some(k => k.includes('lethal hits')),
      devastatingWounds: kw.some(k => k.includes('devastating wounds')),
      antiTarget: null,
      antiThreshold: 4,
    }});
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
}
