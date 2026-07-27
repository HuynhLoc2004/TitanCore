import type { LobbyBootstrap, LobbyDegradedCode } from './lobbyTypes';

export type LobbyStatus =
  | 'BOOTSTRAPPING'
  | 'READY'
  | 'EMPTY'
  | 'DEGRADED'
  | 'OFFLINE'
  | 'RETRYING'
  | 'AUTH_REQUIRED'
  | 'ONBOARDING_REQUIRED'
  | 'FATAL_ERROR';

export type LobbyState = {
  status: LobbyStatus;
  content: LobbyBootstrap | null;
  degradedCodes: LobbyDegradedCode[];
};

export const initialLobbyState: LobbyState = {
  status: 'BOOTSTRAPPING',
  content: null,
  degradedCodes: [],
};

export function resolvedLobbyState(
  content: LobbyBootstrap,
  clientCodes: LobbyDegradedCode[],
): LobbyState {
  const degradedCodes = [...new Set([...content.degraded.codes, ...clientCodes])].slice(0, 8);
  const hasContent = content.navigation.length > 0 || content.sections.length > 0;
  if (content.degraded.active || degradedCodes.length > 0) {
    return { status: 'DEGRADED', content, degradedCodes };
  }
  return {
    status: hasContent ? 'READY' : 'EMPTY',
    content,
    degradedCodes: [],
  };
}
export function transientLobbyState(
  status: Extract<LobbyStatus, 'OFFLINE' | 'RETRYING'>,
  content: LobbyBootstrap | null,
): LobbyState {
  return { status, content, degradedCodes: content?.degraded.codes ?? [] };
}
