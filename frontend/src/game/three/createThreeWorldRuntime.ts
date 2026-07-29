import * as THREE from 'three';
import { selectThreeWorldBudget } from './quality';
import type {
  ThreeWorldRuntimeHandle,
  ThreeWorldRuntimeOptions,
} from './runtimeTypes';

const WORLD_RADIUS = 46;
const HERO_SPEED = 8.5;
const GRAVITY = 25;
const JUMP_SPEED = 10;
const CAMERA_MIN_DISTANCE = 6;
const CAMERA_MAX_DISTANCE = 16;
const CAMERA_TARGET_HEIGHT = 1.8;

type DisposableObject = THREE.Object3D & {
  geometry?: THREE.BufferGeometry;
  material?: THREE.Material | THREE.Material[];
};

export function createThreeWorldRuntime({
  parent,
  reducedMotion: initialReducedMotion,
  onStatus,
  onMetrics,
}: ThreeWorldRuntimeOptions): ThreeWorldRuntimeHandle {
  const width = Math.max(parent.clientWidth, 1);
  const height = Math.max(parent.clientHeight, 1);
  const budget = selectThreeWorldBudget(width, height, window.devicePixelRatio);
  const renderer = new THREE.WebGLRenderer({
    antialias: budget.quality !== 'LOW',
    alpha: false,
    powerPreference: 'high-performance',
  });
  renderer.setSize(width, height, false);
  renderer.setPixelRatio(budget.pixelRatio);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.08;
  renderer.shadowMap.enabled = budget.quality !== 'LOW';
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.domElement.tabIndex = 0;
  renderer.domElement.setAttribute('aria-label', 'TitanCore stylized 3D world proof');
  parent.replaceChildren(renderer.domElement);

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0x88cfff);
  scene.fog = new THREE.FogExp2(0x91c9e8, 0.015);

  const camera = new THREE.PerspectiveCamera(52, width / height, 0.1, 240);
  const world = new THREE.Group();
  scene.add(world);

  const hemisphere = new THREE.HemisphereLight(0xe5f7ff, 0x46513f, 2.2);
  scene.add(hemisphere);
  const sun = new THREE.DirectionalLight(0xfff1c9, 4.1);
  sun.position.set(-18, 28, 16);
  sun.castShadow = renderer.shadowMap.enabled;
  sun.shadow.mapSize.set(budget.shadowMapSize, budget.shadowMapSize);
  sun.shadow.camera.left = -36;
  sun.shadow.camera.right = 36;
  sun.shadow.camera.top = 36;
  sun.shadow.camera.bottom = -36;
  scene.add(sun);

  createSkyIslands(world);
  createTerrain(world);
  createPath(world);
  createTrees(world, budget.treeCount);
  createCrystals(world);
  const clouds = createClouds(world, budget.cloudCount);
  const hero = createHero(world);

  const keys = new Set<string>();
  let reducedMotion = initialReducedMotion;
  let destroyed = false;
  let sleeping = document.hidden;
  let animationFrame = 0;
  let previousTime = performance.now();
  let metricTime = 0;
  let metricFrames = 0;
  let metricFrameDuration = 0;
  let cameraYaw = Math.PI * 0.83;
  let cameraPitch = 0.43;
  let cameraDistance = 10.5;
  let verticalVelocity = 0;
  let grounded = true;
  let dragging = false;
  let pointerX = 0;
  let pointerY = 0;
  const cameraTarget = new THREE.Vector3();
  const desiredCamera = new THREE.Vector3();
  const movement = new THREE.Vector3();
  const forward = new THREE.Vector3();
  const right = new THREE.Vector3();

  const onKeyDown = (event: KeyboardEvent) => {
    if (event.code === 'Space' && grounded && !event.repeat) {
      verticalVelocity = JUMP_SPEED;
      grounded = false;
    }
    keys.add(event.code);
  };
  const onKeyUp = (event: KeyboardEvent) => keys.delete(event.code);
  const onBlur = () => keys.clear();
  const onPointerDown = (event: PointerEvent) => {
    dragging = true;
    pointerX = event.clientX;
    pointerY = event.clientY;
    renderer.domElement.setPointerCapture(event.pointerId);
  };
  const onPointerMove = (event: PointerEvent) => {
    if (!dragging) return;
    const deltaX = event.clientX - pointerX;
    const deltaY = event.clientY - pointerY;
    pointerX = event.clientX;
    pointerY = event.clientY;
    cameraYaw -= deltaX * 0.005;
    cameraPitch = THREE.MathUtils.clamp(cameraPitch - deltaY * 0.0035, 0.16, 1.08);
  };
  const onPointerUp = (event: PointerEvent) => {
    dragging = false;
    if (renderer.domElement.hasPointerCapture(event.pointerId)) {
      renderer.domElement.releasePointerCapture(event.pointerId);
    }
  };
  const onWheel = (event: WheelEvent) => {
    cameraDistance = THREE.MathUtils.clamp(
      cameraDistance + event.deltaY * 0.008,
      CAMERA_MIN_DISTANCE,
      CAMERA_MAX_DISTANCE,
    );
  };
  const onVisibility = () => {
    sleeping = document.hidden;
    keys.clear();
    onStatus({
      phase: sleeping ? 'SLEEPING' : 'READY',
      message: sleeping ? '3D world paused' : '3D world ready',
    });
    if (!sleeping) previousTime = performance.now();
  };

  renderer.domElement.addEventListener('keydown', onKeyDown);
  renderer.domElement.addEventListener('keyup', onKeyUp);
  renderer.domElement.addEventListener('blur', onBlur);
  renderer.domElement.addEventListener('pointerdown', onPointerDown);
  renderer.domElement.addEventListener('pointermove', onPointerMove);
  renderer.domElement.addEventListener('pointerup', onPointerUp);
  renderer.domElement.addEventListener('pointercancel', onPointerUp);
  renderer.domElement.addEventListener('wheel', onWheel, { passive: true });
  document.addEventListener('visibilitychange', onVisibility);

  const resizeObserver = new ResizeObserver(() => {
    const nextWidth = Math.max(parent.clientWidth, 1);
    const nextHeight = Math.max(parent.clientHeight, 1);
    camera.aspect = nextWidth / nextHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(nextWidth, nextHeight, false);
  });
  resizeObserver.observe(parent);

  const updateHero = (delta: number, elapsed: number) => {
    forward.set(-Math.sin(cameraYaw), 0, -Math.cos(cameraYaw));
    right.set(Math.cos(cameraYaw), 0, -Math.sin(cameraYaw));
    movement.set(0, 0, 0);
    if (keys.has('KeyW') || keys.has('ArrowUp')) movement.add(forward);
    if (keys.has('KeyS') || keys.has('ArrowDown')) movement.sub(forward);
    if (keys.has('KeyD') || keys.has('ArrowRight')) movement.add(right);
    if (keys.has('KeyA') || keys.has('ArrowLeft')) movement.sub(right);
    if (movement.lengthSq() > 0) {
      movement.normalize();
      hero.position.addScaledVector(movement, HERO_SPEED * delta);
      hero.rotation.y = Math.atan2(movement.x, movement.z);
      const stride = Math.sin(elapsed * 11) * 0.38;
      hero.userData.leftLeg.rotation.x = stride;
      hero.userData.rightLeg.rotation.x = -stride;
      hero.userData.leftArm.rotation.x = -stride * 0.65;
      hero.userData.rightArm.rotation.x = stride * 0.65;
      hero.userData.cape.rotation.x = -0.22 - Math.abs(stride) * 0.25;
    } else {
      const breath = Math.sin(elapsed * 2.2);
      hero.userData.leftLeg.rotation.x *= 0.82;
      hero.userData.rightLeg.rotation.x *= 0.82;
      hero.userData.leftArm.rotation.x *= 0.82;
      hero.userData.rightArm.rotation.x *= 0.82;
      hero.userData.cape.rotation.x = -0.12 + breath * 0.025;
    }
    hero.position.x = THREE.MathUtils.clamp(hero.position.x, -WORLD_RADIUS, WORLD_RADIUS);
    hero.position.z = THREE.MathUtils.clamp(hero.position.z, -WORLD_RADIUS, WORLD_RADIUS);

    if (!grounded && keys.has('Space') && verticalVelocity < 0) {
      verticalVelocity = Math.max(verticalVelocity, -2.4);
      hero.userData.wingLeft.visible = true;
      hero.userData.wingRight.visible = true;
    } else {
      hero.userData.wingLeft.visible = false;
      hero.userData.wingRight.visible = false;
    }
    verticalVelocity -= GRAVITY * delta;
    hero.position.y += verticalVelocity * delta;
    const floorHeight = terrainHeight(hero.position.x, hero.position.z);
    if (hero.position.y <= floorHeight) {
      hero.position.y = floorHeight;
      verticalVelocity = 0;
      grounded = true;
    }
  };

  const updateCamera = (delta: number) => {
    const compositionOffset = 0.48;
    cameraTarget.set(
      hero.position.x + Math.cos(cameraYaw) * compositionOffset,
      hero.position.y + CAMERA_TARGET_HEIGHT,
      hero.position.z - Math.sin(cameraYaw) * compositionOffset,
    );
    const horizontalDistance = Math.cos(cameraPitch) * cameraDistance;
    desiredCamera.set(
      cameraTarget.x + Math.sin(cameraYaw) * horizontalDistance,
      cameraTarget.y + Math.sin(cameraPitch) * cameraDistance,
      cameraTarget.z + Math.cos(cameraYaw) * horizontalDistance,
    );
    desiredCamera.y = Math.max(
      desiredCamera.y,
      terrainHeight(desiredCamera.x, desiredCamera.z) + 1.1,
    );
    const damping = reducedMotion ? 1 : 1 - Math.exp(-delta * 12);
    camera.position.lerp(desiredCamera, damping);
    camera.lookAt(cameraTarget);
  };

  const frame = (time: number) => {
    if (destroyed) return;
    animationFrame = window.requestAnimationFrame(frame);
    if (sleeping) return;
    const frameDuration = Math.min(time - previousTime, 50);
    previousTime = time;
    const delta = frameDuration / 1000;
    const elapsed = time / 1000;
    updateHero(delta, elapsed);
    updateCamera(delta);
    if (!reducedMotion) {
      clouds.forEach((cloud, index) => {
        cloud.position.x += delta * (0.28 + index * 0.012);
        if (cloud.position.x > 56) cloud.position.x = -56;
      });
    }
    renderer.render(scene, camera);

    metricTime += frameDuration;
    metricFrameDuration += frameDuration;
    metricFrames += 1;
    if (metricTime >= 1000) {
      onMetrics({
        fps: Math.round((metricFrames * 1000) / metricTime),
        frameTimeMs: Number((metricFrameDuration / metricFrames).toFixed(1)),
        drawCalls: renderer.info.render.calls,
        triangles: renderer.info.render.triangles,
        geometries: renderer.info.memory.geometries,
        textures: renderer.info.memory.textures,
        quality: budget.quality,
      });
      metricTime = 0;
      metricFrames = 0;
      metricFrameDuration = 0;
    }
  };

  updateCamera(1);
  onStatus({ phase: 'READY', message: '3D world ready' });
  renderer.domElement.focus();
  animationFrame = window.requestAnimationFrame(frame);

  return {
    setReducedMotion(nextReducedMotion) {
      reducedMotion = nextReducedMotion;
    },
    destroy() {
      if (destroyed) return;
      destroyed = true;
      window.cancelAnimationFrame(animationFrame);
      resizeObserver.disconnect();
      renderer.domElement.removeEventListener('keydown', onKeyDown);
      renderer.domElement.removeEventListener('keyup', onKeyUp);
      renderer.domElement.removeEventListener('blur', onBlur);
      renderer.domElement.removeEventListener('pointerdown', onPointerDown);
      renderer.domElement.removeEventListener('pointermove', onPointerMove);
      renderer.domElement.removeEventListener('pointerup', onPointerUp);
      renderer.domElement.removeEventListener('pointercancel', onPointerUp);
      renderer.domElement.removeEventListener('wheel', onWheel);
      document.removeEventListener('visibilitychange', onVisibility);
      scene.traverse(disposeObject);
      renderer.renderLists.dispose();
      renderer.dispose();
      parent.replaceChildren();
    },
  };
}

