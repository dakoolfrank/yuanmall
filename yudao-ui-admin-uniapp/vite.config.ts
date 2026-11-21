import type { ComponentResolver } from '@uni-helper/vite-plugin-uni-components'
import { writeFileSync } from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import Uni from '@uni-helper/plugin-uni'
import UniComponents, { kebabCase } from '@uni-helper/vite-plugin-uni-components'
// @see https://uni-helper.js.org/vite-plugin-uni-layouts
import UniLayouts from '@uni-helper/vite-plugin-uni-layouts'
// @see https://github.com/uni-helper/vite-plugin-uni-manifest
import UniManifest from '@uni-helper/vite-plugin-uni-manifest'
// @see https://uni-helper.js.org/vite-plugin-uni-pages
import UniPages from '@uni-helper/vite-plugin-uni-pages'
// @see https://github.com/uni-helper/vite-plugin-uni-platform
// 需要与 @uni-helper/vite-plugin-uni-pages 插件一起使用
import UniPlatform from '@uni-helper/vite-plugin-uni-platform'
/**
 * 分包优化、模块异步跨包调用、组件异步跨包引用
 * @see https://github.com/uni-ku/bundle-optimizer
 */
import UniOptimization from '@uni-ku/bundle-optimizer'
// https://github.com/uni-ku/root
import UniKuRoot from '@uni-ku/root'
import dayjs from 'dayjs'
import { visualizer } from 'rollup-plugin-visualizer'
import UnoCSS from 'unocss/vite'
import AutoImport from 'unplugin-auto-import/vite'
import { defineConfig, loadEnv } from 'vite'
import ViteRestart from 'vite-plugin-restart'
import pagesConfig from './pages.config'
import openDevTools from './scripts/open-dev-tools'
import vitePluginEruda from './scripts/vite-plugin-eruda'
import { createCopyNativeResourcesPlugin } from './vite-plugins/copy-native-resources'
import syncManifestPlugin from './vite-plugins/sync-manifest-plugins'

/** 芋道 UI 组件解析器：本地 @/components/yudao-ui/* 的组件 */
function YudaoUiResolver(): ComponentResolver {
  return {
    type: 'component',
    resolve: (name: string) => {
      if (name.match(/^Yd[A-Z]/)) {
        const compName = kebabCase(name)
        return {
          name,
          from: `@/components/yudao-ui/${compName}/${compName}.vue`,
        }
      }
    },
  }
}

/** H5 needs explicit resolvers when UniComponents replaces native easycom. */
function H5UiResolver(): ComponentResolver {
  return {
    type: 'component',
    resolve: (name: string) => {
      const component = kebabCase(name)
      if (/^Wd[A-Z]/.test(name)) {
        return { name, from: `@wot-ui/ui/components/${component}/${component}.vue` }
      }
      if (name.startsWith('ZPaging') && !['ZPagingRefresh', 'ZPagingLoadMore'].includes(name)) {
        return { name, from: `z-paging/components/${component}/${component}.vue` }
      }
    },
  }
}

