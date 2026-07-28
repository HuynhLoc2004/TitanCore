import { describe, expect, it } from 'vitest';
import { selectWorldQuality } from './quality';

describe('selectWorldQuality', () => {
  it('selects a bounded tier from usable dimensions and capped DPR', () => {
    expect(selectWorldQuality(1440, 900, 1)).toBe('HIGH');
    expect(selectWorldQuality(1440, 900, 2)).toBe('BALANCED');
    expect(selectWorldQuality(844, 390, 3)).toBe('LOW');
  });
});
