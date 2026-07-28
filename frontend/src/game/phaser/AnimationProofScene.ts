import Phaser from 'phaser';
import type { AnimationProofMetrics } from '../types';
import { proofAssets } from './proofAssets';

type MetricsCallback = (metrics: AnimationProofMetrics) => void;
type LifecycleCallback = () => void;

const WIDTH = 1280;
const HEIGHT = 720;

export class AnimationProofScene extends Phaser.Scene {
  private metricElapsed = 0;
  private metricFrames = 0;
  private metricFrameTime = 0;
  private reducedMotion = false;
  private decorativeTweens: Phaser.Tweens.Tween[] = [];
  private frameTimers: Phaser.Time.TimerEvent[] = [];

  constructor() {
    super({ key: 'AnimationProof', active: false });
  }

  preload() {
    this.load.once(Phaser.Loader.Events.FILE_LOAD_ERROR, () => {
      const onFailure = this.registry.get('onFailure') as LifecycleCallback | undefined;
      onFailure?.();
    });
    this.load.image('proof-background', proofAssets.background);
    this.load.spritesheet('proof-hero', proofAssets.hero, {
      frameWidth: 443,
      frameHeight: 443,
    });
    this.load.spritesheet('proof-monster', proofAssets.monster, {
      frameWidth: 443,
      frameHeight: 443,
    });
    if (this.registry.get('qualityTier') === 'STANDARD') {
      this.load.spritesheet('proof-boss', proofAssets.boss, {
        frameWidth: 384,
        frameHeight: 512,
      });
      this.load.spritesheet('proof-slash', proofAssets.slash, {
        frameWidth: 384,
        frameHeight: 341,
      });
    }
  }

