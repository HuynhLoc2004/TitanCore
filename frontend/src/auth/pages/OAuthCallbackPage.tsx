import { useEffect, useRef, useState } from 'react';
import { navigate } from '../../app/AppRoutes';
import { useAuth } from '../useAuth';

type CallbackState = 'loading' | 'failed';

const SAFE_ERROR_MESSAGES: Record<string, string> = {
  cancelled: 'Google sign-in was cancelled. Your raid pass is still safe.',
  denied: 'Google sign-in was not approved. You can try again or use your local login.',
  unavailable: 'Google is taking a breather. Try again in a moment.',
  collision: 'That Google email needs local login first before it can join this camp.',
  inactive: 'This TitanCore account cannot sign in right now.',
  expired: 'That sign-in link expired. Start Google sign-in again.',
  invalid: 'That sign-in callback could not be trusted. Please try again.',
  replayed: 'That sign-in callback was already used. Start Google sign-in again.',
  failed: 'Google sign-in did not finish. Please try again.',
};

const FAILURE_CODES = new Set(Object.keys(SAFE_ERROR_MESSAGES));

export function OAuthCallbackPage() {
  const { restoreSession } = useAuth();
  const [state, setState] = useState<CallbackState>('loading');
  const [message, setMessage] = useState('Restoring your Google raid pass...');
  const processedRef = useRef(false);

  useEffect(() => {
    let active = true;
    if (processedRef.current) {
      return () => {
        active = false;
      };
    }
    processedRef.current = true;

    const params = new URLSearchParams(window.location.search);
    const oauth = params.get('oauth');
    const code = params.get('code');
    window.history.replaceState({}, '', '/auth/oauth/callback');

    if (oauth !== 'success') {
      const safeCode = code && FAILURE_CODES.has(code) ? code : 'failed';
      setMessage(SAFE_ERROR_MESSAGES[safeCode]);
      setState('failed');
      return () => {
        active = false;
      };
    }

    restoreSession()
      .then(() => {
        if (!active) {
          return;
        }
        navigate('/app', { replace: true });
      })
      .catch(() => {
        if (!active) {
          return;
        }
        setMessage(SAFE_ERROR_MESSAGES.failed);
        setState('failed');
      });

    return () => {
      active = false;
    };
  }, [restoreSession]);

  if (state === 'failed') {
    return (
      <main className="min-h-screen bg-[var(--tc-bg)] text-white">
        <section className="tc-shell tc-center">
          <div className="tc-callback-card" role="alert" aria-live="assertive">
            <p className="tc-eyebrow">Google sign-in</p>
            <h1>Raid pass not restored</h1>
            <p>{message}</p>
            <div className="tc-callback-actions">
              <button className="tc-button" type="button" onClick={() => window.location.assign('/api/auth/oauth/google/start')}>
                Try Google again
              </button>
              <button className="tc-button tc-button-secondary" type="button" onClick={() => navigate('/login', { replace: true })}>
                Return to login
              </button>
            </div>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-[var(--tc-bg)] text-white">
      <section className="tc-shell tc-center">
        <div className="tc-loader-card tc-oauth-loading" role="status" aria-live="polite">
          <div className="tc-loader-token" aria-hidden="true" />
          <p className="tc-eyebrow">Google sign-in</p>
          <h1>Restoring your raid pass</h1>
          <p>{message}</p>
        </div>
      </section>
    </main>
  );
}
