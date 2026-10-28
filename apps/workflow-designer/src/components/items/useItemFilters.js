import { useState } from 'react'
import { initialFilters, filterItems } from './itemFilters.js'

export function useItemFilters(items, config) {
  const [state, setState] = useState(initialFilters)
  return { items: filterItems(items, state, config), state, setState, config, allItems: items }
}
