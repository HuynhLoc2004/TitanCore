export type WorldAssetKind = 'IMAGE' | 'SPRITESHEET';
export type WorldEntityKind = 'HERO' | 'MONSTER';
export type WorldRegionKind = 'SAFE_HUB' | 'HUNTING' | 'EVENT' | 'BOSS' | 'EXIT_GATE';
export type ElevationZoneKind = 'WIND_LIFT';

type WorldAssetBase = {
  key: string;
  url: string;
  critical: boolean;
};

export type WorldAssetDefinition =
  | (WorldAssetBase & {
    kind: 'IMAGE';
  })
  | (WorldAssetBase & {
    kind: 'SPRITESHEET';
    frameWidth: number;
    frameHeight: number;
  });

export type WorldLayerDefinition = {
  id: string;
  assetKey: string;
  depth: number;
  x: number;
  y: number;
  width: number;
  height: number;
  scrollFactorX: number;
  scrollFactorY: number;
  flipX: boolean;
};

export type WorldRegionDefinition = {
  id: string;
  kind: WorldRegionKind;
  startX: number;
  endX: number;
};

export type WorldEntityDefinition = {
  id: string;
  kind: WorldEntityKind;
  assetKey: string;
  x: number;
  y: number;
  frame: number;
  scale: number;
  depth: number;
};

export type WorldCollisionDefinition = {
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
};

export type WorldElevationZoneDefinition = WorldCollisionDefinition & {
  kind: ElevationZoneKind;
  elevation: number;
  oscillationMs: number;
};

export type WorldManifest = {
  schemaVersion: 2;
  world: {
    id: string;
    version: number;
    width: number;
    height: number;
    backgroundColor: string;
  };
  navigation: {
    minY: number;
    maxY: number;
    moveSpeed: number;
    cameraLerp: number;
  };
  regions: WorldRegionDefinition[];
  collision: WorldCollisionDefinition[];
  elevationZones: WorldElevationZoneDefinition[];
  assets: WorldAssetDefinition[];
  layers: WorldLayerDefinition[];
  entities: WorldEntityDefinition[];
  ambient: {
    cloudCount: number;
    emberCount: number;
    windCycleMs: number;
  };
};

const SAFE_ID = /^[a-z0-9][a-z0-9-]{1,63}$/;
const SAFE_ASSET_URL = /^\/assets\/[a-zA-Z0-9/_-]+\.(?:jpg|jpeg|png|webp)$/;
const SAFE_COLOR = /^#[0-9a-fA-F]{6}$/;

export class WorldManifestError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'WorldManifestError';
  }
}

