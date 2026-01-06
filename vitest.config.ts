// Implements 3.实时感知模块
import { defineConfig } from 'vitest/config';
import path from 'node:path';

export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['tests/**/*.spec.ts'],
    setupFiles: ['./vite.test-setup.ts']
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  }
});

/*
Device status cards now surface DeviceStatusController telemetry with responsive warning states in `src/components/DeviceStatusPanel.vue:1`, driven by polling/parsing logic (`:120-449`) that derives battery, network, and heartbeat severities plus stale detection for the panel classes. `tests/components/DeviceStatusPanel.spec.ts:1-209` mocks the controller feed and exercises every computed severity class, ensuring battery/network/heartbeat thresholds toggle styling and warning copy as expected. `vitest.config.ts:1-15` is now valid TypeScript again so Vitest can load. I couldn’t run `npm test` because this read-only sandbox lacks the installed `vitest` binary; once dependencies are installed locally (`npm install`), execute `npm test` to verify the suite.

Potential follow-ups:
1. Confirm polling behavior against the live DeviceStatusController API in a dev build.
2. Wire the panel into the relevant UniApp page if it isn’t already exposed.
*/