  create() {
    this.reducedMotion = this.registry.get('reducedMotion') === true;
    this.createBackdrop();

    this.createAnimation('hero-attack-proof', 'proof-hero', 8, 9, 1800);

    const hero = this.add.sprite(315, 545, 'proof-hero', 0).setOrigin(0.5, 1).setScale(0.62);
    hero.play('hero-attack-proof');

    if (this.registry.get('qualityTier') === 'STANDARD') {
      this.createAnimation('boss-slam-proof', 'proof-boss', 8, 7, 2300);
      this.createAnimation('slash-proof', 'proof-slash', 12, 14, 1550);

      const slash = this.add.sprite(505, 492, 'proof-slash', 0).setScale(0.47).setAlpha(0.92);
      slash.play('slash-proof');

      const boss = this.add.sprite(1018, 570, 'proof-boss', 0).setOrigin(0.5, 1).setScale(0.47);
      boss.play('boss-slam-proof');
    } else {
      this.add
        .text(1035, 485, 'LOW QUALITY\nBoss preview deferred', {
          align: 'center',
          color: '#dfe6ff',
          fontFamily: 'system-ui, sans-serif',
          fontSize: '18px',
          fontStyle: 'bold',
        })
        .setOrigin(0.5);
    }

    const monsterPositions = [
      [555, 610],
      [675, 584],
      [776, 621],
      [875, 585],
    ];
    monsterPositions.forEach(([x, y], index) => {
      const monster = this.add
        .sprite(x, y, 'proof-monster', index % 4)
        .setOrigin(0.5, 1)
        .setScale(0.34);
      let monsterFrame = index % 4;
      const frameTimer = this.time.addEvent({
        delay: 150 + index * 12,
        loop: true,
        callback: () => {
          monsterFrame = (monsterFrame + 1) % 4;
          monster.setFrame(monsterFrame);
        },
      });
      this.frameTimers.push(frameTimer);
      const tween = this.tweens.add({
        targets: monster,
        x: x + (index % 2 === 0 ? 34 : -34),
        duration: 1600 + index * 170,
        yoyo: true,
        repeat: -1,
        ease: 'Sine.InOut',
      });
      this.decorativeTweens.push(tween);
    });

    this.add
      .text(44, 630, 'CORE RAIDER', {
        color: '#fffaf0',
        fontFamily: 'system-ui, sans-serif',
        fontSize: '18px',
        fontStyle: 'bold',
      })
      .setShadow(0, 2, '#090b18', 4);
    if (this.registry.get('qualityTier') === 'STANDARD') {
      this.add
        .text(1035, 630, 'RUBBER DUCK KING', {
          color: '#ffd166',
          fontFamily: 'system-ui, sans-serif',
          fontSize: '18px',
          fontStyle: 'bold',
        })
        .setOrigin(0.5, 0)
        .setShadow(0, 2, '#090b18', 4);
    }

    this.applyMotionPreference();
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => this.disposeScene());
    const onReady = this.registry.get('onReady') as LifecycleCallback | undefined;
    onReady?.();
  }

  update(_time: number, delta: number) {
    this.metricElapsed += delta;
    this.metricFrames += 1;
    this.metricFrameTime += delta;

    const nextReducedMotion = this.registry.get('reducedMotion') === true;
    if (nextReducedMotion !== this.reducedMotion) {
      this.reducedMotion = nextReducedMotion;
      this.applyMotionPreference();
    }

    if (this.metricElapsed >= 1000) {
      const onMetrics = this.registry.get('onMetrics') as MetricsCallback | undefined;
      onMetrics?.({
        fps: Math.round((this.metricFrames * 1000) / this.metricElapsed),
        frameTimeMs: Number((this.metricFrameTime / this.metricFrames).toFixed(1)),
        renderedObjects: this.children.length,
        renderer: this.game.renderer.type === Phaser.WEBGL ? 'WEBGL' : 'CANVAS',
      });
      this.metricElapsed = 0;
      this.metricFrames = 0;
      this.metricFrameTime = 0;
    }
  }

  private createBackdrop() {
    this.add.image(WIDTH / 2, HEIGHT / 2, 'proof-background').setDisplaySize(WIDTH, HEIGHT);
    this.add.rectangle(WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT, 0x101426, 0.2);
    this.add.rectangle(WIDTH / 2, 655, WIDTH, 130, 0x090b18, 0.42);

    const cloudLayer = this.add.container();
    for (let index = 0; index < 7; index += 1) {
      const cloud = this.add
        .ellipse(index * 230 - 120, 105 + (index % 3) * 52, 230, 52, 0xfff3d7, 0.1)
        .setScrollFactor(0.15);
      cloudLayer.add(cloud);
    }
    const cloudTween = this.tweens.add({
      targets: cloudLayer,
      x: 180,
      duration: 24000,
      yoyo: true,
      repeat: -1,
      ease: 'Sine.InOut',
    });
    this.decorativeTweens.push(cloudTween);

    for (let index = 0; index < 26; index += 1) {
      const ember = this.add.circle(
        80 + ((index * 97) % 1120),
        220 + ((index * 61) % 360),
        1.5 + (index % 3),
        index % 2 === 0 ? 0xffd166 : 0x45f0b5,
        0.32,
      );
      const tween = this.tweens.add({
        targets: ember,
        y: ember.y - 38 - (index % 5) * 8,
        alpha: 0.06,
        duration: 2200 + (index % 7) * 280,
        yoyo: true,
        repeat: -1,
        delay: index * 90,
        ease: 'Sine.InOut',
      });
      this.decorativeTweens.push(tween);
    }
  }

  private createAnimation(
    key: string,
    textureKey: string,
    frameCount: number,
    frameRate: number,
    repeatDelay: number,
  ) {
    this.anims.create({
      key,
      frames: this.anims.generateFrameNumbers(textureKey, { start: 0, end: frameCount - 1 }),
      frameRate,
      repeat: -1,
      repeatDelay,
      skipMissedFrames: true,
    });
  }

  private applyMotionPreference() {
    this.decorativeTweens.forEach((tween) => {
      if (this.reducedMotion) {
        tween.pause();
      } else {
        tween.resume();
      }
    });
    this.anims.globalTimeScale = this.reducedMotion ? 0.45 : 1;
    this.time.timeScale = this.reducedMotion ? 0.35 : 1;
  }

  private disposeScene() {
    this.decorativeTweens.forEach((tween) => tween.destroy());
    this.decorativeTweens = [];
    this.frameTimers.forEach((timer) => timer.destroy());
    this.frameTimers = [];
  }
}
