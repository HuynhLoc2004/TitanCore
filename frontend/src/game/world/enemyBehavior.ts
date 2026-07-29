export type EnemyBehaviorState = 'PATROL' | 'CHASE' | 'TELEGRAPH' | 'RECOVER' | 'DEFEATED';

export type EnemyBehavior = {
  state: EnemyBehaviorState;
  stateUntil: number;
  patrolDirection: -1 | 1;
};

export const SPROUT_BEHAVIOR = {
  aggroRange: 430,
  attackRange: 118,
  leashRange: 620,
  patrolRadius: 120,
  patrolSpeed: 38,
  chaseSpeed: 82,
  telegraphMs: 680,
  recoverMs: 920,
  damage: 12,
} as const;

export function advanceEnemyBehavior(
  behavior: EnemyBehavior,
  now: number,
  distanceToHero: number,
  distanceFromSpawn: number,
): { behavior: EnemyBehavior; attack: boolean } {
  if (behavior.state === 'DEFEATED') return { behavior, attack: false };
  if (behavior.state === 'PATROL') {
    if (distanceToHero <= SPROUT_BEHAVIOR.aggroRange) {
      return { behavior: { ...behavior, state: 'CHASE' }, attack: false };
    }
    if (distanceFromSpawn >= SPROUT_BEHAVIOR.patrolRadius) {
      return {
        behavior: { ...behavior, patrolDirection: behavior.patrolDirection === 1 ? -1 : 1 },
        attack: false,
      };
    }
  }
  if (behavior.state === 'CHASE') {
    if (distanceFromSpawn > SPROUT_BEHAVIOR.leashRange) {
      return { behavior: { ...behavior, state: 'PATROL' }, attack: false };
    }
    if (distanceToHero <= SPROUT_BEHAVIOR.attackRange) {
      return {
        behavior: {
          ...behavior,
          state: 'TELEGRAPH',
          stateUntil: now + SPROUT_BEHAVIOR.telegraphMs,
        },
        attack: false,
      };
    }
  }
  if (behavior.state === 'TELEGRAPH' && now >= behavior.stateUntil) {
    return {
      behavior: {
        ...behavior,
        state: 'RECOVER',
        stateUntil: now + SPROUT_BEHAVIOR.recoverMs,
      },
      attack: true,
    };
  }
  if (behavior.state === 'RECOVER' && now >= behavior.stateUntil) {
    return { behavior: { ...behavior, state: 'CHASE' }, attack: false };
  }
  return { behavior, attack: false };
}
