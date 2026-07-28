import Phaser from 'phaser';
import { parseWorldManifest } from '../manifest';
import type { WorldRuntimeStatus } from '../runtimeTypes';

type StatusCallback = (status: WorldRuntimeStatus) => void;
type FailureCallback = () => void;

export class BootScene extends Phaser.Scene {
  private manifestFailed = false;

  constructor() {
    super({ key: 'WorldBoot', active: false });
  }

  preload() {
    this.emitStatus({ phase: 'BOOT', progress: 0, message: 'Reading world manifest' });
    this.load.once(Phaser.Loader.Events.FILE_LOAD_ERROR, () => {
      this.manifestFailed = true;
      this.fail();
    });
    this.load.json('world-manifest', this.registry.get('manifestUrl') as string);
  }

  create() {
    if (this.manifestFailed) {
      return;
    }
    try {
      const manifest = parseWorldManifest(this.cache.json.get('world-manifest'));
      this.cache.json.remove('world-manifest');
      this.registry.set('worldManifest', manifest);
      this.scene.start('WorldPreload');
    } catch {
      this.fail();
    }
  }

  private emitStatus(status: WorldRuntimeStatus) {
    const onStatus = this.registry.get('onStatus') as StatusCallback | undefined;
    onStatus?.(status);
  }

  private fail() {
    this.emitStatus({ phase: 'FAILED', progress: 0, message: 'World manifest rejected' });
    const onFailure = this.registry.get('onFailure') as FailureCallback | undefined;
    onFailure?.();
  }
}
