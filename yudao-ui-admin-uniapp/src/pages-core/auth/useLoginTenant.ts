import { onMounted, ref } from 'vue'
import { getTenantByWebsite } from '@/api/login'
import { useUserStore } from '@/store/user'
import { isPositiveId, parseId } from '@/utils/id'

/** A single-store login resolves its context without exposing a platform tenant selector. */
export function useLoginTenant() {
  const user = useUserStore()
  const initializing = ref(true)
  const ready = ref(false)
  const error = ref('')
  let running = false

  async function loadTenant() {
    if (running)
      return
    running = true
    initializing.value = true
    ready.value = false
    error.value = ''
    try {
      if (import.meta.env.VITE_APP_TENANT_ENABLE !== 'true') {
        ready.value = true
        return
      }
      user.setVisitTenantId(null)
      user.tenantId = null
      let website = ''
      // #ifdef H5
      website = window.location.hostname
      // #endif
      // #ifdef MP
      website = uni.getAccountInfoSync?.()?.miniProgram?.appId || ''
      // #endif
      const tenant = website ? await getTenantByWebsite(website) : null
      let id = website ? parseId(tenant?.id) : parseId(import.meta.env.VITE_APP_DEFAULT_LOGIN_TENANT_ID)
      // #ifdef MP
      // Newly registered owner apps can use the explicitly configured store.
      // This only selects the login context; account/password and permissions remain server-validated.
      if (!tenant)
        id = parseId(import.meta.env.VITE_APP_DEFAULT_LOGIN_TENANT_ID)
      // #endif
      if (!isPositiveId(id))
        throw new Error('Invalid store binding')
      user.setTenantId(id)
      ready.value = true
    } catch {
      error.value = '暂时无法连接店铺，请重试'
    } finally {
      initializing.value = false
      running = false
    }
  }

  onMounted(loadTenant)
  return { initializing, ready, error, loadTenant }
}