function createTerrain(world: THREE.Group) {
  const geometry = new THREE.PlaneGeometry(104, 104, 48, 48);
  geometry.rotateX(-Math.PI / 2);
  const positions = geometry.attributes.position;
  for (let index = 0; index < positions.count; index += 1) {
    positions.setY(
      index,
      terrainHeight(positions.getX(index), positions.getZ(index)),
    );
  }
  geometry.computeVertexNormals();
  const terrain = new THREE.Mesh(
    geometry,
    new THREE.MeshToonMaterial({ color: 0x75ad68 }),
  );
  terrain.receiveShadow = true;
  world.add(terrain);
}

function terrainHeight(x: number, z: number) {
  const centerFlattening = Math.min(Math.hypot(x, z) / 11, 1);
  return (
    Math.sin(x * 0.13) * 0.8
    + Math.cos(z * 0.11) * 0.65
    + Math.sin((x + z) * 0.075) * 0.55
  ) * centerFlattening;
}

function createPath(world: THREE.Group) {
  const pathMaterial = new THREE.MeshToonMaterial({ color: 0xd6b77a });
  for (let index = -9; index <= 9; index += 1) {
    const z = index * 4.1;
    const x = Math.sin(index * 0.62) * 3.4;
    const stone = new THREE.Mesh(
      new THREE.CylinderGeometry(2.4, 2.8, 0.34, 7),
      pathMaterial,
    );
    stone.position.set(x, terrainHeight(x, z) + 0.08, z);
    stone.scale.z = 0.76;
    stone.receiveShadow = true;
    world.add(stone);
  }
}

