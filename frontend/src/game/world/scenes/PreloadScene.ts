import Phaser from 'phaser';
import type { WorldManifest } from '../manifest';
import type { WorldRuntimeStatus } from '../runtimeTypes';

type StatusCallback = (status: WorldRuntimeStatus) => void;
type FailureCallback = () => void;

export class PreloadScene extends Phaser.Scene {
  private failed = false;

  constructor() {
    super({ key: 'WorldPreload', active: false });
  }

  preload() {
    const manifest = this.registry.get('worldManifest') as WorldManifest;
    this.emitStatus({ phase: 'LOADING', progress: 0, message: 'Loading critical world assets' });

    this.load.on(Phaser.Loader.Events.PROGRESS, (progress: number) => {
      this.emitStatus({
        phase: 'LOADING',
        progress: Math.round(progress * 100),
        message: 'Loading critical world assets',
      });
    });
    this.load.on(Phaser.Loader.Events.FILE_LOAD_ERROR, (file: Phaser.Loader.File) => {
      const failedAsset = manifest.assets.find((asset) => asset.key === file.key);
      if (failedAsset?.critical && !this.failed) {
        this.failed = true;
        this.emitStatus({
          phase: 'FAILED',
          progress: 0,
          message: 'Critical world asset unavailable',
        });
        const onFailure = this.registry.get('onFailure') as FailureCallback | undefined;
        onFailure?.();
      }
    });

    manifest.assets.forEach((asset) => {
      if (asset.kind === 'IMAGE') {
        this.load.image(asset.key, asset.url);
      } else {
        this.load.spritesheet(asset.key, asset.url, {
          frameWidth: asset.frameWidth,
          frameHeight: asset.frameHeight,
        });
      }
    });
  }

  create() {
    this.load.removeAllListeners();
    if (!this.failed) {
      this.scene.start('LocalWorld');
    }
  }

  private emitStatus(status: WorldRuntimeStatus) {
    const onStatus = this.registry.get('onStatus') as StatusCallback | undefined;
    onStatus?.(status);
  }
}
