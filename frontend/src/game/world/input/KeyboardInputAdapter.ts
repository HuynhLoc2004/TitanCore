import Phaser from 'phaser';
import type { UnifiedInputState } from './UnifiedInputState';

type MovementKeys = {
  W: Phaser.Input.Keyboard.Key;
  A: Phaser.Input.Keyboard.Key;
  S: Phaser.Input.Keyboard.Key;
  D: Phaser.Input.Keyboard.Key;
  UP: Phaser.Input.Keyboard.Key;
  LEFT: Phaser.Input.Keyboard.Key;
  DOWN: Phaser.Input.Keyboard.Key;
  RIGHT: Phaser.Input.Keyboard.Key;
  SPACE: Phaser.Input.Keyboard.Key;
  ONE: Phaser.Input.Keyboard.Key;
  TWO: Phaser.Input.Keyboard.Key;
  THREE: Phaser.Input.Keyboard.Key;
  FOUR: Phaser.Input.Keyboard.Key;
  E: Phaser.Input.Keyboard.Key;
};

export class KeyboardInputAdapter {
  private readonly keys: MovementKeys;
  private readonly release = () => this.input.releaseSource('KEYBOARD');

  constructor(
    scene: Phaser.Scene,
    private readonly input: UnifiedInputState,
  ) {
    if (!scene.input.keyboard) throw new Error('Keyboard input is unavailable.');
    this.keys = scene.input.keyboard.addKeys({
      W: Phaser.Input.Keyboard.KeyCodes.W,
      A: Phaser.Input.Keyboard.KeyCodes.A,
      S: Phaser.Input.Keyboard.KeyCodes.S,
      D: Phaser.Input.Keyboard.KeyCodes.D,
      UP: Phaser.Input.Keyboard.KeyCodes.UP,
      LEFT: Phaser.Input.Keyboard.KeyCodes.LEFT,
      DOWN: Phaser.Input.Keyboard.KeyCodes.DOWN,
      RIGHT: Phaser.Input.Keyboard.KeyCodes.RIGHT,
      SPACE: Phaser.Input.Keyboard.KeyCodes.SPACE,
      ONE: Phaser.Input.Keyboard.KeyCodes.ONE,
      TWO: Phaser.Input.Keyboard.KeyCodes.TWO,
      THREE: Phaser.Input.Keyboard.KeyCodes.THREE,
      FOUR: Phaser.Input.Keyboard.KeyCodes.FOUR,
      E: Phaser.Input.Keyboard.KeyCodes.E,
    }) as MovementKeys;
    window.addEventListener('blur', this.release);
  }

  sample() {
    const horizontal = Number(this.keys.D.isDown || this.keys.RIGHT.isDown)
      - Number(this.keys.A.isDown || this.keys.LEFT.isDown);
    const vertical = Number(this.keys.S.isDown || this.keys.DOWN.isDown)
      - Number(this.keys.W.isDown || this.keys.UP.isDown);
    this.input.setMove('KEYBOARD', horizontal, vertical);
    this.input.setAction('KEYBOARD', 'DODGE', this.keys.SPACE.isDown);
    this.input.setAction('KEYBOARD', 'SKILL_1', this.keys.ONE.isDown);
    this.input.setAction('KEYBOARD', 'SKILL_2', this.keys.TWO.isDown);
    this.input.setAction('KEYBOARD', 'SKILL_3', this.keys.THREE.isDown);
    this.input.setAction('KEYBOARD', 'SKILL_4', this.keys.FOUR.isDown);
    this.input.setAction('KEYBOARD', 'INTERACT', this.keys.E.isDown);
  }

  destroy() {
    window.removeEventListener('blur', this.release);
    this.input.releaseSource('KEYBOARD');
  }
}