function createTrees(world: THREE.Group, count: number) {
  const trunkGeometry = new THREE.CylinderGeometry(0.24, 0.36, 2.5, 7);
  const crownGeometry = new THREE.IcosahedronGeometry(1.25, 1);
  const trunkMaterial = new THREE.MeshToonMaterial({ color: 0x76513b });
  const crownMaterials = [
    new THREE.MeshToonMaterial({ color: 0x286f58 }),
    new THREE.MeshToonMaterial({ color: 0x3c8f62 }),
    new THREE.MeshToonMaterial({ color: 0x5aa75d }),
  ];
  for (let index = 0; index < count; index += 1) {
    const angle = index * 2.399963;
    const radius = 13 + ((index * 17) % 32);
    const x = Math.cos(angle) * radius;
    const z = Math.sin(angle) * radius;
    if (Math.abs(x) < 5 && Math.abs(z) < 39) continue;
    const y = terrainHeight(x, z);
    const tree = new THREE.Group();
    const trunk = new THREE.Mesh(trunkGeometry, trunkMaterial);
    trunk.position.y = 1.25;
    trunk.castShadow = true;
    const crown = new THREE.Mesh(
      crownGeometry,
      crownMaterials[index % crownMaterials.length],
    );
    crown.position.y = 3;
    crown.scale.set(1, 1.18 + (index % 3) * 0.12, 1);
    crown.castShadow = true;
    tree.add(trunk, crown);
    tree.position.set(x, y, z);
    tree.rotation.y = angle;
    world.add(tree);
  }
}

