import { useEffect, useState } from 'react'
import { workflowBatchApi } from './workflowsApi.js'

// Read historical stage snapshots independently of the limited live event window.
export function useExecutionDetail(workflowId, executionId, updatedAt) {
  const [state, setState] = useState({ id: null, detail: null, error: '' })
  useEffect(() => {
    if (!executionId) return
    let active = true
    workflowBatchApi(workflowId, executionId).then(
      (detail) => {
        if (active) setState({ id: executionId, detail, error: '' })
      },
      (error) => {
        if (active) setState({ id: executionId, detail: null, error: error.message })
      },
    )
    return () => {
      active = false
    }
  }, [workflowId, executionId, updatedAt])
  return state.id === executionId ? state : { detail: null, error: '' }
}
