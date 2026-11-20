import type { DeliveryPickUpStore } from '@/api/mall/trade/delivery/pick-up-store'

export function isPickUpStoreIncomplete(store: DeliveryPickUpStore) {
  return !store.phone?.trim() || !store.openingTime || !store.closingTime
    || !Number.isFinite(store.latitude) || !Number.isFinite(store.longitude)
}
