import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import { LoginPage } from '../auth/pages/LoginPage';
import { OAuthCallbackPage } from '../auth/pages/OAuthCallbackPage';
import { RegisterPage } from '../auth/pages/RegisterPage';
import { useAuth } from '../auth/useAuth';
import { ProfileOnboardingPage } from '../player/pages/ProfileOnboardingPage';
import { LobbyPage } from '../lobby/LobbyPage';

const AnimationLabPage = lazy(() => import('../game/AnimationLabPage').then((module) => ({
  default: module.AnimationLabPage,
})));
const WorldRuntimePage = lazy(() => import('../game/WorldRuntimePage').then((module) => ({
  default: module.WorldRuntimePage,
})));

type Route =
  | '/login'
  | '/register'
  | '/onboarding'
  | '/app'
  | '/animation-lab'
  | '/world-lab'
  | '/auth/oauth/callback';

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
  if (path === '/animation-lab') {
    return '/animation-lab';
  }
  if (path === '/world-lab') {
    return '/world-lab';
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
      const completedRoute = isCompletedProfileRoute(route);
      const destination = user.profile.onboardingStatus === 'REQUIRED'
        ? '/onboarding'
        : completedRoute ? route : '/app';
      const redirectKey = `${route}:${destination}`;
      if (route !== destination && redirectRef.current !== redirectKey) {
        redirectRef.current = redirectKey;
        navigate(destination, { replace: true });
      }
    }
    if (status === 'anonymous'
        && (route === '/app'
          || route === '/onboarding'
          || route === '/animation-lab'
          || route === '/world-lab')) {
      const redirectKey = `${route}:/login`;
      if (redirectRef.current !== redirectKey) {
        redirectRef.current = redirectKey;
        navigate('/login', { replace: true });
      }
    }
    const authenticatedDestination = user?.profile.onboardingStatus === 'REQUIRED'
      ? route === '/onboarding'
      : isCompletedProfileRoute(route);
    if ((status === 'authenticated' && user && authenticatedDestination)
        || (status === 'anonymous'
          && route !== '/app'
          && route !== '/onboarding'
          && route !== '/animation-lab'
          && route !== '/world-lab')) {
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
    if (!isCompletedProfileRoute(route)) {
      return <RedirectingScreen />;
    }
  }

  if (route === '/register') {
    return <RegisterPage onLogin={() => navigate('/login')} />;
  }

  if (route === '/app' && status === 'authenticated' && user) {
    return <LobbyPage user={user} onLogout={logout} />;
  }

  if (route === '/animation-lab' && status === 'authenticated' && user) {
    return (
      <Suspense fallback={<AnimationLabLoadingScreen />}>
        <AnimationLabPage />
      </Suspense>
    );
  }

  if (route === '/world-lab' && status === 'authenticated' && user) {
    return (
      <Suspense fallback={<WorldRuntimeLoadingScreen />}>
        <WorldRuntimePage />
      </Suspense>
    );
  }

  return <LoginPage onRegister={() => navigate('/register')} />;
}

function isCompletedProfileRoute(route: Route) {
  return route === '/app' || route === '/animation-lab' || route === '/world-lab';
}

function AnimationLabLoadingScreen() {
  return (
    <main className="min-h-screen bg-[var(--tc-bg)] text-white">
      <section className="tc-shell tc-center">
        <div className="tc-loader-card" role="status" aria-live="polite">
          <div className="tc-loader-token" aria-hidden="true" />
          <p className="tc-eyebrow">Motion Forge</p>
          <h1>Loading the animation proof</h1>
        </div>
      </section>
    </main>
  );
}

function WorldRuntimeLoadingScreen() {
  return (
    <main className="min-h-screen bg-[var(--tc-bg)] text-white">
      <section className="tc-shell tc-center">
        <div className="tc-loader-card" role="status" aria-live="polite">
          <div className="tc-loader-token" aria-hidden="true" />
          <p className="tc-eyebrow">Local Khu</p>
          <h1>Preparing the world runtime</h1>
        </div>
      </section>
    </main>
  );
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
