import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, beforeEach, vi } from 'vitest';
import { invalidateAuthGeneration, setAccessToken } from '../auth/api';

beforeEach(() => {
  invalidateAuthGeneration();
  setAccessToken(null);
  window.localStorage.clear();
  window.sessionStorage.clear();
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
  vi.restoreAllMocks();
});

afterEach(() => {
  cleanup();
});
