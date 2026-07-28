import { ApiError, apiFetch } from '../auth/api';
import type { LobbyBootstrap } from './types';

const supportedSections = new Set([
  'HERO_BANNER',
  'ANNOUNCEMENT_STRIP',
  'BOSS_ROOM_LIST',
  'PLAYER_SUMMARY',
  'CHARACTER_PREVIEW',
  'INVENTORY_PREVIEW',
  'EVENT_SPOTLIGHT',
]);

export async function fetchLobbyBootstrap(signal?: AbortSignal) {
  const response = await apiFetch<LobbyBootstrap>('/api/lobby/bootstrap', {
    method: 'GET',
    headers: { 'Accept-Language': navigator.language },
    signal,
  });
  if (response.schemaVersion !== 1
      || typeof response.player?.displayName !== 'string'
      || !Array.isArray(response.navigation)
      || !Array.isArray(response.sections)
      || !response.sections.every((section) => supportedSections.has(section.type))) {
    throw new ApiError(503, 'LOBBY_SCHEMA_UNSUPPORTED', 'The camp signal needs an update.');
  }
  return response;
}
