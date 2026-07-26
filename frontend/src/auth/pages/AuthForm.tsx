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
}: {
  mode: 'login' | 'register';
  onSubmit: (values: { login?: string; email?: string; username?: string; password: string }) => Promise<void>;
  switchLabel: string;
  switchAction: () => void;
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
