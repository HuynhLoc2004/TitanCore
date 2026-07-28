import { describe, expect, it } from 'vitest';
import { parseWorldManifest, WorldManifestError } from './manifest';

function validManifest() {
  return {
    schemaVersion: 1,
    world: {
      id: 'test-world',
      version: 1,
      width: 1280,
      height: 720,
      backgroundColor: '#090b18',
    },
    navigation: {
      minY: 300,
      maxY: 680,
      moveSpeed: 260,
      cameraLerp: 0.09,
    },
    regions: [
      {
        id: 'safe-hub',
        kind: 'SAFE_HUB',
        startX: 0,
        endX: 640,
      },
      {
        id: 'exit-gate',
        kind: 'EXIT_GATE',
        startX: 640,
        endX: 1280,
      },
    ],
    assets: [
      {
        key: 'hero-sheet',
        kind: 'SPRITESHEET',
        url: '/assets/test/hero.png',
        critical: true,
        frameWidth: 128,
        frameHeight: 128,
      },
    ],
    layers: [],
    entities: [
      {
        id: 'hero-one',
        kind: 'HERO',
        assetKey: 'hero-sheet',
        x: 640,
        y: 500,
        frame: 0,
        scale: 1,
        depth: 10,
      },
    ],
    ambient: {
      cloudCount: 4,
      emberCount: 8,
      windCycleMs: 24000,
    },
  };
}

describe('parseWorldManifest', () => {
  it('accepts a bounded local manifest', () => {
    const manifest = parseWorldManifest(validManifest());

    expect(manifest.world.id).toBe('test-world');
    expect(manifest.assets[0]).toMatchObject({
      kind: 'SPRITESHEET',
      frameWidth: 128,
    });
  });

  it('rejects remote URLs and unknown executable-style fields', () => {
    const remote = validManifest();
    remote.assets[0].url = 'https://example.com/hero.png';
    expect(() => parseWorldManifest(remote)).toThrow(WorldManifestError);

    const executable = { ...validManifest(), redirectTarget: '/admin' };
    expect(() => parseWorldManifest(executable)).toThrow(WorldManifestError);
  });

  it('rejects duplicate identities and dangling asset references', () => {
    const duplicate = validManifest();
    duplicate.entities.push({ ...duplicate.entities[0] });
    expect(() => parseWorldManifest(duplicate)).toThrow(/Duplicate entity id/);

    const dangling = validManifest();
    dangling.entities[0].assetKey = 'missing-sheet';
    expect(() => parseWorldManifest(dangling)).toThrow(/Unknown asset reference/);
  });

  it('requires rendered assets to be critical and enforces collection bounds', () => {
    const optional = validManifest();
    optional.assets[0].critical = false;
    expect(() => parseWorldManifest(optional)).toThrow(/must be critical/);

    const excessive = validManifest();
    excessive.entities = Array.from({ length: 129 }, (_, index) => ({
      ...excessive.entities[0],
      id: `hero-${String(index).padStart(3, '0')}`,
    }));
    expect(() => parseWorldManifest(excessive)).toThrow(/bounded runtime collections/);
  });

  it('enforces image layers and spritesheet entities', () => {
    const wrongEntityAsset = validManifest();
    wrongEntityAsset.assets[0] = {
      key: 'hero-sheet',
      kind: 'IMAGE',
      url: '/assets/test/hero.png',
      critical: true,
      frameWidth: 128,
      frameHeight: 128,
    };
    expect(() => parseWorldManifest(wrongEntityAsset)).toThrow();
  });

  it('rejects overlapping, gapped, or incomplete connected regions', () => {
    const gap = validManifest();
    gap.regions[1].startX = 700;
    expect(() => parseWorldManifest(gap)).toThrow(/overlap or leave gaps/);

    const missingExit = validManifest();
    missingExit.regions[1].kind = 'HUNTING';
    expect(() => parseWorldManifest(missingExit)).toThrow(/end with an exit gate/);
  });

  it('rejects unsafe navigation bounds and movement tuning', () => {
    const inverted = validManifest();
    inverted.navigation.minY = 700;
    expect(() => parseWorldManifest(inverted)).toThrow();

    const excessiveSpeed = validManifest();
    excessiveSpeed.navigation.moveSpeed = 900;
    expect(() => parseWorldManifest(excessiveSpeed)).toThrow();
  });
});
