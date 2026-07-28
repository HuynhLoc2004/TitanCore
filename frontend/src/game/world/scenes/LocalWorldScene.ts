import Phaser from 'phaser';
import { FixedStepClock } from '../fixedStepClock';
import type { WorldManifest } from '../manifest';
import type {
  WorldRuntimeMetrics,
  WorldRuntimeStatus,
} from '../runtimeTypes';
import type { WorldQualityTier } from '../quality';
import type { UnifiedInputState } from '../input/UnifiedInputState';
import { KeyboardInputAdapter } from '../input/KeyboardInputAdapter';
import {
  animationKeyFor,
  CORE_RAIDER_ANIMATIONS,
  CORE_RAIDER_FRAME,
  selectLocomotionState,
} from '../locomotion';

type MetricsCallback = (metrics: WorldRuntimeMetrics) => void;
type StatusCallback = (status: WorldRuntimeStatus) => void;

const LOGICAL_WIDTH = 1280;
const LOGICAL_HEIGHT = 720;

export class LocalWorldScene extends Phaser.Scene {
  private readonly clock = new FixedStepClock();
  private cloudLayer?: Phaser.GameObjects.Container;
  private previousSimulationTime = 0;
  private simulationTime = 0;
  private metricElapsed = 0;
  private metricFrames = 0;
  private metricFrameTime = 0;
  private metricSimulationSteps = 0;
  private metricDroppedMs = 0;
  private hidden = false;
  private visibilityListener?: () => void;
  private readonly blurListener = () => this.inputState?.releaseAll();
  private hero?: Phaser.Physics.Arcade.Sprite;
  private keyboardAdapter?: KeyboardInputAdapter;
  private inputState?: UnifiedInputState;
  private dodgeWasPressed = false;
  private readonly pointerDown = (pointer: Phaser.Input.Pointer) => {
    if (pointer.leftButtonDown()) this.inputState?.setAction('POINTER', 'ATTACK', true);
  };
  private readonly pointerUp = () => this.inputState?.setAction('POINTER', 'ATTACK', false);

  constructor() {
    super({ key: 'LocalWorld', active: false });
  }

