import { CircleAlert, CloudOff, LoaderCircle, RadioTower, RotateCw } from 'lucide-react';
import type { LobbyStatus } from '../model/lobbyState';

const copy: Record<Exclude<LobbyStatus, 'READY' | 'DEGRADED'>, {
  eyebrow: string;
  title: string;
  detail: string;
}> = {
  BOOTSTRAPPING: {
    eyebrow: 'Raid signal',
    title: 'Reading the raid board',
    detail: 'Checking the latest published missions for your banner.',
  },
  EMPTY: {
    eyebrow: 'Raid board',
    title: 'No raids are published right now',
    detail: 'Your camp is ready. New approved raid content will appear here when it is published.',
  },
  OFFLINE: {
    eyebrow: 'Connection paused',
    title: 'The raid signal is offline',
    detail: 'Reconnect to refresh the board. Any safe content shown below belongs only to this session.',
  },
  RETRYING: {
    eyebrow: 'Reconnecting',
    title: 'Calling the raid board again',
    detail: 'The camp remains available while the latest content is checked.',
  },
  AUTH_REQUIRED: {
    eyebrow: 'Raid pass expired',
    title: 'Sign in again to continue',
    detail: 'Lobby content has been cleared from this browser session.',
  },
  ONBOARDING_REQUIRED: {
    eyebrow: 'Banner required',
    title: 'Finish your raid identity first',
    detail: 'Complete profile onboarding before opening the raid board.',
  },
  FATAL_ERROR: {
    eyebrow: 'Board unavailable',
    title: 'The raid board could not be opened',
    detail: 'No internal details were displayed. Try again after the camp settles.',
  },
};

export function LobbyStatePanel({
  status,
  onRetry,
}: {
  status: Exclude<LobbyStatus, 'READY' | 'DEGRADED'>;
  onRetry?: () => void;
}) {
  const message = copy[status];
  const Icon = status === 'OFFLINE'
    ? CloudOff
    : status === 'FATAL_ERROR' || status === 'AUTH_REQUIRED'
      ? CircleAlert
      : status === 'EMPTY'
        ? RadioTower
        : LoaderCircle;
  const busy = status === 'BOOTSTRAPPING' || status === 'RETRYING';

  return (
    <section
      className={`tc-lobby-state tc-lobby-state-${status.toLowerCase()}`}
      role={busy ? 'status' : status === 'EMPTY' ? 'region' : 'alert'}
      aria-live={busy ? 'polite' : 'assertive'}
      aria-busy={busy}
    >
      <span className="tc-lobby-state-icon" aria-hidden="true">
        <Icon size={30} />
      </span>
      <div>
        <p className="tc-lobby-kicker">{message.eyebrow}</p>
        <h2>{message.title}</h2>
        <p>{message.detail}</p>
      </div>
      {onRetry && !busy && (
        <button className="tc-lobby-action" type="button" onClick={onRetry}>
          <RotateCw aria-hidden="true" size={18} />
          Try again
        </button>
      )}
      {busy && <div className="tc-lobby-skeleton-lines" aria-hidden="true"><i /><i /><i /></div>}
    </section>
  );
}
