import type { MapCoordinate } from '@/utils/coordinate'
import template from './amap-picker.html?raw'

export const AMAP_PICKER_RESULT = 'yuanmall:pickup-location'

/** 独立 iframe 中按需加载高德；地址解析只移动视图，必须由店主明确选点。 */
export function buildAMapPickerHtml(options: { address: string, city: string, point?: MapCoordinate }) {
  const key = (import.meta.env.VITE_PUBLIC_AMAP_KEY_JS || import.meta.env.VITE_AMAP_KEY)?.trim()
  const securityJsCode = (import.meta.env.VITE_PUBLIC_AMAP_CS_JS || import.meta.env.VITE_AMAP_SECURITY_JSCODE)?.trim()
  if (!key || !securityJsCode) {
    return ''
  }
  const config = JSON.stringify({ ...options, key, securityJsCode, messageType: AMAP_PICKER_RESULT, parentOrigin: window.location.origin })
    .replace(/</g, '\\u003c')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029')
  return template.replace('/* PICKER_CONFIG */ {}', () => config)
}
