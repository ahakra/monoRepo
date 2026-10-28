const values = (value) =>
  (Array.isArray(value) ? value.flat(Infinity) : [value])
    .filter((entry) => entry !== null && entry !== undefined)
    .map(String)
export const initialFilters = () => ({
  query: '',
  searchField: '',
  facets: {},
  createdFrom: '',
  createdTo: '',
  sort: 'updated-desc',
})

function dateBoundary(value, nextDay = false) {
  if (!value) return null
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day + (nextDay ? 1 : 0)).getTime()
}

export function facetOptions(items, facet) {
  if (facet.options) return facet.options
  return [...new Set(items.flatMap((item) => values(facet.values(item))))]
    .sort((a, b) => a.localeCompare(b))
    .map((value) => ({ value, label: value.replaceAll('_', ' ').toLowerCase() }))
}

export function filterItems(items, state, config) {
  const terms = state.query.trim().toLocaleLowerCase().split(/\s+/u).filter(Boolean)
  const from = dateBoundary(state.createdFrom)
  const to = dateBoundary(state.createdTo, true)
  const fields = state.searchField
    ? config.searchFields.filter((field) => field.id === state.searchField)
    : config.searchFields
  const filtered = items.filter((item) => {
    const text = fields
      .flatMap((field) => values(field.values(item)))
      .join(' ')
      .toLocaleLowerCase()
    if (!terms.every((term) => text.includes(term))) return false
    if (
      !(config.facets || []).every(
        (facet) =>
          !state.facets[facet.id] || values(facet.values(item)).includes(state.facets[facet.id]),
      )
    )
      return false
    if (from !== null || to !== null) {
      const created = item.createdAt ? Date.parse(item.createdAt) : NaN
      if (
        !Number.isFinite(created) ||
        (from !== null && created < from) ||
        (to !== null && created >= to)
      )
        return false
    }
    return true
  })
  const name = (item) => String(item.name ?? item.spec?.name ?? '')
  return filtered.sort((left, right) => {
    let result
    if (state.sort === 'name-asc' || state.sort === 'name-desc') {
      result = name(left).localeCompare(name(right), undefined, {
        numeric: true,
        sensitivity: 'base',
      })
      if (state.sort === 'name-desc') result *= -1
    } else {
      const key = state.sort.startsWith('created') ? 'createdAt' : 'updatedAt'
      const a = left[key] ? Date.parse(left[key]) : NaN
      const b = right[key] ? Date.parse(right[key]) : NaN
      // Unknown legacy dates always sort last, in either direction.
      if (!Number.isFinite(a) || !Number.isFinite(b))
        result = Number.isFinite(a) ? -1 : Number.isFinite(b) ? 1 : 0
      else result = (a - b) * (state.sort.endsWith('asc') ? 1 : -1)
    }
    return result || String(left.id).localeCompare(String(right.id))
  })
}
