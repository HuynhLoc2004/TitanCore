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
import type { InputAction } from '../input/UnifiedInputState';
import {
  animationKeyFor,
  CORE_RAIDER_ANIMATIONS,
  CORE_RAIDER_FRAME,
  selectLocomotionState,
} from '../locomotion';
import { approachElevation, perspectiveScale, sampleElevation } from '../traversal';
import {
  COMBAT_FEEL_SKILLS,
  isSkillReady,
  newlyPressedActions,
  selectTargets,
  type CombatFeelSkill,
} from '../combatFeel';
import {
  advanceEnemyBehavior,
  type EnemyBehavior,
} from '../enemyBehavior';
import {
  ENEMY_ARCHETYPES,
  type EnemyArchetype,
  type EnemyArchetypeDefinition,
} from '../enemyArchetypes';

type MetricsCallback = (metrics: WorldRuntimeMetrics) => void;
type StatusCallback = (status: WorldRuntimeStatus) => void;

const LOGICAL_WIDTH = 1280;
const LOGICAL_HEIGHT = 720;
const HERO_GROUND_OFFSET = 16;
const HERO_ATTACK_ANIMATION = 'core-raider-local-attack';
const SPROUT_IDLE_ANIMATION = 'core-sprout-local-idle';
const SPROUT_CHARGE_ANIMATION = 'core-sprout-local-charge';
const SPROUT_ATTACK_ANIMATION = 'core-sprout-local-attack';
const SPROUT_RECOVER_ANIMATION = 'core-sprout-local-recover';
const SPROUT_HIT_ANIMATION = 'core-sprout-local-hit';
const SLASH_ANIMATION = 'core-local-slash';
const HUNT_TARGET = 6;

