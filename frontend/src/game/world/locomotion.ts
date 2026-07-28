export type LocomotionState = 'IDLE' | 'WALK' | 'RUN';

export const CORE_RAIDER_FRAME = {
  width: 444,
  height: 444,
  pivotX: 222,
  groundY: 412,
} as const;

export const CORE_RAIDER_ANIMATIONS = {
  idle: {
    key: 'core-raider-idle',
    frames: [0, 1],
    frameRate: 3,
    repeat: -1,
  },
  walk: {
    key: 'core-raider-walk',
    frames: [2, 3, 4, 5],
    frameRate: 8,
    repeat: -1,
  },
  run: {
    key: 'core-raider-run',
    frames: [4, 5, 6, 3],
    frameRate: 10,
    repeat: -1,
  },
  dodgeVisual: {
    key: 'core-raider-dodge-visual',
    frames: [6, 7, 6],
    frameRate: 12,
    repeat: 0,
  },
} as const;

const MOVEMENT_DEAD_ZONE = 0.08;
const RUN_THRESHOLD = 0.72;

export function selectLocomotionState(moveX: number, moveY: number): LocomotionState {
  const magnitude = Math.hypot(moveX, moveY);
  if (magnitude < MOVEMENT_DEAD_ZONE) return 'IDLE';
  if (magnitude < RUN_THRESHOLD) return 'WALK';
  return 'RUN';
}

export function animationKeyFor(state: LocomotionState) {
  if (state === 'WALK') return CORE_RAIDER_ANIMATIONS.walk.key;
  if (state === 'RUN') return CORE_RAIDER_ANIMATIONS.run.key;
  return CORE_RAIDER_ANIMATIONS.idle.key;
}
