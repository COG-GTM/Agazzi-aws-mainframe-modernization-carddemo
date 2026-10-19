import react from '@vitejs/plugin-react';
import { defineWorkspace } from 'vitest/config';

export default defineWorkspace([
  {
    plugins: [react()],
    test: {
      name: 'unit',
      environment: 'jsdom',
      include: ['tests/unit/**/*.test.{ts,tsx}', 'tests/golden/**/*.test.ts'],
      setupFiles: ['tests/setup.ts'],
    },
  },
  {
    test: {
      name: 'contract',
      environment: 'node',
      include: ['tests/contract/**/*.test.ts'],
      globalSetup: ['tests/contract/prism.global.ts'],
      testTimeout: 20000,
      hookTimeout: 60000,
    },
  },
]);