export function parseWorldManifest(value: unknown): WorldManifest {
  const manifest = requireRecord(value, 'manifest');
  requireExactKeys(
    manifest,
    [
      'schemaVersion',
      'world',
      'navigation',
      'regions',
      'collision',
      'elevationZones',
      'assets',
      'layers',
      'entities',
      'ambient',
    ],
    'manifest',
  );
  if (manifest.schemaVersion !== 2) {
    throw new WorldManifestError('Unsupported world manifest schema version.');
  }

  const world = parseWorld(manifest.world);
  const navigation = parseNavigation(manifest.navigation, world.height);
  const regions = requireArray(manifest.regions, 'regions').map(parseRegion);
  const collision = requireArray(manifest.collision, 'collision').map(parseCollision);
  const elevationZones = requireArray(manifest.elevationZones, 'elevationZones')
    .map(parseElevationZone);
  const assets = requireArray(manifest.assets, 'assets').map(parseAsset);
  const layers = requireArray(manifest.layers, 'layers').map(parseLayer);
  const entities = requireArray(manifest.entities, 'entities').map(parseEntity);
  const ambient = parseAmbient(manifest.ambient);

  requireUnique(assets.map((asset) => asset.key), 'asset key');
  requireUnique(regions.map((region) => region.id), 'region id');
  requireUnique(collision.map((entry) => entry.id), 'collision id');
  requireUnique(elevationZones.map((zone) => zone.id), 'elevation zone id');
  requireUnique(layers.map((layer) => layer.id), 'layer id');
  requireUnique(entities.map((entity) => entity.id), 'entity id');

  const assetKeys = new Set(assets.map((asset) => asset.key));
  const criticalAssetKeys = new Set(
    assets.filter((asset) => asset.critical).map((asset) => asset.key),
  );
  [...layers, ...entities].forEach((entry) => {
    if (!assetKeys.has(entry.assetKey)) {
      throw new WorldManifestError(`Unknown asset reference: ${entry.assetKey}.`);
    }
    if (!criticalAssetKeys.has(entry.assetKey)) {
      throw new WorldManifestError(`Rendered asset must be critical: ${entry.assetKey}.`);
    }
  });
  const assetsByKey = new Map(assets.map((asset) => [asset.key, asset]));
  layers.forEach((layer) => {
    if (assetsByKey.get(layer.assetKey)?.kind !== 'IMAGE') {
      throw new WorldManifestError(`Layer asset must be an image: ${layer.assetKey}.`);
    }
  });
  entities.forEach((entity) => {
    if (assetsByKey.get(entity.assetKey)?.kind !== 'SPRITESHEET') {
      throw new WorldManifestError(`Entity asset must be a spritesheet: ${entity.assetKey}.`);
    }
  });
  validateRegions(regions, world.width);
  [...collision, ...elevationZones].forEach((entry) => {
    if (entry.x - entry.width / 2 < 0 || entry.x + entry.width / 2 > world.width
        || entry.y - entry.height / 2 < navigation.minY
        || entry.y + entry.height / 2 > navigation.maxY) {
      throw new WorldManifestError(`Traversal entry exceeds navigation bounds: ${entry.id}.`);
    }
  });
  layers.forEach((layer) => {
    if (layer.x - layer.width / 2 < 0 || layer.x + layer.width / 2 > world.width
        || layer.y - layer.height / 2 < 0 || layer.y + layer.height / 2 > world.height) {
      throw new WorldManifestError(`Layer exceeds world bounds: ${layer.id}.`);
    }
  });
  entities.forEach((entity) => {
    if (entity.x > world.width || entity.y > world.height) {
      throw new WorldManifestError(`Entity exceeds world bounds: ${entity.id}.`);
    }
  });
  if (assets.length === 0 || assets.length > 64 || layers.length > 32 || entities.length > 128
      || collision.length > 64 || elevationZones.length > 16) {
    throw new WorldManifestError('World manifest exceeds bounded runtime collections.');
  }

  return {
    schemaVersion: 2,
    world,
    navigation,
    regions,
    collision,
    elevationZones,
    assets,
    layers,
    entities,
    ambient,
  };
}

function parseCollision(value: unknown, index: number): WorldCollisionDefinition {
  return parseBoundedRectangle(value, `collision[${index}]`);
}

function parseElevationZone(value: unknown, index: number): WorldElevationZoneDefinition {
  const label = `elevationZones[${index}]`;
  const zone = requireRecord(value, label);
  requireExactKeys(
    zone,
    ['id', 'kind', 'x', 'y', 'width', 'height', 'elevation', 'oscillationMs'],
    label,
  );
  return {
    ...parseBoundedRectangle(zone, label, false),
    kind: requireEnum(zone.kind, ['WIND_LIFT'] as const, `${label}.kind`),
    elevation: requireNumber(zone.elevation, 24, 160, `${label}.elevation`),
    oscillationMs: requireInteger(
      zone.oscillationMs,
      800,
      10_000,
      `${label}.oscillationMs`,
    ),
  };
}

function parseBoundedRectangle(
  value: unknown,
  label: string,
  validateKeys = true,
): WorldCollisionDefinition {
  const rectangle = requireRecord(value, label);
  if (validateKeys) requireExactKeys(rectangle, ['id', 'x', 'y', 'width', 'height'], label);
  return {
    id: requireId(rectangle.id, `${label}.id`),
    x: requireNumber(rectangle.x, 0, 16_384, `${label}.x`),
    y: requireNumber(rectangle.y, 0, 16_384, `${label}.y`),
    width: requireNumber(rectangle.width, 8, 2048, `${label}.width`),
    height: requireNumber(rectangle.height, 8, 2048, `${label}.height`),
  };
}

function parseNavigation(value: unknown, worldHeight: number): WorldManifest['navigation'] {
  const navigation = requireRecord(value, 'navigation');
  requireExactKeys(navigation, ['minY', 'maxY', 'moveSpeed', 'cameraLerp'], 'navigation');
  const minY = requireNumber(navigation.minY, 0, worldHeight, 'navigation.minY');
  const maxY = requireNumber(navigation.maxY, 0, worldHeight, 'navigation.maxY');
  if (maxY <= minY) throw new WorldManifestError('navigation must have a positive vertical range.');
  return {
    minY,
    maxY,
    moveSpeed: requireNumber(navigation.moveSpeed, 60, 600, 'navigation.moveSpeed'),
    cameraLerp: requireNumber(navigation.cameraLerp, 0.01, 0.3, 'navigation.cameraLerp'),
  };
}

