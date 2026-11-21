// Bundles the CLI into a single dependency-free ESM file: dist/nod.mjs
import { build } from 'esbuild'
import { readFile } from 'node:fs/promises'

const pkg = JSON.parse(await readFile(new URL('../package.json', import.meta.url), 'utf8'))

await build({
  entryPoints: ['src/main.ts'],
  outfile: 'dist/nod.mjs',
  bundle: true,
  platform: 'node',
  format: 'esm',
  target: 'node22',
  minify: true,
  legalComments: 'none',
  banner: { js: '#!/usr/bin/env node' },
  define: { __NOD_VERSION__: JSON.stringify(pkg.version) },
  logLevel: 'info',
})
