import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';

// One config for both Vite (dev/build) and Vitest. The chart-data mappers are pure functions,
// so the unit tests run in a plain Node environment — no DOM needed.
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
  },
  test: {
    environment: 'node',
    include: ['test/**/*.test.js'],
  },
});
