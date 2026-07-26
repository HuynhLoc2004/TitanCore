import { useRef, useState, type FormEvent } from 'react';
import { ApiError } from '../api';

type FieldState = {
  login?: string;
  email?: string;
  username?: string;
  password?: string;
};

export function AuthForm({
  mode,
  onSubmit,
  switchLabel,
  switchAction,
  onGoogleStart = startGoogleOAuth,
}: {
  mode: 'login' | 'register';
  onSubmit: (values: { login?: string; email?: string; username?: string; password: string }) => Promise<void>;
  switchLabel: string;
  switchAction: () => void;
  onGoogleStart?: () => void;
}) {
  const [values, setValues] = useState({ login: '', email: '', username: '', password: '' });
  const [errors, setErrors] = useState<FieldState>({});
  const [globalError, setGlobalError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const submittingRef = useRef(false);
  const passwordId = `${mode}-password`;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submittingRef.current) {
      return;
    }
    const nextErrors = validate(mode, values);
    setErrors(nextErrors);
    setGlobalError(null);
    if (Object.keys(nextErrors).length > 0) {
      return;
    }
    submittingRef.current = true;
    setLoading(true);
    try {
      await onSubmit(values);
    } catch (error) {
      setGlobalError(error instanceof ApiError ? error.message : 'The gate jammed. Try again.');
    } finally {
      submittingRef.current = false;
      setLoading(false);
    }
  }

  return (
    <form className="tc-auth-card" onSubmit={(event) => void submit(event)} noValidate>
      <div>
        <p className="tc-eyebrow">{mode === 'login' ? 'Raid pass' : 'New champion'}</p>
        <h2>{mode === 'login' ? 'Enter the camp' : 'Forge your banner'}</h2>
      </div>

      {globalError && <div className="tc-alert" role="alert">{globalError}</div>}

      {mode === 'login' ? (
        <Field
          label="Email or username"
          name="login"
          value={values.login}
          error={errors.login}
          autoComplete="username"
          onChange={(value) => setValues((current) => ({ ...current, login: value }))}
        />
      ) : (
        <>
          <Field
            label="Email"
            name="email"
            type="email"
            value={values.email}
            error={errors.email}
            autoComplete="email"
            onChange={(value) => setValues((current) => ({ ...current, email: value }))}
          />
          <Field
            label="Hero name"
            name="username"
            value={values.username}
            error={errors.username}
            autoComplete="username"
            onChange={(value) => setValues((current) => ({ ...current, username: value }))}
          />
        </>
      )}

      <div className="tc-password-wrap">
        <Field
          label="Password"
          name="password"
          id={passwordId}
          type={showPassword ? 'text' : 'password'}
          value={values.password}
          error={errors.password}
          autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
          onChange={(value) => setValues((current) => ({ ...current, password: value }))}
        />
        <button
          className="tc-icon-button"
          type="button"
          aria-label={showPassword ? 'Hide password' : 'Show password'}
          aria-controls={passwordId}
          onClick={() => setShowPassword((current) => !current)}
        >
          {showPassword ? 'Hide' : 'Show'}
        </button>
      </div>

      <button className="tc-button" type="submit" disabled={loading}>
        {loading ? 'Summoning...' : mode === 'login' ? 'Start raid' : 'Create champion'}
      </button>

      <div className="tc-oauth-divider" aria-hidden="true">
        <span />
        <strong>or</strong>
        <span />
      </div>

      <button
        className="tc-google-button"
        type="button"
        disabled={loading}
        onClick={() => {
          if (!submittingRef.current) {
            onGoogleStart();
          }
        }}
      >
        <GoogleMark />
        <span>Continue with Google</span>
      </button>

      <button className="tc-link-button" type="button" onClick={() => {
        if (!submittingRef.current) {
          switchAction();
        }
      }} disabled={loading}>
        {switchLabel}
      </button>
    </form>
  );
}

function Field({
  label,
  name,
  value,
  onChange,
  error,
  id = name,
  type = 'text',
  autoComplete,
}: {
  label: string;
  name: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  id?: string;
  type?: string;
  autoComplete?: string;
}) {
  const errorId = `${id}-error`;
  return (
    <label className="tc-field" htmlFor={id}>
      <span>{label}</span>
      <input
        id={id}
        name={name}
        type={type}
        value={value}
        autoComplete={autoComplete}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? errorId : undefined}
        onChange={(event) => onChange(event.target.value)}
      />
      {error && <small id={errorId}>{error}</small>}
    </label>
  );
}

function validate(mode: 'login' | 'register', values: {
  login: string;
  email: string;
  username: string;
  password: string;
}) {
  const errors: FieldState = {};
  if (mode === 'login' && values.login.trim().length === 0) {
    errors.login = 'Tell the gate who you are.';
  }
  if (mode === 'register') {
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email.trim())) {
      errors.email = 'Use a real email-shaped rune.';
    }
    if (!/^[A-Za-z0-9_]{3,32}$/.test(values.username.trim())) {
      errors.username = '3-32 letters, numbers, or underscores.';
    }
  }
  if (values.password.length < 12) {
    errors.password = 'At least 12 characters. Bosses respect commitment.';
  }
  return errors;
}

function startGoogleOAuth() {
  window.location.assign('/api/auth/oauth/google/start');
}

function GoogleMark() {
  return (
    <svg className="tc-google-mark" viewBox="0 0 18 18" aria-hidden="true" focusable="false">
      <path fill="#4285F4" d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.7-1.57 2.68-3.88 2.68-6.62z" />
      <path fill="#34A853" d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.8.54-1.84.86-3.04.86-2.34 0-4.33-1.58-5.04-3.72H.94v2.33A9 9 0 0 0 9 18z" />
      <path fill="#FBBC05" d="M3.96 10.7A5.41 5.41 0 0 1 3.68 9c0-.59.1-1.16.28-1.7V4.97H.94A9 9 0 0 0 0 9c0 1.45.34 2.82.94 4.03l3.02-2.33z" />
      <path fill="#EA4335" d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58C13.46.9 11.43 0 9 0A9 9 0 0 0 .94 4.97L3.96 7.3C4.67 5.16 6.66 3.58 9 3.58z" />
    </svg>
  );
}