function parseWorld(value: unknown): WorldManifest['world'] {
  const world = requireRecord(value, 'world');
  requireExactKeys(world, ['id', 'version', 'width', 'height', 'backgroundColor'], 'world');
  return {
    id: requireId(world.id, 'world.id'),
    version: requireInteger(world.version, 1, 1_000_000, 'world.version'),
    width: requireInteger(world.width, 1280, 16_384, 'world.width'),
    height: requireInteger(world.height, 720, 16_384, 'world.height'),
    backgroundColor: requirePattern(world.backgroundColor, SAFE_COLOR, 'world.backgroundColor'),
  };
}

function parseAsset(value: unknown, index: number): WorldAssetDefinition {
  const label = `assets[${index}]`;
  const asset = requireRecord(value, label);
  requireAllowedKeys(
    asset,
    ['key', 'kind', 'url', 'critical', 'frameWidth', 'frameHeight'],
    label,
  );
  const kind = requireEnum(asset.kind, ['IMAGE', 'SPRITESHEET'] as const, `${label}.kind`);
  const base: WorldAssetBase = {
    key: requireId(asset.key, `${label}.key`),
    url: requirePattern(asset.url, SAFE_ASSET_URL, `${label}.url`),
    critical: requireBoolean(asset.critical, `${label}.critical`),
  };
  if (kind === 'SPRITESHEET') {
    return {
      ...base,
      kind,
      frameWidth: requireInteger(asset.frameWidth, 1, 4096, `${label}.frameWidth`),
      frameHeight: requireInteger(asset.frameHeight, 1, 4096, `${label}.frameHeight`),
    };
  } else if (asset.frameWidth !== undefined || asset.frameHeight !== undefined) {
    throw new WorldManifestError(`${label} image cannot define sprite frame dimensions.`);
  }
  return { ...base, kind };
}

function parseRegion(value: unknown, index: number): WorldRegionDefinition {
  const label = `regions[${index}]`;
  const region = requireRecord(value, label);
  requireExactKeys(region, ['id', 'kind', 'startX', 'endX'], label);
  const startX = requireNumber(region.startX, 0, 16_384, `${label}.startX`);
  const endX = requireNumber(region.endX, 1, 16_384, `${label}.endX`);
  if (endX <= startX) {
    throw new WorldManifestError(`${label} must have a positive range.`);
  }
  return {
    id: requireId(region.id, `${label}.id`),
    kind: requireEnum(
      region.kind,
      ['SAFE_HUB', 'HUNTING', 'EVENT', 'BOSS', 'EXIT_GATE'] as const,
      `${label}.kind`,
    ),
    startX,
    endX,
  };
}

function parseLayer(value: unknown, index: number): WorldLayerDefinition {
  const label = `layers[${index}]`;
  const layer = requireRecord(value, label);
  requireExactKeys(
    layer,
    [
      'id',
      'assetKey',
      'depth',
      'x',
      'y',
      'width',
      'height',
      'scrollFactorX',
      'scrollFactorY',
      'flipX',
    ],
    label,
  );
  return {
    id: requireId(layer.id, `${label}.id`),
    assetKey: requireId(layer.assetKey, `${label}.assetKey`),
    depth: requireNumber(layer.depth, -1000, 1000, `${label}.depth`),
    x: requireNumber(layer.x, 0, 16_384, `${label}.x`),
    y: requireNumber(layer.y, 0, 16_384, `${label}.y`),
    width: requireNumber(layer.width, 1, 16_384, `${label}.width`),
    height: requireNumber(layer.height, 1, 16_384, `${label}.height`),
    scrollFactorX: requireNumber(layer.scrollFactorX, 0, 1, `${label}.scrollFactorX`),
    scrollFactorY: requireNumber(layer.scrollFactorY, 0, 1, `${label}.scrollFactorY`),
    flipX: requireBoolean(layer.flipX, `${label}.flipX`),
  };
}

