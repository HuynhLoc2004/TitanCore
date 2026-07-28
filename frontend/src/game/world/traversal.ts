import type { WorldElevationZoneDefinition } from './manifest';

export type ElevationSample = {
  zoneId: string | null;
  target: number;
};

export function sampleElevation(
  x: number,
  y: number,
  simulationTimeMs: number,
  zones: readonly WorldElevationZoneDefinition[],
): ElevationSample {
  const zone = zones.find((candidate) => contains(candidate, x, y));
  if (!zone) return { zoneId: null, target: 0 };
  const phase = (simulationTimeMs % zone.oscillationMs) / zone.oscillationMs;
  const floatOffset = Math.sin(phase * Math.PI * 2) * Math.min(8, zone.elevation * 0.08);
  return {
    zoneId: zone.id,
    target: zone.elevation + floatOffset,
  };
}

export function approachElevation(
  current: number,
  target: number,
  deltaMs: number,
  speedPerSecond = 260,
) {
  const maximumStep = speedPerSecond * (Math.max(0, deltaMs) / 1000);
  if (Math.abs(target - current) <= maximumStep) return target;
  return current + Math.sign(target - current) * maximumStep;
}

export function perspectiveScale(
  y: number,
  minY: number,
  maxY: number,
  nearScale = 1.08,
  farScale = 0.86,
) {
  if (maxY <= minY) return 1;
  const progress = Math.min(1, Math.max(0, (y - minY) / (maxY - minY)));
  return farScale + (nearScale - farScale) * progress;
}

function contains(
  zone: Pick<WorldElevationZoneDefinition, 'x' | 'y' | 'width' | 'height'>,
  x: number,
  y: number,
) {
  return Math.abs(x - zone.x) <= zone.width / 2
    && Math.abs(y - zone.y) <= zone.height / 2;
}
