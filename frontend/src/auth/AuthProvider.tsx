import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { fetchMe, login, logout as apiLogout, refreshAccessToken, register, setAccessToken, type User } from './api';

type AuthStatus = 'bootstrapping' | 'anonymous' | 'authenticated';

type AuthContextValue = {
  status: AuthStatus;
  user: User | null;
  error: string | null;
  login: (loginValue: string, password: string) => Promise<void>;
  register: (email: string, username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
};

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('bootstrapping');
  const [user, setUser] = useState<User | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    refreshAccessToken()
      .then(() => fetchMe())
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
  }, []);

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
    try {
      await apiLogout();
    } finally {
      setAccessToken(null);
      setUser(null);
      setStatus('anonymous');
    }
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    status,
    user,
    error,
    login: loginAction,
    register: registerAction,
    logout: logoutAction,
  }), [error, loginAction, logoutAction, registerAction, status, user]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