function createCrystals(world: THREE.Group) {
  const colors = [0x8b5cf6, 0x38d9c5, 0xffcd57];
  for (let index = 0; index < 18; index += 1) {
    const angle = index * 1.93;
    const radius = 15 + (index % 6) * 4.8;
    const x = Math.cos(angle) * radius;
    const z = Math.sin(angle) * radius;
    const crystal = new THREE.Mesh(
      new THREE.OctahedronGeometry(0.5 + (index % 3) * 0.16, 0),
      new THREE.MeshToonMaterial({
        color: colors[index % colors.length],
        emissive: colors[index % colors.length],
        emissiveIntensity: 0.18,
      }),
    );
    crystal.position.set(x, terrainHeight(x, z) + 0.7, z);
    crystal.rotation.z = 0.12;
    crystal.castShadow = true;
    world.add(crystal);
  }
}

function createClouds(world: THREE.Group, count: number) {
  const clouds: THREE.Group[] = [];
  const material = new THREE.MeshToonMaterial({
    color: 0xf4fbff,
    transparent: true,
    opacity: 0.82,
  });
  for (let index = 0; index < count; index += 1) {
    const cloud = new THREE.Group();
    for (let puff = 0; puff < 5; puff += 1) {
      const mesh = new THREE.Mesh(
        new THREE.SphereGeometry(1.4 + (puff % 2) * 0.45, 10, 8),
        material,
      );
      mesh.position.set(puff * 1.45, Math.sin(puff) * 0.35, (puff % 2) * 0.5);
      cloud.add(mesh);
    }
    cloud.position.set(
      -50 + index * (100 / Math.max(count - 1, 1)),
      13 + (index % 4) * 3.2,
      -26 + (index % 5) * 13,
    );
    cloud.scale.setScalar(0.8 + (index % 3) * 0.18);
    world.add(cloud);
    clouds.push(cloud);
  }
  return clouds;
}

function createSkyIslands(world: THREE.Group) {
  const rockMaterial = new THREE.MeshToonMaterial({ color: 0x516071 });
  const grassMaterial = new THREE.MeshToonMaterial({ color: 0x5d9b62 });
  for (let index = 0; index < 9; index += 1) {
    const angle = index * 0.82;
    const radius = 64 + (index % 3) * 11;
    const island = new THREE.Group();
    const rock = new THREE.Mesh(
      new THREE.ConeGeometry(4.8 + (index % 2), 8, 7),
      rockMaterial,
    );
    rock.rotation.z = Math.PI;
    const top = new THREE.Mesh(
      new THREE.CylinderGeometry(4.8, 5.2, 0.9, 7),
      grassMaterial,
    );
    top.position.y = 3.65;
    island.add(rock, top);
    island.position.set(
      Math.cos(angle) * radius,
      13 + (index % 4) * 3,
      Math.sin(angle) * radius,
    );
    world.add(island);
  }
}

