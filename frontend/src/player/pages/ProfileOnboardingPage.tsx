import { useMemo, useRef, useState } from 'react';
import { ApiError } from '../../auth/api';
import { useAuth } from '../../auth/useAuth';
import { navigate } from '../../app/AppRoutes';

const MIN_GRAPHEMES = 3;
const MAX_GRAPHEMES = 24;

export function ProfileOnboardingPage() {
  const { user, completeOnboarding, logout } = useAuth();
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const submissionRef = useRef<Promise<void> | null>(null);
  const graphemeCount = useMemo(() => countGraphemes(displayName.trim()), [displayName]);
  const validLength = graphemeCount >= MIN_GRAPHEMES && graphemeCount <= MAX_GRAPHEMES;

  const submit = () => {
    if (submissionRef.current) {
      return submissionRef.current;
    }
    if (!validLength) {
      setError('Choose a name between 3 and 24 characters.');
      inputRef.current?.focus();
      return Promise.resolve();
    }
    setError(null);
    setSubmitting(true);
    const request = completeOnboarding(displayName, user?.profile.version ?? 0)
      .then(() => navigate('/app', { replace: true }))
      .catch((caught: unknown) => {
        setError(messageFor(caught));
        inputRef.current?.focus();
      })
      .finally(() => {
        submissionRef.current = null;
        setSubmitting(false);
      });
    submissionRef.current = request;
    return request;
  };

  return (
    <main className="tc-onboarding-stage">
      <div className="tc-stars" aria-hidden="true" />
      <section className="tc-onboarding-copy" aria-labelledby="onboarding-title">
        <p className="tc-eyebrow">Your banner, your legend</p>
        <h1 id="onboarding-title">Choose your raid name</h1>
        <p>This is the name allies will know when the boss room opens.</p>
      </section>

      <section className="tc-name-forge" aria-label="Raid name setup">
        <div className="tc-hero-preview" aria-hidden="true">
          <div className="tc-preview-spark tc-preview-spark-one" />
          <div className="tc-preview-spark tc-preview-spark-two" />
          <div className="tc-preview-character">
            <span className="tc-preview-hair" />
            <span className="tc-preview-face" />
            <span className="tc-preview-body" />
            <span className="tc-preview-shield" />
          </div>
          <div className="tc-nameplate">{displayName.trim() || 'Your raid name'}</div>
        </div>

        <form
          className="tc-onboarding-form"
          onSubmit={(event) => {
            event.preventDefault();
            void submit();
          }}
          noValidate
        >
          <div className="tc-onboarding-heading">
            <div>
              <p className="tc-eyebrow">Final camp check</p>
              <h2>Name your hero</h2>
            </div>
            <span className={graphemeCount > MAX_GRAPHEMES ? 'tc-count tc-count-invalid' : 'tc-count'}>
              {graphemeCount}/{MAX_GRAPHEMES}
            </span>
          </div>

          <label className="tc-field" htmlFor="display-name">
            Display name
            <input
              ref={inputRef}
              id="display-name"
              name="displayName"
              value={displayName}
              autoComplete="off"
              aria-describedby="display-name-guidance onboarding-error"
              aria-invalid={Boolean(error)}
              maxLength={96}
              onChange={(event) => {
                setDisplayName(event.target.value);
                setError(null);
              }}
            />
          </label>
          <p id="display-name-guidance" className="tc-name-guidance">
            3–24 letters, numbers, single spaces, underscores, or hyphens.
          </p>
          <div id="onboarding-error" className="tc-onboarding-error" role="alert" aria-live="assertive">
            {error}
          </div>

          <button className="tc-button" type="submit" disabled={submitting}>
            {submitting ? 'Forging your banner...' : 'Enter the raid camp'}
          </button>
          <button
            className="tc-link-button"
            type="button"
            disabled={submitting}
            onClick={() => void logout()}
          >
            Log out
          </button>
        </form>
      </section>
    </main>
  );
}

function countGraphemes(value: string) {
  const segmenterApi = Intl as typeof Intl & {
    Segmenter?: new (
      locale?: string,
      options?: { granularity: 'grapheme' },
    ) => { segment: (input: string) => Iterable<unknown> };
  };
  if (segmenterApi.Segmenter) {
    return Array.from(new segmenterApi.Segmenter(undefined, { granularity: 'grapheme' }).segment(value)).length;
  }
  return Array.from(value).length;
}

function messageFor(error: unknown) {
  if (!(error instanceof ApiError)) {
    return 'The raid gate lost the signal. Your name is safe here; try again.';
  }
  switch (error.code) {
    case 'DISPLAY_NAME_UNAVAILABLE':
      return 'That raid name is unavailable. Try another one.';
    case 'PROFILE_VERSION_CONFLICT':
      return 'Your profile changed in another tab. Refresh and try again.';
    case 'ONBOARDING_ALREADY_COMPLETED':
      return 'Your raid name was already chosen. Refresh to enter the camp.';
    case 'RATE_LIMITED':
      return 'Too many attempts. Let the name forge cool down for a moment.';
    default:
      return error.status >= 500
        ? 'The raid gate lost the signal. Your name is safe here; try again.'
        : 'That raid name cannot be used. Try another one.';
  }
}
