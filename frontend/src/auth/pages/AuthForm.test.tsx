import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api';
import { AuthForm } from './AuthForm';

function deferred() {
  let resolve!: () => void;
  const promise = new Promise<void>((next) => {
    resolve = next;
  });
  return { promise, resolve };
}

async function fillLoginForm() {
  await userEvent.type(screen.getByLabelText(/email or username/i), 'hero');
  await userEvent.type(screen.getByLabelText(/^password$/i), 'very-secure-passphrase');
}

describe('AuthForm', () => {
  it('allows only one rapid click submission', async () => {
    const pending = deferred();
    const onSubmit = vi.fn(() => pending.promise);
    render(<AuthForm mode="login" onSubmit={onSubmit} switchLabel="Register" switchAction={vi.fn()} />);
    await fillLoginForm();

    const submit = screen.getByRole('button', { name: /start raid/i });
    await Promise.all([userEvent.click(submit), userEvent.click(submit)]);

    expect(onSubmit).toHaveBeenCalledTimes(1);
    pending.resolve();
  });

  it('allows only one repeated Enter submission', async () => {
    const pending = deferred();
    const onSubmit = vi.fn(() => pending.promise);
    render(<AuthForm mode="login" onSubmit={onSubmit} switchLabel="Register" switchAction={vi.fn()} />);
    await fillLoginForm();

    await userEvent.type(screen.getByLabelText(/^password$/i), '{Enter}{Enter}');

    expect(onSubmit).toHaveBeenCalledTimes(1);
    pending.resolve();
  });

  it('allows retry after a failed submission', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValueOnce(new ApiError(401, 'AUTHENTICATION_FAILED', 'That combo did not open the raid gate.'))
      .mockResolvedValueOnce(undefined);
    render(<AuthForm mode="login" onSubmit={onSubmit} switchLabel="Register" switchAction={vi.fn()} />);
    await fillLoginForm();

    await userEvent.click(screen.getByRole('button', { name: /start raid/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/did not open/i);
    await userEvent.click(screen.getByRole('button', { name: /start raid/i }));

    expect(onSubmit).toHaveBeenCalledTimes(2);
  });

  it('suppresses route switching while submission is active', async () => {
    const pending = deferred();
    const switchAction = vi.fn();
    render(<AuthForm mode="login" onSubmit={() => pending.promise} switchLabel="Register" switchAction={switchAction} />);
    await fillLoginForm();

    await userEvent.click(screen.getByRole('button', { name: /start raid/i }));
    await userEvent.click(screen.getByRole('button', { name: /register/i }));

    expect(switchAction).not.toHaveBeenCalled();
    pending.resolve();
    await waitFor(() => expect(screen.getByRole('button', { name: /register/i })).not.toBeDisabled());
  });
});
