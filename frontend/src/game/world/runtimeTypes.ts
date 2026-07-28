import type { WorldQualityTier } from './quality';

export type WorldRuntimePhase = 'BOOT' | 'LOADING' | 'READY' | 'FAILED' | 'SLEEPING';

export type WorldRuntimeStatus = {
  phase: WorldRuntimePhase;
  progress: number;
  message: string;
};

export type WorldRuntimeMetrics = {
  fps: number;
  frameTimeMs: number;
  simulationHz: number;
  renderedObjects: number;
  worldScreens: number;
  regionCount: number;
  droppedSimulationMs: number;
  renderer: 'WEBGL' | 'CANVAS';
  quality: WorldQualityTier;
};

export type WorldRuntimeHandle = {
  destroy: () => void;
  setReducedMotion: (reduced: boolean) => void;
};
