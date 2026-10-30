package com.ahvakra.camelintegration.workflows.domain;

import java.util.UUID;

/** Lets draft management protect active executions without depending on an engine implementation. */
public interface WorkflowExecutionState {
    boolean isRunning(UUID workflowId);
}
