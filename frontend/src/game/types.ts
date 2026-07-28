export type AnimationProofMetrics = {
  fps: number;
  frameTimeMs: number;
  renderedObjects: number;
  renderer: 'WEBGL' | 'CANVAS';
};

export type AnimationProofHandle = {
  destroy: () => void;
  setReducedMotion: (reduced: boolean) => void;
};
