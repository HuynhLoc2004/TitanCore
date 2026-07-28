import { describe, expect, it } from 'vitest';
import type { WorldElevationZoneDefinition } from './manifest';
import { approachElevation, perspectiveScale, sampleElevation } from './traversal';

const zone: WorldElevationZoneDefinition = {
  id: 'wind-lift',
  kind: 'WIND_LIFT',
  x: 500,
  y: 500,
  width: 200,
  height: 100,
  elevation: 80,
  oscillationMs: 2000,
};

describe('2.5D traversal elevation', () => {
  it('keeps ground authority outside lift zones', () => {
    expect(sampleElevation(200, 500, 0, [zone])).toEqual({
      zoneId: null,
      target: 0,
    });
  });

  it('adds bounded visual elevation inside a wind lift', () => {
    expect(sampleElevation(500, 500, 0, [zone])).toEqual({
      zoneId: 'wind-lift',
      target: 80,
    });
    expect(sampleElevation(500, 500, 500, [zone]).target).toBeCloseTo(86.4);
  });

  it('approaches elevation without frame-rate dependent overshoot', () => {
    expect(approachElevation(0, 80, 100)).toBe(26);
    expect(approachElevation(76, 80, 100)).toBe(80);
    expect(approachElevation(40, 0, 100)).toBe(14);
  });
});

describe('perspectiveScale', () => {
  it('makes foreground actors larger without exceeding its bounded range', () => {
    expect(perspectiveScale(420, 420, 680)).toBeCloseTo(0.86);
    expect(perspectiveScale(550, 420, 680)).toBeCloseTo(0.97);
    expect(perspectiveScale(680, 420, 680)).toBeCloseTo(1.08);
    expect(perspectiveScale(200, 420, 680)).toBeCloseTo(0.86);
    expect(perspectiveScale(900, 420, 680)).toBeCloseTo(1.08);
  });
});
