export type InputSource = 'KEYBOARD' | 'POINTER' | 'TOUCH';
export type InputAction =
  | 'ATTACK'
  | 'SKILL_1'
  | 'SKILL_2'
  | 'SKILL_3'
  | 'SKILL_4'
  | 'DODGE'
  | 'INTERACT';

export type InputSnapshot = {
  sequence: number;
  generation: number;
  moveX: number;
  moveY: number;
  aimX: number;
  aimY: number;
  actions: ReadonlySet<InputAction>;
};

type Vector = { x: number; y: number };

export class UnifiedInputState {
  private sequence = 0;
  private generation = 0;
  private readonly movement = new Map<InputSource, Vector>();
  private readonly aim = new Map<InputSource, Vector>();
  private readonly actions = new Map<InputSource, Set<InputAction>>();

  beginGeneration() {
    this.generation += 1;
    this.releaseAll();
    return this.generation;
  }

  setMove(source: InputSource, x: number, y: number) {
    this.setVector(this.movement, source, x, y);
  }

  setAim(source: InputSource, x: number, y: number) {
    this.setVector(this.aim, source, x, y);
  }

  setAction(source: InputSource, action: InputAction, pressed: boolean) {
    const sourceActions = this.actions.get(source) ?? new Set<InputAction>();
    const changed = pressed ? !sourceActions.has(action) : sourceActions.has(action);
    if (!changed) return;
    if (pressed) sourceActions.add(action);
    else sourceActions.delete(action);
    if (sourceActions.size > 0) this.actions.set(source, sourceActions);
    else this.actions.delete(source);
    this.sequence += 1;
  }

  releaseSource(source: InputSource) {
    const movementChanged = this.movement.delete(source);
    const aimChanged = this.aim.delete(source);
    const actionsChanged = this.actions.delete(source);
    const changed = movementChanged || aimChanged || actionsChanged;
    if (changed) this.sequence += 1;
  }

  releaseAll() {
    const changed = this.movement.size + this.aim.size + this.actions.size > 0;
    this.movement.clear();
    this.aim.clear();
    this.actions.clear();
    if (changed) this.sequence += 1;
  }

  snapshot(): InputSnapshot {
    const move = combineVectors(this.movement.values());
    const aim = combineVectors(this.aim.values());
    return {
      sequence: this.sequence,
      generation: this.generation,
      moveX: move.x,
      moveY: move.y,
      aimX: aim.x,
      aimY: aim.y,
      actions: new Set([...this.actions.values()].flatMap((values) => [...values])),
    };
  }

  private setVector(
    collection: Map<InputSource, Vector>,
    source: InputSource,
    x: number,
    y: number,
  ) {
    const normalized = normalizeVector(x, y);
    const current = collection.get(source);
    if (current?.x === normalized.x && current.y === normalized.y) return;
    if (!current && normalized.x === 0 && normalized.y === 0) return;
    if (normalized.x === 0 && normalized.y === 0) collection.delete(source);
    else collection.set(source, normalized);
    this.sequence += 1;
  }
}

function normalizeVector(x: number, y: number): Vector {
  if (!Number.isFinite(x) || !Number.isFinite(y)) return { x: 0, y: 0 };
  const length = Math.hypot(x, y);
  if (length === 0) return { x: 0, y: 0 };
  const scale = length > 1 ? 1 / length : 1;
  return {
    x: Number((x * scale).toFixed(4)),
    y: Number((y * scale).toFixed(4)),
  };
}

function combineVectors(vectors: IterableIterator<Vector>): Vector {
  let x = 0;
  let y = 0;
  for (const vector of vectors) {
    x += vector.x;
    y += vector.y;
  }
  return normalizeVector(x, y);
}