function createHero(world: THREE.Group) {
  const hero = new THREE.Group();
  const skin = new THREE.MeshToonMaterial({ color: 0xffc58f });
  const coat = new THREE.MeshToonMaterial({ color: 0x17243f });
  const coral = new THREE.MeshToonMaterial({ color: 0xe94d5f });
  const gold = new THREE.MeshToonMaterial({ color: 0xffc857 });
  const boot = new THREE.MeshToonMaterial({ color: 0x392e35 });

  const torso = new THREE.Mesh(new THREE.CapsuleGeometry(0.62, 1.15, 6, 10), coat);
  torso.position.y = 2.25;
  const head = new THREE.Mesh(new THREE.SphereGeometry(0.66, 18, 14), skin);
  head.position.y = 3.65;
  const hair = new THREE.Mesh(new THREE.ConeGeometry(0.78, 0.95, 7), coral);
  hair.position.y = 4.22;
  hair.rotation.z = -0.12;
  const scarf = new THREE.Mesh(new THREE.TorusGeometry(0.63, 0.16, 8, 18), coral);
  scarf.position.y = 3.12;
  scarf.rotation.x = Math.PI / 2;
  const belt = new THREE.Mesh(new THREE.TorusGeometry(0.59, 0.11, 8, 18), gold);
  belt.position.y = 1.85;
  belt.rotation.x = Math.PI / 2;
  const cape = new THREE.Mesh(new THREE.PlaneGeometry(1.25, 1.8), coral);
  cape.position.set(0, 2.45, -0.58);
  cape.rotation.x = -0.12;

  const leftLeg = createLimb(boot, 0.24, 1.25);
  leftLeg.position.set(-0.3, 1.2, 0);
  const rightLeg = createLimb(boot, 0.24, 1.25);
  rightLeg.position.set(0.3, 1.2, 0);
  const leftArm = createLimb(skin, 0.18, 1.05);
  leftArm.position.set(-0.78, 2.65, 0);
  leftArm.rotation.z = -0.12;
  const rightArm = createLimb(skin, 0.18, 1.05);
  rightArm.position.set(0.78, 2.65, 0);
  rightArm.rotation.z = 0.12;

  const wingMaterial = new THREE.MeshToonMaterial({
    color: 0x69e8ff,
    emissive: 0x1a94c2,
    emissiveIntensity: 0.4,
    transparent: true,
    opacity: 0.78,
    side: THREE.DoubleSide,
  });
  const wingLeft = new THREE.Mesh(new THREE.CircleGeometry(1.25, 3), wingMaterial);
  wingLeft.position.set(-1.2, 2.5, -0.45);
  wingLeft.rotation.set(0.15, -0.25, 0.65);
  wingLeft.visible = false;
  const wingRight = wingLeft.clone();
  wingRight.position.x = 1.2;
  wingRight.rotation.z = -0.65;

  hero.add(
    torso,
    head,
    hair,
    scarf,
    belt,
    cape,
    leftLeg,
    rightLeg,
    leftArm,
    rightArm,
    wingLeft,
    wingRight,
  );
  hero.position.set(0, terrainHeight(0, 0), 8);
  hero.traverse((object) => {
    if (object instanceof THREE.Mesh) {
      object.castShadow = true;
      object.receiveShadow = true;
    }
  });
  hero.userData = {
    leftLeg,
    rightLeg,
    leftArm,
    rightArm,
    cape,
    wingLeft,
    wingRight,
  };
  world.add(hero);
  return hero;
}

function createLimb(material: THREE.Material, radius: number, length: number) {
  const pivot = new THREE.Group();
  const mesh = new THREE.Mesh(
    new THREE.CapsuleGeometry(radius, length, 5, 8),
    material,
  );
  mesh.position.y = -length * 0.5;
  pivot.add(mesh);
  return pivot;
}

function disposeObject(object: THREE.Object3D) {
  const disposable = object as DisposableObject;
  disposable.geometry?.dispose();
  const materials = Array.isArray(disposable.material)
    ? disposable.material
    : disposable.material ? [disposable.material] : [];
  materials.forEach((material) => material.dispose());
}