  create() {
    const manifest = this.registry.get('worldManifest') as WorldManifest;
    const quality = this.registry.get('qualityTier') as WorldQualityTier;
    this.inputState = this.registry.get('inputState') as UnifiedInputState;
    this.inputState.beginGeneration();
    this.cameras.main.setBounds(0, 0, manifest.world.width, manifest.world.height);
    this.physics.world.setBounds(
      0,
      manifest.navigation.minY,
      manifest.world.width,
      manifest.navigation.maxY - manifest.navigation.minY,
    );

    manifest.layers.forEach((layer) => {
      this.add
        .image(layer.x, layer.y, layer.assetKey)
        .setDisplaySize(layer.width, layer.height)
        .setScrollFactor(layer.scrollFactorX, layer.scrollFactorY)
        .setFlipX(layer.flipX)
        .setDepth(layer.depth);
    });

    this.createAmbient(manifest, quality);
    manifest.entities.forEach((entity) => {
      if (entity.kind === 'HERO') {
        this.hero = this.physics.add
          .sprite(entity.x, entity.y, entity.assetKey, entity.frame)
          .setOrigin(
            CORE_RAIDER_FRAME.pivotX / CORE_RAIDER_FRAME.width,
            CORE_RAIDER_FRAME.groundY / CORE_RAIDER_FRAME.height,
          )
          .setScale(entity.scale)
          .setDepth(entity.depth)
          .setCollideWorldBounds(true);
        const body = this.hero.body as Phaser.Physics.Arcade.Body;
        body.setSize(150, 190).setOffset(146, 220);
      } else {
        this.add
          .sprite(entity.x, entity.y, entity.assetKey, entity.frame)
          .setOrigin(0.5, 1)
          .setScale(entity.scale)
          .setDepth(entity.depth);
      }
    });

    if (this.hero) {
      this.createHeroAnimations(this.hero.texture.key);
      this.hero.play(CORE_RAIDER_ANIMATIONS.idle.key);
      this.cameras.main.startFollow(
        this.hero,
        true,
        manifest.navigation.cameraLerp,
        manifest.navigation.cameraLerp,
        0,
        90,
      );
    }
    this.keyboardAdapter = new KeyboardInputAdapter(this, this.inputState);
    this.input.on(Phaser.Input.Events.POINTER_DOWN, this.pointerDown);
    this.input.on(Phaser.Input.Events.POINTER_UP, this.pointerUp);

    this.visibilityListener = () => this.handleVisibility();
    document.addEventListener('visibilitychange', this.visibilityListener);
    window.addEventListener('blur', this.blurListener);
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => this.disposeScene());
    this.emitStatus({ phase: 'READY', progress: 100, message: 'Local Khu runtime ready' });
    const onReady = this.registry.get('onReady') as (() => void) | undefined;
    onReady?.();
  }

  update(_time: number, delta: number) {
    if (this.hidden) {
      return;
    }
    this.keyboardAdapter?.sample();
    if (this.hero && this.inputState) {
      const pointer = this.input.activePointer;
      this.inputState.setAim(
        'POINTER',
        pointer.worldX - this.hero.x,
        pointer.worldY - this.hero.y,
      );
    }
    const result = this.clock.advance(delta, (stepMs) => {
      this.previousSimulationTime = this.simulationTime;
      this.simulationTime += stepMs;
      this.metricSimulationSteps += 1;
      this.simulateMovement();
    });
    this.metricDroppedMs += result.droppedMs;
    this.renderAmbient(result.alpha);

    this.metricElapsed += delta;
    this.metricFrames += 1;
    this.metricFrameTime += delta;
    if (this.metricElapsed >= 1000) {
      this.emitMetrics();
    }
  }

  private simulateMovement() {
    if (!this.hero || !this.inputState) return;
    const manifest = this.registry.get('worldManifest') as WorldManifest;
    const input = this.inputState.snapshot();
    const dodgePressed = input.actions.has('DODGE');
    const dodgeStarted = dodgePressed && !this.dodgeWasPressed;
    this.dodgeWasPressed = dodgePressed;
    this.hero.setVelocity(
      input.moveX * manifest.navigation.moveSpeed,
      input.moveY * manifest.navigation.moveSpeed,
    );
    if (input.moveX !== 0) this.hero.setFlipX(input.moveX < 0);
    if (dodgeStarted) {
      this.hero.play(CORE_RAIDER_ANIMATIONS.dodgeVisual.key, true);
    } else if (this.hero.anims.currentAnim?.key !== CORE_RAIDER_ANIMATIONS.dodgeVisual.key
      || !this.hero.anims.isPlaying) {
      const locomotion = selectLocomotionState(input.moveX, input.moveY);
      this.hero.play(animationKeyFor(locomotion), true);
    }
    this.cameras.main.setFollowOffset(-input.moveX * 85, 90 - input.moveY * 32);
  }

  private createHeroAnimations(textureKey: string) {
    Object.values(CORE_RAIDER_ANIMATIONS).forEach((animation) => {
      if (this.anims.exists(animation.key)) return;
      this.anims.create({
        key: animation.key,
        frames: animation.frames.map((frame) => ({ key: textureKey, frame })),
        frameRate: animation.frameRate,
        repeat: animation.repeat,
        skipMissedFrames: true,
      });
    });
  }

  private createAmbient(manifest: WorldManifest, quality: WorldQualityTier) {
    const cloudLimit = quality === 'LOW' ? 3 : quality === 'BALANCED' ? 5 : 8;
    const emberLimit = quality === 'LOW' ? 8 : quality === 'BALANCED' ? 16 : 32;
    const cloudCount = Math.min(manifest.ambient.cloudCount, cloudLimit);
    const emberCount = Math.min(manifest.ambient.emberCount, emberLimit);

    this.cloudLayer = this.add.container(0, 0).setDepth(-50).setScrollFactor(0.3);
    for (let index = 0; index < cloudCount; index += 1) {
      this.cloudLayer.add(
        this.add.ellipse(
          420 + index * 310,
          260 + (index % 3) * 72,
          270,
          58,
          0xfff3d7,
          0.09,
        ),
      );
    }
    for (let index = 0; index < emberCount; index += 1) {
      this.add
        .circle(
          680 + ((index * 113) % 1160),
          720 + ((index * 79) % 430),
          1.5 + (index % 3),
          index % 2 === 0 ? 0xffd166 : 0x45f0b5,
          0.24,
        )
        .setDepth(30);
    }
  }

  private renderAmbient(alpha: number) {
    if (!this.cloudLayer) {
      return;
    }
    const reducedMotion = this.registry.get('reducedMotion') === true;
    const interpolatedTime = Phaser.Math.Linear(
      this.previousSimulationTime,
      this.simulationTime,
      alpha,
    );
    const range = reducedMotion ? 12 : 90;
    this.cloudLayer.x = Math.sin(interpolatedTime / 7000) * range;
  }

  private emitMetrics() {
    const onMetrics = this.registry.get('onMetrics') as MetricsCallback | undefined;
    const quality = this.registry.get('qualityTier') as WorldQualityTier;
    onMetrics?.({
      fps: Math.round((this.metricFrames * 1000) / this.metricElapsed),
      frameTimeMs: Number((this.metricFrameTime / this.metricFrames).toFixed(1)),
      simulationHz: Math.round((this.metricSimulationSteps * 1000) / this.metricElapsed),
      renderedObjects: this.children.length,
      worldScreens: Number((manifestWidth(this) / LOGICAL_WIDTH).toFixed(1)),
      regionCount: (this.registry.get('worldManifest') as WorldManifest).regions.length,
      droppedSimulationMs: Math.round(this.metricDroppedMs),
      renderer: this.game.renderer.type === Phaser.WEBGL ? 'WEBGL' : 'CANVAS',
      quality,
    });
    this.metricElapsed = 0;
    this.metricFrames = 0;
    this.metricFrameTime = 0;
    this.metricSimulationSteps = 0;
    this.metricDroppedMs = 0;
  }

  private handleVisibility() {
    this.hidden = document.hidden;
    if (this.hidden) this.inputState?.releaseAll();
    this.clock.reset();
    this.emitStatus({
      phase: this.hidden ? 'SLEEPING' : 'READY',
      progress: 100,
      message: this.hidden ? 'World runtime sleeping' : 'Local Khu runtime ready',
    });
    if (this.hidden) {
      this.game.loop.sleep();
    } else {
      this.game.loop.wake();
    }
  }

  private emitStatus(status: WorldRuntimeStatus) {
    const onStatus = this.registry.get('onStatus') as StatusCallback | undefined;
    onStatus?.(status);
  }

  private disposeScene() {
    if (this.visibilityListener) {
      document.removeEventListener('visibilitychange', this.visibilityListener);
    }
    window.removeEventListener('blur', this.blurListener);
    this.visibilityListener = undefined;
    this.input.off(Phaser.Input.Events.POINTER_DOWN, this.pointerDown);
    this.input.off(Phaser.Input.Events.POINTER_UP, this.pointerUp);
    this.keyboardAdapter?.destroy();
    this.keyboardAdapter = undefined;
    this.inputState?.releaseAll();
    this.inputState = undefined;
    this.hero = undefined;
    this.dodgeWasPressed = false;
    this.clock.reset();
    this.cloudLayer = undefined;
  }
}

function manifestWidth(scene: Phaser.Scene) {
  return (scene.registry.get('worldManifest') as WorldManifest).world.width;
}
