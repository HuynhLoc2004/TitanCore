import type { EnemyBehaviorTuning } from './enemyBehavior';

export type EnemyArchetype = 'SPROUT_SCOUT' | 'SPROUT_BRUISER' | 'SPROUT_WARDEN';
export type EntityBehaviorProfile = 'HERO' | EnemyArchetype;

export type EnemyArchetypeDefinition = {
  maxHp: number;
  damage: number;
  tint: number;
  behavior: EnemyBehaviorTuning;
};

export const ENEMY_ARCHETYPES: Record<EnemyArchetype, EnemyArchetypeDefinition> = {
  SPROUT_SCOUT: {
    maxHp: 68,
    damage: 8,
    tint: 0xb9fff0,
    behavior: {
      aggroRange: 480,
      attackRange: 92,
      leashRange: 680,
      patrolRadius: 150,
      patrolSpeed: 54,
      chaseSpeed: 112,
      telegraphMs: 520,
      recoverMs: 680,
    },
  },
  SPROUT_BRUISER: {
    maxHp: 168,
    damage: 18,
    tint: 0xffc078,
    behavior: {
      aggroRange: 390,
      attackRange: 132,
      leashRange: 600,
      patrolRadius: 100,
      patrolSpeed: 28,
      chaseSpeed: 66,
      telegraphMs: 880,
      recoverMs: 1200,
    },
  },
  SPROUT_WARDEN: {
    maxHp: 104,
    damage: 11,
    tint: 0xc6b5ff,
    behavior: {
      aggroRange: 520,
      attackRange: 255,
      leashRange: 720,
      patrolRadius: 130,
      patrolSpeed: 34,
      chaseSpeed: 72,
      telegraphMs: 760,
      recoverMs: 1080,
    },
  },
};
