package ai.dev.kit.tracing.fluent

import kotlinx.coroutines.CoroutineScope

/**
 * Holder object for ProjectID and sessionID
 */
expect object TracingSessionProvider {
    /**
     * In Langfuse, ProjectID is a hash-like string. It's currently determined by the API keys,
     * no way to change it programmatically.
     * E.g. `https://langfuse.com/project/abcde123/traces`
     *
     * In W&B Weave, ProjectID is the same thing as the project name, e.g.
     *  `https://wandb.ai/weave-entity/project-name`
     * Where "project-name" is the project name
     */
    val currentProjectId: String?

    /**
     * In Langfuse, [Session](https://langfuse.com/docs/tracing-features/sessions)
     * is a label that can be used to group related traces.
     * Session ID is the same as the human-readable session name. It is created automatically
     * when the first trace labeled with it is uploaded.
     *
     *
     * In W&B Weave, there is no such thing as a Session.
     */
    val currentSessionId: String?
}

/**
 *  Executes the block with a projectId set. It has no effect on Langfuse.
 */
expect suspend fun <T> withProjectId(id: String, block: suspend CoroutineScope.() -> T): T

/**
 * Same as [withProjectId] but blocking.
 */
expect fun <T> withProjectIdBlocking(id: String, block: suspend CoroutineScope.() -> T): T

/**
 * Executes the block with a sessionId set. Has no effect on W&B Weave.
 */
expect suspend fun <T> withSessionId(id: String, block: suspend CoroutineScope.() -> T): T

/**
 * Same as [withSessionId] but blocking.
 */
expect fun <T> withSessionIdBlocking(id: String?, block: suspend CoroutineScope.() -> T): T
