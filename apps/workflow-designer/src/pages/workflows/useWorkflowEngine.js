import { useEffect, useRef, useState } from 'react'
import {
  runWorkflow,
  workflowEngineApi,
  workflowEventsApi,
  workflowBatchesApi,
} from './workflowsApi.js'

export function useWorkflowEngine(id, isSaved) {
  const [state, setState] = useState({ status: null, loading: isSaved, statusError: '' })
  const [action, setAction] = useState('')
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  const [events, setEvents] = useState([])
  const [traceError, setTraceError] = useState('')
  const [truncated, setTruncated] = useState(false)
  const [executions, setExecutions] = useState([])
  const [batchError, setBatchError] = useState('')
  const [hasMoreBatches, setHasMoreBatches] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const batchCursor = useRef(null)
  const cursor = useRef(0)
  const traceSession = useRef(null)
  const inFlight = useRef(false)
  const revision = useRef(0)
  const mounted = useRef(false)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  useEffect(() => {
    if (!isSaved) return
    let active = true
    let timer
    async function poll() {
      const requestedAt = revision.current
      try {
        const [statusResult, traceResult, batchResult] = await Promise.allSettled([
          workflowEngineApi(id),
          workflowEventsApi(id, cursor.current),
          workflowBatchesApi(id),
        ])
        if (!active || requestedAt !== revision.current || inFlight.current) return
        if (statusResult.status === 'fulfilled')
          setState({ status: statusResult.value, loading: false, statusError: '' })
        else
          setState((current) => ({
            ...current,
            loading: false,
            statusError: statusResult.reason.message,
          }))
        if (batchResult.status === 'fulfilled') {
          const page = batchResult.value
          setExecutions((current) => {
            const merged = new Map(current.map((batch) => [batch.executionId, batch]))
            page.items.forEach((batch) => merged.set(batch.executionId, batch))
            return [...merged.values()].sort((a, b) => a.cursor - b.cursor)
          })
          if (batchCursor.current == null || !page.hasMore) {
            batchCursor.current = page.nextBefore
            setHasMoreBatches(page.hasMore)
          }
          setBatchError('')
        } else setBatchError(batchResult.reason.message)
        if (traceResult.status === 'fulfilled') {
          let page = traceResult.value
          const changedSession = traceSession.current && traceSession.current !== page.sessionId
          if (changedSession && !page.reset) page = await workflowEventsApi(id, 0)
          if (!active || requestedAt !== revision.current || inFlight.current) return
          const reset = changedSession || page.reset
          traceSession.current = page.sessionId
          cursor.current = page.nextAfter
          setEvents((current) => {
            const merged = new Map((reset ? [] : current).map((event) => [event.sequence, event]))
            page.events.forEach((event) => merged.set(event.sequence, event))
            return [...merged.values()].sort((a, b) => a.sequence - b.sequence).slice(-2000)
          })
          setTruncated((current) => (reset ? page.truncated : current || page.truncated))
          setTraceError('')
        } else setTraceError(traceResult.reason.message)
      } catch (error) {
        if (active && requestedAt === revision.current && !inFlight.current)
          setTraceError(error.message)
      } finally {
        if (active) timer = setTimeout(poll, 2500)
      }
    }
    poll()
    return () => {
      active = false
      clearTimeout(timer)
    }
  }, [id, isSaved, reload])

  async function loadMoreBatches() {
    if (loadingMore) return
    setLoadingMore(true)
    try {
      const page = await workflowBatchesApi(id, batchCursor.current)
      if (!mounted.current) return
      setExecutions((current) => {
        const merged = new Map(current.map((batch) => [batch.executionId, batch]))
        page.items.forEach((batch) => merged.set(batch.executionId, batch))
        return [...merged.values()].sort((a, b) => a.cursor - b.cursor)
      })
      batchCursor.current = page.nextBefore
      setHasMoreBatches(page.hasMore)
      setBatchError('')
    } catch (error) {
      if (mounted.current) setBatchError(error.message)
    } finally {
      if (mounted.current) setLoadingMore(false)
    }
  }

  async function perform(action, saveBeforeRun) {
    if (inFlight.current) return
    inFlight.current = true
    revision.current++
    setAction(action)
    setError('')
    try {
      const status =
        action === 'start'
          ? await runWorkflow(id, saveBeforeRun)
          : await workflowEngineApi(id, 'stop')
      if (mounted.current) setState({ status, loading: false, statusError: '' })
    } catch (error) {
      if (mounted.current) setError(error.message)
    } finally {
      revision.current++
      inFlight.current = false
      if (mounted.current) {
        setAction('')
        setReload((value) => value + 1)
      }
    }
  }

  return {
    ...state,
    events,
    executions,
    batchError,
    hasMoreBatches,
    loadingMore,
    loadMoreBatches,
    traceError,
    truncated,
    action,
    busy: !!action,
    error,
    start: (saveBeforeRun) => perform('start', saveBeforeRun),
    stop: () => perform('stop'),
    retry: () => {
      setState((current) => ({ ...current, loading: true }))
      setReload((value) => value + 1)
    },
  }
}
