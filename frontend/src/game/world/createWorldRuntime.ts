import Phaser from 'phaser';
import { selectWorldQuality } from './quality';
import type {
  WorldRuntimeHandle,
  WorldRuntimeMetrics,
  WorldRuntimeStatus,
} from './runtimeTypes';
import { BootScene } from './scenes/BootScene';
import { LocalWorldScene } from './scenes/LocalWorldScene';
import { PreloadScene } from './scenes/PreloadScene';
import type { UnifiedInputState } from './input/UnifiedInputState';

export type CreateWorldRuntimeOptions = {
  parent: HTMLElement;
  manifestUrl: string;
  reducedMotion: boolean;
  inputState: UnifiedInputState;
  onStatus: (status: WorldRuntimeStatus) => void;
  onMetrics: (metrics: WorldRuntimeMetrics) => void;
  onReady: () => void;
  onFailure: () => void;
};

export function createWorldRuntime({
  parent,
  manifestUrl,
  reducedMotion,
  inputState,
  onStatus,
  onMetrics,
  onReady,
  onFailure,
}: CreateWorldRuntimeOptions): WorldRuntimeHandle {
  const dpr = Math.min(Math.max(window.devicePixelRatio || 1, 1), 2);
  const quality = selectWorldQuality(parent.clientWidth, parent.clientHeight, dpr);
  const game = new Phaser.Game({
    type: Phaser.AUTO,
    parent,
    width: 1280,
    height: 720,
    backgroundColor: '#090b18',
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
    physics: {
      default: 'arcade',
      arcade: {
        gravity: { x: 0, y: 0 },
        debug: false,
      },
    },
    callbacks: {
      postBoot: (bootedGame) => {
        bootedGame.registry.set('manifestUrl', manifestUrl);
        bootedGame.registry.set('reducedMotion', reducedMotion);
        bootedGame.registry.set('inputState', inputState);
        bootedGame.registry.set('qualityTier', quality);
        bootedGame.registry.set('onStatus', onStatus);
        bootedGame.registry.set('onMetrics', onMetrics);
        bootedGame.registry.set('onReady', onReady);
        bootedGame.registry.set('onFailure', onFailure);
        bootedGame.scene.add('WorldBoot', BootScene, false);
        bootedGame.scene.add('WorldPreload', PreloadScene, false);
        bootedGame.scene.add('LocalWorld', LocalWorldScene, false);
        bootedGame.scene.start('WorldBoot');
      },
    },
  });

  return {
    destroy: () => game.destroy(true),
    setReducedMotion: (reduced) => game.registry.set('reducedMotion', reduced),
  };
}
