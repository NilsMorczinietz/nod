import { svelte } from '@sveltejs/vite-plugin-svelte'
import { defineConfig } from 'vite'

// Two pages: the main window (index.html) and the overlay (overlay.html).
export default defineConfig({
  plugins: [svelte()],
  clearScreen: false,
  server: { port: 1420, strictPort: true, watch: { ignored: ['**/src-tauri/**'] } },
  build: {
    target: 'es2022',
    rollupOptions: { input: { main: 'index.html', overlay: 'overlay.html' } },
  },
})
