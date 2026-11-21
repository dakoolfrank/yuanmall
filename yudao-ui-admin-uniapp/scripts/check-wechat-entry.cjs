const fs = require('node:fs')
const path = require('node:path')
const process = require('node:process')

// A main-package entry cannot synchronously depend on an unloaded subpackage.
const root = process.argv[2] ? path.resolve(process.argv[2]) : path.resolve(__dirname, '../dist/build/mp-weixin')
const appFile = path.join(root, 'app.json')
if (!fs.existsSync(appFile)) {
  console.error('Build the WeChat release before checking its entry dependencies.')
  process.exit(1)
}
const app = JSON.parse(fs.readFileSync(appFile, 'utf8'))
const subRoots = (app.subPackages || app.subpackages || []).map(item => `${item.root}/`)
const visited = new Set()
const violations = []

function visit(relative) {
  if (visited.has(relative))
    return
  visited.add(relative)
  const filename = path.join(root, relative)
  if (!fs.existsSync(filename))
    throw new Error(`Missing component metadata: ${relative}`)
  const json = JSON.parse(fs.readFileSync(filename, 'utf8'))
  for (const [name, component] of Object.entries(json.usingComponents || {})) {
    if (component.startsWith('plugin://'))
      continue
    const target = path.posix.normalize(component.startsWith('/')
      ? component.slice(1)
      : path.posix.join(path.posix.dirname(relative), component))
    if (target.startsWith('../'))
      throw new Error(`Component is outside the build: ${target}`)
    if (subRoots.some(prefix => target.startsWith(prefix))) {
      if (!json.componentPlaceholder?.[name])
        violations.push({ source: relative, name, target })
    } else {
      visit(`${target}.json`)
    }
  }
}

visit('app.json')
visit(`${app.entryPagePath || app.pages[0]}.json`)

// A successful build can leave unresolved easycom tags in the output template.
const interactivePages = [
  'pages-core/auth/login',
  'pages-mall/product/spu/index',
  'pages-mall/trade/order/index',
  'pages-member/user/index',
  'pages-mall/trade/delivery/pick-up-store/index',
]
for (const page of interactivePages) {
  const template = fs.readFileSync(path.join(root, `${page}.wxml`), 'utf8')
  const metadata = JSON.parse(fs.readFileSync(path.join(root, `${page}.json`), 'utf8'))
  const components = { ...app.usingComponents, ...metadata.usingComponents }
  const tags = new Set(Array.from(template.matchAll(/<(wd-[\w-]+|z-paging)(?=[\s/>])/g), match => match[1]))
  for (const tag of tags) {
    if (!components[tag])
      violations.push({ source: `${page}.wxml`, name: tag, reason: 'Missing component registration' })
  }
}

console.log(JSON.stringify({ componentFiles: visited.size, interactivePages: interactivePages.length, violations }, null, 2))
if (violations.length)
  process.exitCode = 1
