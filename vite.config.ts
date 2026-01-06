// Implements System
import { defineConfig } from 'vite';
import uni from '@dcloudio/vite-plugin-uni';

export default defineConfig({
  plugins: [uni()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './vite.test-setup.ts'
  }
});