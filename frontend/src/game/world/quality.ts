export type WorldQualityTier = 'LOW' | 'BALANCED' | 'HIGH';

export function selectWorldQuality(
  width: number,
  height: number,
  devicePixelRatio: number,
): WorldQualityTier {
  const safeDpr = Math.min(Math.max(devicePixelRatio || 1, 1), 2);
  const pixelArea = width * height * safeDpr * safeDpr;
  if (width < 760 || height < 430 || pixelArea > 8_000_000) {
    return 'LOW';
  }
  if (width < 1200 || height < 700 || pixelArea > 5_000_000) {
    return 'BALANCED';
  }
  return 'HIGH';
}
