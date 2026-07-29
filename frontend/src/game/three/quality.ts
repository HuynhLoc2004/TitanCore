export type ThreeWorldQuality = 'LOW' | 'BALANCED' | 'HIGH';

export type ThreeWorldBudget = {
  quality: ThreeWorldQuality;
  pixelRatio: number;
  shadowMapSize: number;
  treeCount: number;
  cloudCount: number;
};

export function selectThreeWorldBudget(
  width: number,
  height: number,
  devicePixelRatio: number,
): ThreeWorldBudget {
  const pixelRatio = Math.min(Math.max(devicePixelRatio || 1, 1), 2);
  const pixelArea = width * height * pixelRatio * pixelRatio;
  if (width < 760 || height < 430 || pixelArea > 7_000_000) {
    return {
      quality: 'LOW',
      pixelRatio: Math.min(pixelRatio, 1.25),
      shadowMapSize: 512,
      treeCount: 34,
      cloudCount: 5,
    };
  }
  if (width < 1280 || height < 720 || pixelArea > 5_000_000) {
    return {
      quality: 'BALANCED',
      pixelRatio: Math.min(pixelRatio, 1.5),
      shadowMapSize: 1024,
      treeCount: 58,
      cloudCount: 8,
    };
  }
  return {
    quality: 'HIGH',
    pixelRatio,
    shadowMapSize: 2048,
    treeCount: 84,
    cloudCount: 11,
  };
}