// https://vitejs.dev/config/
export default defineConfig(({ command, mode }) => {
  // @see https://unocss.dev/
  // const UnoCSS = (await import('unocss/vite')).default
  // console.log(mode === process.env.NODE_ENV) // true

  // mode: 区分生产环境还是开发环境
  console.log('command, mode -> ', command, mode)
  // pnpm dev:h5 时得到 => serve development
  // pnpm build:h5 时得到 => build production
  // pnpm dev:mp-weixin 时得到 => build development (注意区别，command为build)
  // pnpm build:mp-weixin 时得到 => build production
  // pnpm dev:app 时得到 => build development (注意区别，command为build)
  // pnpm build:app 时得到 => build production
  // dev 和 build 命令可以分别使用 .env.development 和 .env.production 的环境变量
  // 非 H5 端 dev 也是 build command，最终加载哪个 env 文件以实际 mode 为准。

  const { UNI_PLATFORM, SKIP_OPEN_DEVTOOLS } = process.env
  console.log('UNI_PLATFORM -> ', UNI_PLATFORM) // 得到 mp-weixin, h5, app 等

  const envDir = path.resolve(process.cwd(), 'env')
  const env = loadEnv(mode, envDir)
  const localEnv = loadEnv(mode, envDir, '')
  const {
    VITE_APP_PORT,
    VITE_SERVER_BASEURL,
    VITE_APP_TITLE,
    VITE_DELETE_CONSOLE,
    VITE_APP_PUBLIC_BASE,
    VITE_APP_PROXY_ENABLE,
    VITE_APP_PROXY_PREFIX,
    VITE_COPY_NATIVE_RES_ENABLE,
  } = env
  const { WECHAT_DEVTOOLS_CLI_PATH } = localEnv
  const serverDev = mode === 'serverdev' && UNI_PLATFORM === 'h5'
  const wechatStoreAdmin = mode === 'wechat' && UNI_PLATFORM === 'mp-weixin'
  const wechatSubPackages = new Set([
    'src/pages-core',
    'src/pages-member',
    'src/pages-pay',
    'src/pages-mall',
    'src/pages-statistics',
  ])
  // Reset generated routes, keeping easycom available when Uni initializes before UniPages.
  if (wechatStoreAdmin)
    writeFileSync(path.resolve('src/pages.json'), `${JSON.stringify({ ...pagesConfig, pages: [{ path: '' }] }, null, 2)}\n`)

  return defineConfig({
    envDir: './env', // 自定义env目录
    base: VITE_APP_PUBLIC_BASE,
    plugins: [
      serverDev && {
        name: 'serverdev-external-vendor-maps',
        enforce: 'post',
        transform(code, id) {
          const file = id.split('?')[0]
          if (file.includes('/node_modules/.vite/deps/') && file.endsWith('.js')) {
            return { code: `${code}\n//# sourceMappingURL=${path.basename(file)}.map`, map: { mappings: '' } }
          }
        },
      },
      // UniXXX 需要在 Uni 之前引入
      UniLayouts(),
      UniPlatform(),
      UniManifest(),
      UniComponents({ // 仅自动导入本地组件；Wot UI 与 z-paging 由 pages.config.ts 的 easycom 解析
        resolvers: [YudaoUiResolver(), ...(UNI_PLATFORM === 'h5' ? [H5UiResolver()] : [])],
        extensions: ['vue'],
        deep: true, // 是否递归扫描子目录，
        directoryAsNamespace: false, // 是否把目录名作为命名空间前缀，true 时组件名为 目录名+组件名，
        dts: UNI_PLATFORM === 'h5' ? false : 'src/types/local-components.d.ts', // 仅生成项目本地组件类型，避免拉入第三方 Vue 源码
      }),
      UniPages({
        exclude: [
          '**/components/**/**.*',
          '**/sections/**/**.*',
          ...(wechatStoreAdmin ? ['{bpm,contact,message}/**', 'index/{search,settings}/**'] : []),
        ],
        // pages 目录为 src/pages，分包目录不能配置在pages目录下！！
        // 是个数组，可以配置多个，但是不能为pages里面的目录！！
        subPackages: [
          'src/pages-core', // 这个是相对必要的路由，尽量留着（登录页、注册页、404页等）
          'src/pages-system', // “系统管理”模块
          'src/pages-infra', // “基础设施”模块
          'src/pages-bpm', // “工作流程”模块
          'src/pages-crm', // “客户管理”模块
          'src/pages-statistics', // “统计中心”模块
          'src/pages-iot', // “物联网”模块
          'src/pages-member', // “会员中心”模块
          'src/pages-pay', // “支付管理”模块
          'src/pages-mp', // “公众号管理”模块
          'src/pages-mall', // “商城管理”模块
          'src/pages-mes', // “生产制造”模块
          'src/pages-ai', // “人工智能”模块
          'src/pages-im', // “即时通讯”模块
          'src/pages-erp', // “ERP 管理”模块
          'src/pages-hrm', // “人力资源管理”模块
          'src/pages-fms', // “财务会计”模块
          'src/pages-wms', // “仓储管理”模块
          'src/pages-pms', // “项目管理”模块
          'src/pages-oa', // “OA 办公”模块
        ].filter(directory => !wechatStoreAdmin || wechatSubPackages.has(directory)),
        dts: 'src/types/uni-pages.d.ts',
      }),
      // UniOptimization 插件需要 page.json 文件，故应在 UniPages 插件之后执行
      UniOptimization({
        // 仅微信小程序开启，避免支付宝小程序运行报错
        enable: UNI_PLATFORM === 'mp-weixin',
        dts: {
          base: 'src/types',
        },
        logger: false,
      }),
      // 若存在改变 pages.json 的插件，请将 UniKuRoot 放置其后
      UniKuRoot({
        excludePages: [
          '**/components/**/**.*',
          '**/sections/**/**.*',
          'src/pages-crm/statistics/**',
        ],
      }),
      Uni(),
      {
        // 临时解决 dcloudio 官方的 @dcloudio/uni-mp-compiler 出现的编译 BUG
        // 参考 github issue: https://github.com/dcloudio/uni-app/issues/4952
        // 自定义插件禁用 vite:vue 插件的 devToolsEnabled，强制编译 vue 模板时 inline 为 true
        name: 'fix-vite-plugin-vue',
        configResolved(config) {
          const plugin = config.plugins.find(p => p.name === 'vite:vue')
          if (plugin && plugin.api && plugin.api.options) {
            plugin.api.options.devToolsEnabled = false
          }
        },
      },
      UnoCSS(),
      AutoImport({
        imports: ['vue', 'uni-app'],
        dts: 'src/types/auto-import.d.ts',
        dirs: ['src/hooks'], // 自动导入 hooks
        vueTemplate: true, // default false
      }),
      ViteRestart({
        // 通过这个插件，在修改vite配置文件则不需要重新运行也生效配置
        restart: ['vite.config.js', 'vite.config.ts'],
      }),
      // h5环境增加 BUILD_TIME 和 BUILD_BRANCH
      UNI_PLATFORM === 'h5' && {
        name: 'html-transform',
        transformIndexHtml(html) {
          return html.replace('%BUILD_TIME%', dayjs().format('YYYY-MM-DD HH:mm:ss')).replace('%VITE_APP_TITLE%', VITE_APP_TITLE)
        },
      },
      // 打包分析插件，h5 + 生产环境才弹出
      UNI_PLATFORM === 'h5'
      && mode === 'production'
      && visualizer({
        filename: './node_modules/.cache/visualizer/stats.html',
        open: true,
        gzipSize: true,
        brotliSize: true,
      }),
      // 原生插件资源复制插件 - 仅在 app 平台且启用时生效
      createCopyNativeResourcesPlugin(
        UNI_PLATFORM === 'app' && VITE_COPY_NATIVE_RES_ENABLE === 'true',
        {
          verbose: mode === 'development', // 开发模式显示详细日志
        },
      ),
      syncManifestPlugin(),
      vitePluginEruda({
        open: UNI_PLATFORM === 'h5' && mode === 'development',
      }),
      // 自动打开开发者工具插件 (必须修改 .env 文件中的 VITE_WX_APPID)
      // 上传或 CI 场景可通过 SKIP_OPEN_DEVTOOLS=true 跳过
      UNI_PLATFORM === 'mp-weixin' && SKIP_OPEN_DEVTOOLS !== 'true' && openDevTools({
        mode,
        wechatDevtoolsCliPath: WECHAT_DEVTOOLS_CLI_PATH,
      }),
    ],
    optimizeDeps: serverDev
      ? {
          // Keep uni-app's Vue/router exclusions; bundle only safe standalone dependencies.
          include: ['@vue/devtools-api', '@uni-helper/uni-env', '@wot-ui/ui/components/wd-toast', 'crypto-js', 'jsencrypt', 'pinia-plugin-persistedstate', 'dayjs'],
        }
      : undefined,
    define: {
      __VITE_APP_PROXY__: JSON.stringify(VITE_APP_PROXY_ENABLE),
    },
    css: {
      preprocessorOptions: {
        scss: {
          // 使用 Sass modern compiler，并静默依赖中的 Sass deprecation 提示。
          api: 'modern-compiler',
          silenceDeprecations: ['legacy-js-api', 'import'],
        },
      },
      postcss: {
        plugins: [
          // autoprefixer({
          //   // 指定目标浏览器
          //   overrideBrowserslist: ['> 1%', 'last 2 versions'],
          // }),
        ],
      },
    },

    resolve: {
      alias: {
        '@': path.join(process.cwd(), './src'),
        '@img': path.join(process.cwd(), './src/static/images'),
      },
    },
    server: {
      host: serverDev ? '127.0.0.1' : '0.0.0.0',
      strictPort: serverDev,
      hmr: serverDev ? { protocol: 'wss', host: 'mall.nexrur.ai', clientPort: 443, path: '/__vite_hmr' } : true,
      fs: { strict: true, allow: [process.cwd()], deny: ['.env', '.env.*', '**/env/.env*', '*.{crt,pem}', '**/.git/**'] },
      warmup: serverDev ? { clientFiles: ['./src/main.ts', './src/pages-core/auth/login.vue', './src/pages-mall/product/spu/index.vue', './src/pages-mall/trade/order/index.vue'] } : undefined,
      port: Number.parseInt(VITE_APP_PORT, 10),
      // 仅 H5 端生效，其他端不生效（其他端走build，不走devServer)
      proxy: JSON.parse(VITE_APP_PROXY_ENABLE)
        ? {
            [VITE_APP_PROXY_PREFIX]: {
              target: VITE_SERVER_BASEURL,
              changeOrigin: true,
              // 后端有/api前缀则不做处理，没有则需要去掉
              rewrite: path => path.replace(new RegExp(`^${VITE_APP_PROXY_PREFIX}`), ''),
            },
          }
        : undefined,
    },
    esbuild: {
      drop: VITE_DELETE_CONSOLE === 'true' ? ['console', 'debugger'] : [],
    },
    build: {
      sourcemap: false,
      // 方便非h5端调试
      // sourcemap: VITE_SHOW_SOURCEMAP === 'true', // 默认是false
      target: 'es6',
      // 开发环境不用压缩
      minify: mode === 'development' ? false : 'esbuild',
    },
  })
})