type LocalTarget = {
  id: string;
  sprite: Phaser.GameObjects.Sprite;
  hpBack: Phaser.GameObjects.Rectangle;
  hpFill: Phaser.GameObjects.Rectangle;
  hp: number;
  maxHp: number;
  baseScale: number;
  respawnAt: number;
  spawnX: number;
  spawnY: number;
  behavior: EnemyBehavior;
  telegraph?: Phaser.GameObjects.Ellipse;
  archetype: EnemyArchetype;
  profile: EnemyArchetypeDefinition;
};

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
  private heroAnchor?: Phaser.GameObjects.Zone;
  private heroVisual?: Phaser.GameObjects.Sprite;
  private heroShadow?: Phaser.GameObjects.Ellipse;
  private keyboardAdapter?: KeyboardInputAdapter;
  private inputState?: UnifiedInputState;
  private dodgeWasPressed = false;
  private heroBaseScale = 1;
  private elevation = 0;
  private previousActions = new Set<InputAction>();
  private readonly cooldownReadyAt = new Map<InputAction, number>();
  private actionLockedUntil = 0;
  private combatHud?: Phaser.GameObjects.Text;
  private playerHp = 100;
  private playerInvulnerableUntil = 0;
  private huntKills = 0;
  private readonly targets: LocalTarget[] = [];
  private foregroundMist?: Phaser.GameObjects.Container;
  private windRings: Array<{
    ring: Phaser.GameObjects.Ellipse;
    oscillationMs: number;
  }> = [];
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
        this.heroAnchor = this.add.zone(
          entity.x,
          entity.y - HERO_GROUND_OFFSET,
          56,
          HERO_GROUND_OFFSET * 2,
        );
        this.physics.add.existing(this.heroAnchor);
        const body = this.heroAnchor.body as Phaser.Physics.Arcade.Body;
        body.setCollideWorldBounds(true);
        this.heroShadow = this.add
          .ellipse(entity.x, entity.y + 3, 76, 24, 0x06101c, 0.34)
          .setDepth(1000 + entity.y - 2);
        this.heroVisual = this.add
          .sprite(entity.x, entity.y, entity.assetKey, entity.frame)
          .setOrigin(
            CORE_RAIDER_FRAME.pivotX / CORE_RAIDER_FRAME.width,
            CORE_RAIDER_FRAME.groundY / CORE_RAIDER_FRAME.height,
          )
          .setScale(entity.scale)
          .setDepth(1000 + entity.y);
        this.heroBaseScale = entity.scale;
      } else {
        const archetype = entity.behaviorProfile as EnemyArchetype;
        const profile = ENEMY_ARCHETYPES[archetype];
        const sprite = this.add
          .sprite(entity.x, entity.y, entity.assetKey, entity.frame)
          .setOrigin(0.5, 1)
          .setScale(entity.scale)
          .setTint(profile.tint)
          .setDepth(1000 + entity.y);
        const hpBack = this.add
          .rectangle(entity.x, entity.y - 104, 72, 8, 0x08101f, 0.9)
          .setDepth(2000 + entity.y);
        const hpFill = this.add
          .rectangle(entity.x - 34, entity.y - 104, 68, 4, 0x45f0b5, 1)
          .setOrigin(0, 0.5)
          .setDepth(2001 + entity.y);
        this.targets.push({
          id: entity.id,
          sprite,
          hpBack,
          hpFill,
          hp: profile.maxHp,
          maxHp: profile.maxHp,
          baseScale: entity.scale,
          respawnAt: 0,
          spawnX: entity.x,
          spawnY: entity.y,
          behavior: {
            state: 'PATROL',
            stateUntil: 0,
            patrolDirection: entity.frame % 2 === 0 ? 1 : -1,
          },
          archetype,
          profile,
        });
      }
    });
    this.createTraversalGeometry(manifest);

    if (this.heroVisual && this.heroAnchor) {
      this.createHeroAnimations(this.heroVisual.texture.key);
      this.createCombatAnimations();
      this.heroVisual.play(CORE_RAIDER_ANIMATIONS.idle.key);
      this.cameras.main.startFollow(
        this.heroAnchor,
        true,
        manifest.navigation.cameraLerp,
        manifest.navigation.cameraLerp,
        0,
        90,
      );
    }
    this.keyboardAdapter = new KeyboardInputAdapter(this, this.inputState);
    this.combatHud = this.add
      .text(LOGICAL_WIDTH / 2, 62, '', {
        fontFamily: 'system-ui, sans-serif',
        fontSize: '15px',
        fontStyle: '700',
        color: '#f6fbff',
        backgroundColor: 'rgba(7, 13, 28, 0.76)',
        padding: { x: 14, y: 8 },
      })
      .setOrigin(0.5, 0)
      .setScrollFactor(0)
      .setDepth(5000);
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
    if (this.heroAnchor && this.inputState) {
      const pointer = this.input.activePointer;
      this.inputState.setAim(
        'POINTER',
        pointer.worldX - this.heroAnchor.x,
        pointer.worldY - this.heroAnchor.y,
      );
    }
    const result = this.clock.advance(delta, (stepMs) => {
      this.previousSimulationTime = this.simulationTime;
      this.simulationTime += stepMs;
      this.metricSimulationSteps += 1;
      this.simulateMovement(stepMs);
    });
    this.metricDroppedMs += result.droppedMs;
    this.renderHero();
    this.renderCombat();
    this.renderAmbient(result.alpha);

    this.metricElapsed += delta;
    this.metricFrames += 1;
    this.metricFrameTime += delta;
    if (this.metricElapsed >= 1000) {
      this.emitMetrics();
    }
  }

  private simulateMovement(stepMs: number) {
    if (!this.heroAnchor || !this.heroVisual || !this.inputState) return;
    const manifest = this.registry.get('worldManifest') as WorldManifest;
    const input = this.inputState.snapshot();
    const pressedActions = newlyPressedActions(input.actions, this.previousActions);
    this.previousActions = new Set(input.actions);
    this.tryCombatAction(pressedActions);
    this.simulateEnemies(stepMs);
    const dodgePressed = input.actions.has('DODGE');
    const dodgeStarted = dodgePressed && !this.dodgeWasPressed;
    this.dodgeWasPressed = dodgePressed;
    const body = this.heroAnchor.body as Phaser.Physics.Arcade.Body;
    body.setVelocity(
      input.moveX * manifest.navigation.moveSpeed,
      input.moveY * manifest.navigation.moveSpeed,
    );
    if (input.moveX !== 0) this.heroVisual.setFlipX(input.moveX < 0);
    if (dodgeStarted && this.simulationTime >= this.actionLockedUntil) {
      this.heroVisual.play(CORE_RAIDER_ANIMATIONS.dodgeVisual.key, true);
    } else if (this.simulationTime >= this.actionLockedUntil
      && (this.heroVisual.anims.currentAnim?.key
        !== CORE_RAIDER_ANIMATIONS.dodgeVisual.key
      || !this.heroVisual.anims.isPlaying)) {
      const locomotion = selectLocomotionState(input.moveX, input.moveY);
      this.heroVisual.play(animationKeyFor(locomotion), true);
    }
    const elevationSample = sampleElevation(
      this.heroAnchor.x,
      this.heroAnchor.y + HERO_GROUND_OFFSET,
      this.registry.get('reducedMotion') === true ? 0 : this.simulationTime,
      manifest.elevationZones,
    );
    this.elevation = approachElevation(this.elevation, elevationSample.target, stepMs);
    this.cameras.main.setFollowOffset(-input.moveX * 85, 90 - input.moveY * 32);
  }

  private renderHero() {
    if (!this.heroAnchor || !this.heroVisual || !this.heroShadow) return;
    const manifest = this.registry.get('worldManifest') as WorldManifest;
    const groundY = this.heroAnchor.y + HERO_GROUND_OFFSET;
    this.heroVisual
      .setPosition(this.heroAnchor.x, groundY - this.elevation)
      .setScale(
        this.heroBaseScale * perspectiveScale(
          groundY,
          manifest.navigation.minY,
          manifest.navigation.maxY,
        ),
      )
      .setDepth(1000 + groundY);
    const elevationRatio = Phaser.Math.Clamp(this.elevation / 160, 0, 1);
    this.heroShadow
      .setPosition(this.heroAnchor.x, groundY + 3)
      .setScale(1 - elevationRatio * 0.42)
      .setAlpha(0.34 - elevationRatio * 0.18)
      .setDepth(1000 + groundY - 2);
  }

  private tryCombatAction(pressedActions: ReadonlySet<InputAction>) {
    if (!this.heroAnchor || !this.heroVisual || this.simulationTime < this.actionLockedUntil) return;
    const skill = COMBAT_FEEL_SKILLS.find((candidate) => (
      pressedActions.has(candidate.action)
      && isSkillReady(candidate.action, this.simulationTime, this.cooldownReadyAt)
    ));
    if (!skill) return;

    this.cooldownReadyAt.set(skill.action, this.simulationTime + skill.cooldownMs);
    this.actionLockedUntil = this.simulationTime + (skill.area ? 380 : 300);
    this.heroVisual.play(HERO_ATTACK_ANIMATION, true);
    const facing = this.heroVisual.flipX ? -1 : 1;
    if (skill.action === 'SKILL_1' && this.heroAnchor) {
      this.heroAnchor.x = Phaser.Math.Clamp(this.heroAnchor.x + facing * 38, 0, 5120);
    }
    this.spawnSkillEffect(skill);
    const selected = selectTargets(
      { x: this.heroAnchor.x, y: this.heroAnchor.y },
      this.targets.map((target) => ({
        id: target.id,
        x: target.sprite.x,
        y: target.sprite.y,
        active: target.respawnAt === 0,
      })),
      skill.range,
      skill.area,
      facing,
    );
    selected.forEach(({ id }) => {
      const target = this.targets.find((candidate) => candidate.id === id);
      if (target) this.applyLocalHit(target, skill);
    });
  }

  private simulateEnemies(stepMs: number) {
    if (!this.heroAnchor) return;
    const deltaSeconds = stepMs / 1000;
    this.targets.forEach((target) => {
      if (target.respawnAt > 0) return;
      const dx = this.heroAnchor!.x - target.sprite.x;
      const dy = this.heroAnchor!.y - target.sprite.y;
      const distanceToHero = Math.hypot(dx, dy);
      const distanceFromSpawn = Math.hypot(
        target.sprite.x - target.spawnX,
        target.sprite.y - target.spawnY,
      );
      const result = advanceEnemyBehavior(
        target.behavior,
        this.simulationTime,
        distanceToHero,
        distanceFromSpawn,
        target.profile.behavior,
      );
      const previousState = target.behavior.state;
      target.behavior = result.behavior;
      if (target.behavior.state === 'PATROL') {
        target.sprite.x += target.behavior.patrolDirection
          * target.profile.behavior.patrolSpeed * deltaSeconds;
        target.sprite.setFlipX(target.behavior.patrolDirection < 0);
      } else if (target.behavior.state === 'CHASE' && distanceToHero > 0) {
        target.sprite.x += (dx / distanceToHero)
          * target.profile.behavior.chaseSpeed * deltaSeconds;
        target.sprite.y += (dy / distanceToHero)
          * target.profile.behavior.chaseSpeed * deltaSeconds;
        target.sprite.setFlipX(dx < 0);
      }
      if (target.behavior.state === 'TELEGRAPH' && previousState !== 'TELEGRAPH') {
        target.sprite.play(SPROUT_CHARGE_ANIMATION, true);
        this.showEnemyTelegraph(target);
      }
      if (result.attack) {
        target.sprite.play(SPROUT_ATTACK_ANIMATION, true);
        target.telegraph?.destroy();
        target.telegraph = undefined;
        if (distanceToHero <= target.profile.behavior.attackRange + 18) {
          this.applyPlayerHit(target);
        }
        this.time.delayedCall(190, () => {
          if (target.behavior.state === 'RECOVER' && target.hp > 0) {
            target.sprite.play(SPROUT_RECOVER_ANIMATION, true);
          }
        });
      } else if (previousState === 'RECOVER' && target.behavior.state === 'CHASE') {
        target.sprite.play(SPROUT_IDLE_ANIMATION, true);
      }
    });
  }

  private showEnemyTelegraph(target: LocalTarget) {
    target.telegraph?.destroy();
    const reducedMotion = this.registry.get('reducedMotion') === true;
    target.telegraph = this.add
      .ellipse(
        target.sprite.x,
        target.sprite.y + 6,
        Math.min(290, target.profile.behavior.attackRange * 1.25),
        Math.min(94, target.profile.behavior.attackRange * 0.48),
        0xff526e,
        0.18,
      )
      .setStrokeStyle(4, 0xff8b57, 0.92)
      .setDepth(940 + target.sprite.y);
    this.tweens.add({
      targets: target.telegraph,
      scaleX: reducedMotion ? 1 : 0.72,
      scaleY: reducedMotion ? 1 : 0.72,
      alpha: reducedMotion ? 0.5 : 0.82,
      yoyo: true,
      repeat: reducedMotion ? 0 : 2,
      duration: reducedMotion ? target.profile.behavior.telegraphMs : 210,
    });
  }

  private applyPlayerHit(target: LocalTarget) {
    if (!this.heroAnchor || !this.heroVisual
      || this.simulationTime < this.playerInvulnerableUntil) return;
    this.playerHp = Math.max(0, this.playerHp - target.profile.damage);
    this.playerInvulnerableUntil = this.simulationTime + 720;
    const direction = Math.sign(this.heroAnchor.x - target.sprite.x) || 1;
    this.heroAnchor.x = Phaser.Math.Clamp(this.heroAnchor.x + direction * 34, 0, 5120);
    this.heroVisual.setTintFill(0xff8c93);
    this.time.delayedCall(120, () => this.heroVisual?.clearTint());
    this.cameras.main.shake(
      this.registry.get('reducedMotion') === true ? 40 : 95,
      this.registry.get('reducedMotion') === true ? 0.0005 : 0.0018,
    );
    if (this.playerHp === 0) {
      this.playerHp = 100;
      this.heroAnchor.setPosition(620, 594);
    }
  }

  private spawnSkillEffect(skill: CombatFeelSkill) {
    if (!this.heroAnchor || !this.heroVisual) return;
    const reducedMotion = this.registry.get('reducedMotion') === true;
    if (skill.area) {
      const ring = this.add
        .ellipse(this.heroAnchor.x, this.heroAnchor.y + 12, 100, 42, skill.color, 0.16)
        .setStrokeStyle(5, skill.color, 0.88)
        .setDepth(950 + this.heroAnchor.y);
      this.tweens.add({
        targets: ring,
        scaleX: skill.range / 50,
        scaleY: skill.range / 82,
        alpha: 0,
        duration: reducedMotion ? 180 : 420,
        ease: 'Quad.easeOut',
        onComplete: () => ring.destroy(),
      });
      return;
    }
    const facing = this.heroVisual.flipX ? -1 : 1;
    const slash = this.add
      .sprite(
        this.heroAnchor.x + facing * 80,
        this.heroAnchor.y - this.elevation - 52,
        'core-slash-effect',
        0,
      )
      .setScale(skill.action === 'SKILL_3' ? 0.62 : 0.44)
      .setFlipX(facing < 0)
      .setTint(skill.color)
      .setDepth(2100 + this.heroAnchor.y);
    slash.play(SLASH_ANIMATION);
    slash.once(Phaser.Animations.Events.ANIMATION_COMPLETE, () => slash.destroy());
  }

  private applyLocalHit(target: LocalTarget, skill: CombatFeelSkill) {
    const wasAlive = target.hp > 0;
    target.hp = Math.max(0, target.hp - skill.damage);
    target.sprite
      .setTintFill(0xffffff)
      .play(SPROUT_HIT_ANIMATION, true);
    this.time.delayedCall(95, () => {
      if (!target.sprite.active) return;
      target.sprite.setTint(target.profile.tint);
      if (target.hp > 0) target.sprite.play(SPROUT_IDLE_ANIMATION, true);
    });
    const damageText = this.add
      .text(target.sprite.x, target.sprite.y - 125, `-${skill.damage}`, {
        fontFamily: 'system-ui, sans-serif',
        fontSize: skill.action === 'SKILL_3' ? '24px' : '19px',
        fontStyle: '800',
        color: `#${skill.color.toString(16).padStart(6, '0')}`,
        stroke: '#07101f',
        strokeThickness: 4,
      })
      .setOrigin(0.5)
      .setDepth(4000);
    this.tweens.add({
      targets: damageText,
      y: damageText.y - 42,
      alpha: 0,
      duration: 520,
      ease: 'Cubic.easeOut',
      onComplete: () => damageText.destroy(),
    });
    if (this.heroAnchor) {
      const push = Math.sign(target.sprite.x - this.heroAnchor.x) || 1;
      target.sprite.x += push * (skill.area ? 10 : 18);
    }
    this.cameras.main.shake(
      this.registry.get('reducedMotion') === true ? 25 : 55,
      this.registry.get('reducedMotion') === true ? 0.0004 : 0.0011,
    );
    if (target.hp === 0) {
      if (wasAlive) this.huntKills = Math.min(HUNT_TARGET, this.huntKills + 1);
      target.respawnAt = this.simulationTime + 2600;
      target.behavior = { ...target.behavior, state: 'DEFEATED' };
      target.telegraph?.destroy();
      target.telegraph = undefined;
      target.sprite.setVisible(false);
      target.hpBack.setVisible(false);
      target.hpFill.setVisible(false);
    }
  }

  private renderCombat() {
    const manifest = this.registry.get('worldManifest') as WorldManifest;
    this.targets.forEach((target) => {
      if (target.respawnAt > 0 && this.simulationTime >= target.respawnAt) {
        target.hp = target.maxHp;
        target.respawnAt = 0;
        target.sprite.setPosition(target.spawnX, target.spawnY);
        target.behavior = { state: 'PATROL', stateUntil: 0, patrolDirection: 1 };
        target.sprite
          .setVisible(true)
          .setTint(target.profile.tint)
          .play(SPROUT_IDLE_ANIMATION, true);
        target.hpBack.setVisible(true);
        target.hpFill.setVisible(true);
      }
      if (target.respawnAt > 0) return;
      const scale = perspectiveScale(
        target.sprite.y,
        manifest.navigation.minY,
        manifest.navigation.maxY,
      );
      const reducedMotion = this.registry.get('reducedMotion') === true;
      const motionAmount = reducedMotion ? 0 : target.behavior.state === 'CHASE' ? 0.035 : 0.018;
      const pulse = Math.sin(
        this.simulationTime / (target.behavior.state === 'CHASE' ? 90 : 180)
          + target.spawnX * 0.01,
      ) * motionAmount;
      const anticipation = target.behavior.state === 'TELEGRAPH'
        ? Math.sin(this.simulationTime / 75) * (reducedMotion ? 0.015 : 0.055)
        : 0;
      target.sprite
        .setScale(
          target.baseScale * scale * (1 + pulse + anticipation),
          target.baseScale * scale * (1 - pulse * 0.65 - anticipation * 0.4),
        )
        .setDepth(1000 + target.sprite.y);
      target.hpBack
        .setPosition(target.sprite.x, target.sprite.y - 104 * scale)
        .setDepth(2000 + target.sprite.y);
      target.hpFill
        .setPosition(target.sprite.x - 34, target.sprite.y - 104 * scale)
        .setDisplaySize(68 * (target.hp / target.maxHp), 4)
        .setDepth(2001 + target.sprite.y);
      target.telegraph?.setPosition(target.sprite.x, target.sprite.y + 6)
        .setDepth(940 + target.sprite.y);
    });
    if (this.combatHud) {
      const skills = COMBAT_FEEL_SKILLS.map((skill) => {
        const remaining = Math.max(
          0,
          (this.cooldownReadyAt.get(skill.action) ?? 0) - this.simulationTime,
        );
        return remaining === 0 ? `${skill.label} READY` : `${skill.label} ${(remaining / 1000).toFixed(1)}`;
      }).join('  |  ');
      const objective = this.huntKills >= HUNT_TARGET
        ? 'CORE GATE CHARGED'
        : `HUNT ${this.huntKills}/${HUNT_TARGET}`;
      this.combatHud.setText(`${objective}  |  HP ${this.playerHp}/100  |  ${skills}`);
    }
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

  private createCombatAnimations() {
    if (!this.anims.exists(HERO_ATTACK_ANIMATION)) {
      this.anims.create({
        key: HERO_ATTACK_ANIMATION,
        frames: this.anims.generateFrameNumbers('core-raider-attack', { start: 0, end: 7 }),
        frameRate: 15,
        repeat: 0,
        skipMissedFrames: true,
      });
    }
    if (!this.anims.exists(SLASH_ANIMATION)) {
      this.anims.create({
        key: SLASH_ANIMATION,
        frames: this.anims.generateFrameNumbers('core-slash-effect', { start: 0, end: 11 }),
        frameRate: 22,
        repeat: 0,
        skipMissedFrames: true,
      });
    }
    if (!this.anims.exists(SPROUT_IDLE_ANIMATION)) {
      this.anims.create({
        key: SPROUT_IDLE_ANIMATION,
        frames: this.anims.generateFrameNumbers('core-sprout', { start: 0, end: 3 }),
        frameRate: 7,
        repeat: -1,
        skipMissedFrames: true,
      });
    }
    if (!this.anims.exists(SPROUT_HIT_ANIMATION)) {
      this.anims.create({
        key: SPROUT_HIT_ANIMATION,
        frames: [{ key: 'core-sprout', frame: 6 }, { key: 'core-sprout', frame: 7 }],
        frameRate: 12,
        repeat: 0,
      });
    }
    if (!this.anims.exists(SPROUT_CHARGE_ANIMATION)) {
      this.anims.create({
        key: SPROUT_CHARGE_ANIMATION,
        frames: [{ key: 'core-sprout', frame: 4 }],
        frameRate: 1,
        repeat: -1,
      });
    }
    if (!this.anims.exists(SPROUT_ATTACK_ANIMATION)) {
      this.anims.create({
        key: SPROUT_ATTACK_ANIMATION,
        frames: [{ key: 'core-sprout', frame: 5 }],
        frameRate: 1,
        repeat: 0,
      });
    }
    if (!this.anims.exists(SPROUT_RECOVER_ANIMATION)) {
      this.anims.create({
        key: SPROUT_RECOVER_ANIMATION,
        frames: [{ key: 'core-sprout', frame: 7 }],
        frameRate: 1,
        repeat: -1,
      });
    }
    this.targets.forEach((target) => target.sprite.play(SPROUT_IDLE_ANIMATION, true));
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
    this.foregroundMist = this.add.container(0, 0).setDepth(3000);
    const mistCount = quality === 'LOW' ? 3 : quality === 'BALANCED' ? 5 : 7;
    for (let index = 0; index < mistCount; index += 1) {
      this.foregroundMist.add(
        this.add
          .ellipse(260 + index * 820, 690 - (index % 2) * 18, 560, 74, 0xccefff, 0.055)
          .setScrollFactor(1.04, 1),
      );
    }
  }

  private createTraversalGeometry(manifest: WorldManifest) {
    manifest.collision.forEach((definition) => {
      const obstacle = this.add.zone(
        definition.x,
        definition.y,
        definition.width,
        definition.height,
      );
      this.physics.add.existing(obstacle, true);
      if (this.heroAnchor) this.physics.add.collider(this.heroAnchor, obstacle);
    });
    manifest.elevationZones.forEach((zone) => {
      const ring = this.add
        .ellipse(zone.x, zone.y, zone.width * 0.72, zone.height * 0.52, 0x7eeaff, 0.07)
        .setStrokeStyle(3, 0x94fff0, 0.22)
        .setDepth(900 + zone.y);
      this.windRings.push({ ring, oscillationMs: zone.oscillationMs });
    });
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
    if (this.foregroundMist) {
      this.foregroundMist.x = Math.sin(interpolatedTime / 5200) * (range * 0.38);
    }
    this.windRings.forEach(({ ring, oscillationMs }, index) => {
      const phase = reducedMotion
        ? 0
        : Math.sin((interpolatedTime + index * 190) / oscillationMs * Math.PI * 2);
      ring
        .setScale(1 + phase * 0.07, 1 - phase * 0.05)
        .setAlpha(reducedMotion ? 0.07 : 0.05 + phase * 0.025);
    });
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
    this.heroAnchor = undefined;
    this.heroVisual = undefined;
    this.heroShadow = undefined;
    this.heroBaseScale = 1;
    this.dodgeWasPressed = false;
    this.previousActions.clear();
    this.cooldownReadyAt.clear();
    this.actionLockedUntil = 0;
    this.combatHud = undefined;
    this.playerHp = 100;
    this.playerInvulnerableUntil = 0;
    this.huntKills = 0;
    this.targets.forEach((target) => target.telegraph?.destroy());
    this.targets.length = 0;
    this.elevation = 0;
    this.clock.reset();
    this.cloudLayer = undefined;
    this.foregroundMist = undefined;
    this.windRings = [];
  }
}

function manifestWidth(scene: Phaser.Scene) {
  return (scene.registry.get('worldManifest') as WorldManifest).world.width;
}
