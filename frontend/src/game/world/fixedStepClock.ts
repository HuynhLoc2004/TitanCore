export type FixedStepResult = {
  alpha: number;
  steps: number;
  droppedMs: number;
};

export class FixedStepClock {
  private accumulatorMs = 0;

  constructor(
    readonly stepMs = 1000 / 30,
    readonly maximumSteps = 5,
    readonly maximumDeltaMs = 250,
  ) {
    if (stepMs <= 0 || maximumSteps < 1 || maximumDeltaMs < stepMs) {
      throw new Error('Invalid fixed-step clock configuration.');
    }
  }

  advance(deltaMs: number, step: (stepMs: number) => void): FixedStepResult {
    const safeDelta = Number.isFinite(deltaMs) ? Math.max(0, deltaMs) : 0;
    const boundedDelta = Math.min(safeDelta, this.maximumDeltaMs);
    let droppedMs = safeDelta - boundedDelta;
    this.accumulatorMs += boundedDelta;

    let steps = 0;
    while (this.accumulatorMs >= this.stepMs && steps < this.maximumSteps) {
      step(this.stepMs);
      this.accumulatorMs -= this.stepMs;
      steps += 1;
    }

    if (this.accumulatorMs >= this.stepMs) {
      const retained = this.accumulatorMs % this.stepMs;
      droppedMs += this.accumulatorMs - retained;
      this.accumulatorMs = retained;
    }

    return {
      alpha: this.accumulatorMs / this.stepMs,
      steps,
      droppedMs,
    };
  }

  reset() {
    this.accumulatorMs = 0;
  }
}
