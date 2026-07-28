import { useEffect, useRef, useState } from 'react';
import { LoginPage } from '../auth/pages/LoginPage';
import { OAuthCallbackPage } from '../auth/pages/OAuthCallbackPage';
import { RegisterPage } from '../auth/pages/RegisterPage';
import { useAuth } from '../auth/useAuth';
import { ProfileOnboardingPage } from '../player/pages/ProfileOnboardingPage';
import { LobbyPage } from '../lobby/LobbyPage';

type Route = '/login' | '/register' | '/onboarding' | '/app' | '/auth/oauth/callback';

function currentRoute(): Route {
  const path = window.location.pathname;
  if (path === '/auth/oauth/callback') {
    return '/auth/oauth/callback';
  }
  if (path === '/register') {
    return '/register';
  }
  if (path === '/app') {
    return '/app';
  }
  if (path === '/onboarding') {
    return '/onboarding';
  }
  return '/login';
}

export function navigate(route: Route, options: { replace?: boolean } = {}) {
  if (options.replace) {
    window.history.replaceState({}, '', route);
  } else {
    window.history.pushState({}, '', route);
  }
  window.dispatchEvent(new PopStateEvent('popstate'));
}

export function AppRoutes() {
  const [route, setRoute] = useState<Route>(currentRoute);
  const { status, user, logout } = useAuth();
  const redirectRef = useRef<string | null>(null);

  useEffect(() => {
    const listener = () => setRoute(currentRoute());
    window.addEventListener('popstate', listener);
    return () => window.removeEventListener('popstate', listener);
  }, []);

  useEffect(() => {
    if (status === 'authenticated' && user && route !== '/auth/oauth/callback') {
      const destination = user.profile.onboardingStatus === 'REQUIRED' ? '/onboarding' : '/app';
      const redirectKey = `${route}:${destination}`;
      if (route !== destination && redirectRef.current !== redirectKey) {
        redirectRef.current = redirectKey;
        navigate(destination, { replace: true });
      }
    }
    if (status === 'anonymous' && (route === '/app' || route === '/onboarding')) {
      const redirectKey = `${route}:/login`;
      if (redirectRef.current !== redirectKey) {
        redirectRef.current = redirectKey;
        navigate('/login', { replace: true });
      }
    }
    if ((status === 'authenticated' && user
        && route === (user.profile.onboardingStatus === 'REQUIRED' ? '/onboarding' : '/app'))
        || (status === 'anonymous' && route !== '/app' && route !== '/onboarding')) {
      redirectRef.current = null;
    }
  }, [route, status, user]);

  if (route === '/auth/oauth/callback') {
    return <OAuthCallbackPage />;
  }

  if (status === 'bootstrapping') {
    return <BootstrapScreen />;
  }

  if (status === 'authenticated' && user) {
    if (user.profile.onboardingStatus === 'REQUIRED') {
      return route === '/onboarding' ? <ProfileOnboardingPage /> : <RedirectingScreen />;
    }
    if (route !== '/app') {
      return <RedirectingScreen />;
    }
  }

  if (route === '/register') {
    return <RegisterPage onLogin={() => navigate('/login')} />;
  }

  if (route === '/app' && status === 'authenticated' && user) {
    return <LobbyPage user={user} onLogout={logout} />;
  }

  return <LoginPage onRegister={() => navigate('/register')} />;
}

function BootstrapScreen() {
  return (
    <main className="min-h-screen bg-[var(--tc-bg)] text-white">
      <section className="tc-shell tc-center">
        <div className="tc-loader-card" role="status" aria-live="polite">
          <div className="tc-loader-token" aria-hidden="true" />
          <p className="tc-eyebrow">TitanCore</p>
          <h1>Checking your raid pass</h1>
        </div>
      </section>
    </main>
  );
}

function RedirectingScreen() {
  return (
    <main className="min-h-screen bg-[var(--tc-bg)] text-white">
      <section className="tc-shell tc-center">
        <div className="tc-loader-card" role="status" aria-live="polite">
          <div className="tc-loader-token" aria-hidden="true" />
          <p className="tc-eyebrow">TitanCore</p>
          <h1>Opening your raid lobby</h1>
        </div>
      </section>
    </main>
  );
}
