export type ThreeWorldPhase = 'BOOT' | 'READY' | 'FAILED' | 'SLEEPING';

export type ThreeWorldStatus = {
  phase: ThreeWorldPhase;
  message: string;
};

export type ThreeWorldMetrics = {
  fps: number;
  frameTimeMs: number;
  drawCalls: number;
  triangles: number;
  geometries: number;
  textures: number;
  quality: 'LOW' | 'BALANCED' | 'HIGH';
};

export type ThreeWorldRuntimeOptions = {
  parent: HTMLElement;
  reducedMotion: boolean;
  onStatus: (status: ThreeWorldStatus) => void;
  onMetrics: (metrics: ThreeWorldMetrics) => void;
};

export type ThreeWorldRuntimeHandle = {
  setReducedMotion: (reducedMotion: boolean) => void;
  destroy: () => void;
};
