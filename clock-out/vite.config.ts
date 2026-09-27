import { defineConfig } from 'vite';

// three.js alone is ~600 kB minified; one chunk is fine for a single-page game.
export default defineConfig({
  build: { chunkSizeWarningLimit: 900 },
});
