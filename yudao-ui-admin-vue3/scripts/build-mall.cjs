const path = require('node:path')
const { spawnSync } = require('node:child_process')

const root = path.resolve(__dirname, '..')
// Limit native bundler workers to avoid memory spikes on the development PC.
const result = spawnSync(
  process.execPath,
  [
    '--max-old-space-size=8192',
    path.join(root, 'node_modules/vite/bin/vite.js'),
    'build',
    '--mode',
    'mall'
  ],
  {
    cwd: root,
    stdio: 'inherit',
    env: {
      ...process.env,
      NODE_ENV: 'production',
      ROLLDOWN_WORKER_THREADS: process.env.ROLLDOWN_WORKER_THREADS || '2',
      RAYON_NUM_THREADS: process.env.RAYON_NUM_THREADS || '2'
    }
  }
)

if (result.error) throw result.error
process.exit(result.status ?? 1)
