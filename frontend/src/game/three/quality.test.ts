import { describe, expect, it } from 'vitest';
import { selectThreeWorldBudget } from './quality';

describe('selectThreeWorldBudget', () => {
  it('caps low-end mobile rendering cost', () => {
    expect(selectThreeWorldBudget(740, 390, 3)).toEqual({
      quality: 'LOW',
      pixelRatio: 1.25,
      shadowMapSize: 512,
      treeCount: 34,
      cloudCount: 5,
    });
  });

  it('allows a bounded high-quality desktop tier', () => {
    const budget = selectThreeWorldBudget(1440, 900, 1);
    expect(budget.quality).toBe('HIGH');
    expect(budget.pixelRatio).toBeLessThanOrEqual(2);
    expect(budget.shadowMapSize).toBe(2048);
  });

  it('does not accept invalid device pixel ratios', () => {
    expect(selectThreeWorldBudget(1280, 720, 0).pixelRatio).toBe(1);
  });
});
