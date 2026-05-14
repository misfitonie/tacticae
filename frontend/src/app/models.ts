export interface ComputeRequest {
  attacks: number;
  hitOn: number;
  woundOn: number;
  saveOn: number;
  damage: number;
  critThreshold: number;
  sustainedHits: number;
  twinLinked: boolean;
  lethalHits: boolean;
  devastatingWounds: boolean;
  antiTarget: string | null;
  antiThreshold: number;
}

export interface ComputeResponse {
  mean: number;
  variance: number;
  pmf: Record<string, number>;
}

export interface ParsedWeapon {
  name: string;
  count: number;
  range: string;   // "Melee" ou portée ex. "24\""
  attacks: string;
  skill: number;
  strength: number;
  ap: number;
  damage: string;
  keywords: string[];
}

export interface ParsedUnit {
  name: string;
  count: number;
  toughness: number;
  wounds: number;
  save: number;
  invSave: number;  // 7 = aucune save invulnérable
  weapons: ParsedWeapon[];
}

export interface ArmyList {
  name: string;
  factionName: string;
  units: ParsedUnit[];
}
