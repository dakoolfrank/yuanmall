const path = require('node:path');
const { spawn } = require('node:child_process');

const projectRoot = path.resolve(__dirname, '..');
const pluginRoot = path.dirname(require.resolve('@dcloudio/vite-plugin-uni/package.json'));
const build = process.argv[2] === 'build';
const child = spawn(process.execPath, [path.join(pluginRoot, 'bin/uni.js'), ...process.argv.slice(2)], {
  cwd: projectRoot,
  env: {
    ...process.env,
    UNI_INPUT_DIR: projectRoot,
    UNI_OUTPUT_DIR: path.join(projectRoot, 'unpackage', build ? 'build' : 'dev', 'h5'),
  },
  stdio: 'inherit',
});
child.on('error', (error) => {
  console.error(error);
  process.exitCode = 1;
});
child.on('exit', (code) => { process.exitCode = code ?? 1; });
