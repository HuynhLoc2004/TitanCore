import { useEffect, useState } from 'react';
import { LoginPage } from '../auth/pages/LoginPage';
import { RegisterPage } from '../auth/pages/RegisterPage';
import { useAuth } from '../auth/useAuth';

type Route = '/login' | '/register' | '/app';

function currentRoute(): Route {
  const path = window.location.pathname;
  if (path === '/register') {
    return '/register';
  }
  if (path === '/app') {
    return '/app';
  }
  return '/login';
}

export function navigate(route: Route) {
  window.history.pushState({}, '', route);
  window.dispatchEvent(new PopStateEvent('popstate'));
}

export function AppRoutes() {
  const [route, setRoute] = useState<Route>(currentRoute);
  const { status, user, logout } = useAuth();

  useEffect(() => {
    const listener = () => setRoute(currentRoute());
    window.addEventListener('popstate', listener);
    return () => window.removeEventListener('popstate', listener);
  }, []);

  useEffect(() => {
    if (status === 'authenticated' && route !== '/app') {
      navigate('/app');
    }
    if (status === 'anonymous' && route === '/app') {
      navigate('/login');
    }
  }, [route, status]);

  if (status === 'bootstrapping') {
    return <BootstrapScreen />;
  }

  if (status === 'authenticated' && route !== '/app') {
    return <RedirectingScreen />;
  }

  if (route === '/register') {
    return <RegisterPage onLogin={() => navigate('/login')} />;
  }

  if (route === '/app' && status === 'authenticated' && user) {
    return (
      <main className="min-h-screen overflow-hidden bg-[var(--tc-bg)] text-white">
        <section className="tc-shell">
          <div className="tc-stars" aria-hidden="true" />
          <div className="tc-dashboard">
            <div>
              <p className="tc-eyebrow">Raid lobby online</p>
              <h1>Welcome back, {user.username}</h1>
              <p>
                Your war banner is ready. The boss room opens in the next approved gameplay phase.
              </p>
            </div>
            <div className="tc-player-card">
              <span>{user.role}</span>
              <strong>{user.status}</strong>
              <p>{user.email}</p>
            </div>
            <button className="tc-button tc-button-secondary" type="button" onClick={() => void logout()}>
              Log out
            </button>
          </div>
        </section>
      </main>
    );
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
