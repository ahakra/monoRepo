import { useEffect, useState } from 'react'

export function useWorkflowCatalog(fetchItems) {
  const [items, setItems] = useState([])
  const [state, setState] = useState({ loading: true, error: '', loaded: false })
  const [reload, setReload] = useState(0)
  useEffect(() => {
    let active = true
    fetchItems()
      .then((result) => {
        if (active) {
          setItems(result)
          setState({ loading: false, error: '', loaded: true })
        }
      })
      .catch((error) => {
        if (active) setState({ loading: false, error: error.message, loaded: false })
      })
    return () => {
      active = false
    }
  }, [fetchItems, reload])
  function refresh() {
    setState((current) => ({ ...current, loading: true, error: '' }))
    setReload((value) => value + 1)
  }
  return { items, state, refresh }
}
