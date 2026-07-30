import type { InputAction } from './input/UnifiedInputState';

export type CombatFeelSkill = {
  action: Extract<InputAction, 'ATTACK' | 'SKILL_1' | 'SKILL_2' | 'SKILL_3' | 'SKILL_4'>;
  label: string;
  cooldownMs: number;
  range: number;
  damage: number;
  color: number;
  area: boolean;
};

export const COMBAT_FEEL_SKILLS: readonly CombatFeelSkill[] = [
  {
    action: 'ATTACK',
    label: 'ATK',
    cooldownMs: 460,
    range: 240,
    damage: 18,
    color: 0xffd166,
    area: false,
  },
  {
    action: 'SKILL_1',
    label: '1',
    cooldownMs: 1400,
    range: 270,
    damage: 34,
    color: 0x7eeaff,
    area: false,
  },
  {
    action: 'SKILL_2',
    label: '2',
    cooldownMs: 2400,
    range: 230,
    damage: 22,
    color: 0x45f0b5,
    area: true,
  },
  {
    action: 'SKILL_3',
    label: '3',
    cooldownMs: 3200,
    range: 340,
    damage: 48,
    color: 0xff8f70,
    area: false,
  },
  {
    action: 'SKILL_4',
    label: '4',
    cooldownMs: 5200,
    range: 310,
    damage: 30,
    color: 0xc39bff,
    area: true,
  },
] as const;

export type CombatTargetPoint = {
  id: string;
  x: number;
  y: number;
  active: boolean;
};

export function newlyPressedActions(
  current: ReadonlySet<InputAction>,
  previous: ReadonlySet<InputAction>,
) {
  return new Set([...current].filter((action) => !previous.has(action)));
}

export function isSkillReady(
  action: CombatFeelSkill['action'],
  now: number,
  readyAt: ReadonlyMap<InputAction, number>,
) {
  return now >= (readyAt.get(action) ?? 0);
}

export function selectTargets(
  origin: { x: number; y: number },
  candidates: readonly CombatTargetPoint[],
  range: number,
  area: boolean,
  facingX = 0,
) {
  const inRange = candidates
    .filter((target) => target.active)
    .map((target) => ({
      target,
      distance: Math.hypot(target.x - origin.x, target.y - origin.y),
    }))
    .filter(({ distance }) => distance <= range)
    .filter(({ target }) => area || facingX === 0 || (target.x - origin.x) * facingX >= 0)
    .sort((left, right) => left.distance - right.distance
      || left.target.id.localeCompare(right.target.id));
  return area ? inRange.map(({ target }) => target) : inRange.slice(0, 1).map(({ target }) => target);
}