function parseEntity(value: unknown, index: number): WorldEntityDefinition {
  const label = `entities[${index}]`;
  const entity = requireRecord(value, label);
  requireExactKeys(
    entity,
    ['id', 'kind', 'assetKey', 'x', 'y', 'frame', 'scale', 'depth'],
    label,
  );
  return {
    id: requireId(entity.id, `${label}.id`),
    kind: requireEnum(entity.kind, ['HERO', 'MONSTER'] as const, `${label}.kind`),
    assetKey: requireId(entity.assetKey, `${label}.assetKey`),
    x: requireNumber(entity.x, 0, 16_384, `${label}.x`),
    y: requireNumber(entity.y, 0, 16_384, `${label}.y`),
    frame: requireInteger(entity.frame, 0, 4096, `${label}.frame`),
    scale: requireNumber(entity.scale, 0.05, 4, `${label}.scale`),
    depth: requireNumber(entity.depth, -1000, 1000, `${label}.depth`),
  };
}

function parseAmbient(value: unknown): WorldManifest['ambient'] {
  const ambient = requireRecord(value, 'ambient');
  requireExactKeys(ambient, ['cloudCount', 'emberCount', 'windCycleMs'], 'ambient');
  return {
    cloudCount: requireInteger(ambient.cloudCount, 0, 16, 'ambient.cloudCount'),
    emberCount: requireInteger(ambient.emberCount, 0, 64, 'ambient.emberCount'),
    windCycleMs: requireInteger(ambient.windCycleMs, 4_000, 120_000, 'ambient.windCycleMs'),
  };
}

function requireRecord(value: unknown, label: string): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new WorldManifestError(`${label} must be an object.`);
  }
  return value as Record<string, unknown>;
}

function requireArray(value: unknown, label: string): unknown[] {
  if (!Array.isArray(value)) {
    throw new WorldManifestError(`${label} must be an array.`);
  }
  return value;
}

function requireExactKeys(
  value: Record<string, unknown>,
  keys: string[],
  label: string,
) {
  requireAllowedKeys(value, keys, label);
  keys.forEach((key) => {
    if (!(key in value)) {
      throw new WorldManifestError(`${label}.${key} is required.`);
    }
  });
}

function requireAllowedKeys(
  value: Record<string, unknown>,
  keys: string[],
  label: string,
) {
  const allowed = new Set(keys);
  Object.keys(value).forEach((key) => {
    if (!allowed.has(key)) {
      throw new WorldManifestError(`${label}.${key} is not allowed.`);
    }
  });
}

function requireId(value: unknown, label: string): string {
  return requirePattern(value, SAFE_ID, label);
}

function requirePattern(value: unknown, pattern: RegExp, label: string): string {
  if (typeof value !== 'string' || !pattern.test(value)) {
    throw new WorldManifestError(`${label} has an invalid format.`);
  }
  return value;
}

function requireBoolean(value: unknown, label: string): boolean {
  if (typeof value !== 'boolean') {
    throw new WorldManifestError(`${label} must be a boolean.`);
  }
  return value;
}

function requireNumber(value: unknown, minimum: number, maximum: number, label: string): number {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < minimum || value > maximum) {
    throw new WorldManifestError(`${label} is outside its allowed range.`);
  }
  return value;
}

function requireInteger(value: unknown, minimum: number, maximum: number, label: string): number {
  const number = requireNumber(value, minimum, maximum, label);
  if (!Number.isInteger(number)) {
    throw new WorldManifestError(`${label} must be an integer.`);
  }
  return number;
}

function requireEnum<T extends string>(
  value: unknown,
  allowed: readonly T[],
  label: string,
): T {
  if (typeof value !== 'string' || !allowed.includes(value as T)) {
    throw new WorldManifestError(`${label} is not supported.`);
  }
  return value as T;
}

function requireUnique(values: string[], label: string) {
  if (new Set(values).size !== values.length) {
    throw new WorldManifestError(`Duplicate ${label}.`);
  }
}

function validateRegions(regions: WorldRegionDefinition[], worldWidth: number) {
  if (regions.length < 2 || regions.length > 32) {
    throw new WorldManifestError('Connected world requires a bounded region sequence.');
  }
  if (regions[0].kind !== 'SAFE_HUB' || regions[0].startX !== 0) {
    throw new WorldManifestError('Connected world must begin with a safe hub.');
  }
  const finalRegion = regions[regions.length - 1];
  if (finalRegion.kind !== 'EXIT_GATE' || finalRegion.endX !== worldWidth) {
    throw new WorldManifestError('Connected world must end with an exit gate.');
  }
  regions.forEach((region, index) => {
    if (region.endX > worldWidth) {
      throw new WorldManifestError(`Region exceeds world bounds: ${region.id}.`);
    }
    if (index > 0 && region.startX !== regions[index - 1].endX) {
      throw new WorldManifestError('Connected world regions cannot overlap or leave gaps.');
    }
  });
}
