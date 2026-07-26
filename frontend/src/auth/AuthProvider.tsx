import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  fetchMe,
  completeProfileOnboarding,
  getAuthGeneration,
  invalidateAuthGeneration,
  login,
  logout as apiLogout,
  refreshAccessToken,
  register,
  setAccessToken,
  type User,
} from './api';

type AuthStatus = 'bootstrapping' | 'anonymous' | 'authenticated';

type AuthContextValue = {
  status: AuthStatus;
  user: User | null;
  error: string | null;
  login: (loginValue: string, password: string) => Promise<void>;
  register: (email: string, username: string, password: string) => Promise<void>;
  restoreSession: () => Promise<User>;
  logout: () => Promise<void>;
  completeOnboarding: (displayName: string, expectedVersion: number) => Promise<void>;
};

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('bootstrapping');
  const [user, setUser] = useState<User | null>(null);
  const [error, setError] = useState<string | null>(null);

  const restoreSessionAction = useCallback(async () => {
    const generation = getAuthGeneration();
    await refreshAccessToken();
    const currentUser = await fetchMe();
    if (generation !== getAuthGeneration()) {
      setAccessToken(null);
      throw new Error('Stale authentication restoration');
    }
    setUser(currentUser);
    setStatus('authenticated');
    return currentUser;
  }, []);

  useEffect(() => {
    let mounted = true;
    if (window.location.pathname === '/auth/oauth/callback') {
      setStatus('anonymous');
      return () => {
        mounted = false;
      };
    }
    restoreSessionAction()
      .then((currentUser) => {
        if (!mounted) {
          return;
        }
        setUser(currentUser);
        setStatus('authenticated');
      })
      .catch(() => {
        if (!mounted) {
          return;
        }
        setAccessToken(null);
        setUser(null);
        setStatus('anonymous');
      });
    return () => {
      mounted = false;
    };
  }, [restoreSessionAction]);

  const loginAction = useCallback(async (loginValue: string, password: string) => {
    setError(null);
    const response = await login(loginValue, password);
    setUser(response.user);
    setStatus('authenticated');
  }, []);

  const registerAction = useCallback(async (email: string, username: string, password: string) => {
    setError(null);
    const response = await register(email, username, password);
    setUser(response.user);
    setStatus('authenticated');
  }, []);

  const logoutAction = useCallback(async () => {
    setError(null);
    invalidateAuthGeneration();
    try {
      await apiLogout();
    } finally {
      setAccessToken(null);
      setUser(null);
      setStatus('anonymous');
    }
  }, []);

  const completeOnboardingAction = useCallback(async (displayName: string, expectedVersion: number) => {
    const profile = await completeProfileOnboarding(displayName, expectedVersion);
    setUser((current) => current ? { ...current, profile } : current);
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    status,
    user,
    error,
    login: loginAction,
    register: registerAction,
    restoreSession: restoreSessionAction,
    logout: logoutAction,
    completeOnboarding: completeOnboardingAction,
  }), [completeOnboardingAction, error, loginAction, logoutAction, registerAction,
    restoreSessionAction, status, user]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
