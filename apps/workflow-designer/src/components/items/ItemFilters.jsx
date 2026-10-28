import { initialFilters, facetOptions } from './itemFilters.js'
import './ItemFilters.css'

export function ItemFilters({ filter, label, disabled = false }) {
  const { state, setState, config, allItems, items } = filter
  function change(key, value) {
    setState((current) => ({ ...current, [key]: value }))
  }
  return (
    <section className="item-filters" aria-label={`Search and filter ${label}`}>
      <fieldset disabled={disabled}>
        <div className="item-filter-controls">
          <label>
            Search {label}
            <input
              type="search"
              value={state.query}
              onChange={(e) => change('query', e.target.value)}
              placeholder="Search saved items…"
            />
          </label>
          <label>
            Search in
            <select
              value={state.searchField}
              onChange={(e) => change('searchField', e.target.value)}
            >
              <option value="">All searchable details</option>
              {config.searchFields.map((field) => (
                <option key={field.id} value={field.id}>
                  {field.label}
                </option>
              ))}
            </select>
          </label>
          {(config.facets || []).map((facet) => (
            <label key={facet.id}>
              {facet.label}
              <select
                value={state.facets[facet.id] || ''}
                onChange={(e) => change('facets', { ...state.facets, [facet.id]: e.target.value })}
              >
                <option value="">All</option>
                {facetOptions(allItems, facet).map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
          ))}
          <label>
            Created from
            <input
              type="date"
              value={state.createdFrom}
              onChange={(e) => change('createdFrom', e.target.value)}
            />
          </label>
          <label>
            Created through
            <input
              type="date"
              value={state.createdTo}
              onChange={(e) => change('createdTo', e.target.value)}
            />
          </label>
          <label>
            Sort by
            <select value={state.sort} onChange={(e) => change('sort', e.target.value)}>
              <option value="updated-desc">Recently updated</option>
              <option value="created-desc">Newest created</option>
              <option value="created-asc">Oldest created</option>
              <option value="name-asc">Name A–Z</option>
              <option value="name-desc">Name Z–A</option>
            </select>
          </label>
        </div>
        <div className="item-filter-status">
          <span role="status">
            {items.length} of {allItems.length} {label}
          </span>
          <button type="button" onClick={() => setState(initialFilters())}>
            Clear filters
          </button>
        </div>
        {state.createdFrom && state.createdTo && state.createdFrom > state.createdTo && (
          <p role="alert">Created from must be on or before Created through.</p>
        )}
      </fieldset>
    </section>
  )
}

export function NoMatchingItems({ filter }) {
  return (
    <div className="config-empty">
      <h3>No matching items</h3>
      <p>Try a different search or clear the filters.</p>
      <button type="button" onClick={() => filter.setState(initialFilters())}>
        Clear filters
      </button>
    </div>
  )
}

export function ItemTimestamps({ item }) {
  function time(value) {
    return value && Number.isFinite(Date.parse(value)) ? (
      <time dateTime={value}>{new Date(value).toLocaleString()}</time>
    ) : (
      <span title="Creation time was not recorded for this legacy item.">Unknown</span>
    )
  }
  return (
    <p className="item-timestamps">
      Created {time(item.createdAt)} · Updated {time(item.updatedAt)}
    </p>
  )
}
