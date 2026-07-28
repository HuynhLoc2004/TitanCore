import Phaser from 'phaser';
import type { AnimationProofHandle, AnimationProofMetrics } from '../types';
import { AnimationProofScene } from './AnimationProofScene';

type CreateAnimationProofOptions = {
  parent: HTMLElement;
  reducedMotion: boolean;
  onMetrics: (metrics: AnimationProofMetrics) => void;
  onReady: () => void;
  onFailure: () => void;
};

export function createAnimationProof({
  parent,
  reducedMotion,
  onMetrics,
  onReady,
  onFailure,
}: CreateAnimationProofOptions): AnimationProofHandle {
  const game = new Phaser.Game({
    type: Phaser.AUTO,
    parent,
    width: 1280,
    height: 720,
    backgroundColor: '#090b18',
    transparent: false,
    antialias: true,
    pixelArt: false,
    roundPixels: true,
    render: {
      antialias: true,
      powerPreference: 'high-performance',
    },
    scale: {
      mode: Phaser.Scale.FIT,
      autoCenter: Phaser.Scale.CENTER_BOTH,
      width: 1280,
      height: 720,
    },
    fps: {
      target: 60,
      min: 30,
      smoothStep: true,
    },
    callbacks: {
      postBoot: (bootedGame) => {
        bootedGame.registry.set('reducedMotion', reducedMotion);
        bootedGame.registry.set('onMetrics', onMetrics);
        bootedGame.registry.set('onReady', onReady);
        bootedGame.registry.set('onFailure', onFailure);
        bootedGame.registry.set('qualityTier', parent.clientWidth < 900 ? 'LOW' : 'STANDARD');
        bootedGame.scene.add('AnimationProof', AnimationProofScene, true);
      },
    },
  });
  return {
    destroy: () => game.destroy(true),
    setReducedMotion: (reduced) => game.registry.set('reducedMotion', reduced),
  };
}
