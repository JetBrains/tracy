package ai.jetbrains.tracy.core.policy

import ai.jetbrains.tracy.core.TracingManager

/**
 * Determines if content tracing is allowed for the specified content kind
 * by checking [TracingManager.contentCapturePolicy].
 *
 * @param kind The type of content being evaluated (e.g., [ContentKind.INPUT] or [ContentKind.OUTPUT]).
 * @return `true` if tracing is allowed for the given content kind based on the current content capture policy; `false` otherwise.
 */
fun contentTracingAllowed(kind: ContentKind): Boolean = when (kind) {
    ContentKind.INPUT -> TracingManager.contentCapturePolicy.captureInputs
    ContentKind.OUTPUT -> TracingManager.contentCapturePolicy.captureOutputs
}

/**
 * Same as [orRedacted] but with a [ContentCapturePolicy] instance taken from [TracingManager].
 */
fun String.orRedacted(kind: ContentKind): String = this.orRedacted(kind, TracingManager.contentCapturePolicy)

/**
 * Returns either the current string or "REDACTED" based on the provided content kind
 * and content capture policy. The behavior depends on whether the policy allows capturing
 * sensitive input or output content.
 *
 * @param kind Specifies the type of content (e.g., input or output) to evaluate.
 * @param policy The content capture policy that determines redaction behavior.
 * @return The original string if the policy allows capturing the specified content kind;
 * otherwise, "REDACTED".
 */
fun String.orRedacted(kind: ContentKind, policy: ContentCapturePolicy): String {
    return when (kind) {
        ContentKind.INPUT -> this.orRedactedInput(policy)
        ContentKind.OUTPUT -> this.orRedactedOutput(policy)
    }
}

/**
 * Represents the type of content that can be captured or redacted.
 *
 * This enum is used to specify whether the content is:
 * - [INPUT]: Sensitive input content (e.g., user/system messages, function call results).
 * - [OUTPUT]: Sensitive output content (e.g., assistant response messages).
 *
 * It is used in conjunction with a [ContentCapturePolicy] to determine
 * whether to capture or redact specific types of content during operations.
 *
 * @see ContentCapturePolicy
 * @see orRedacted
 * @see orRedactedInput
 * @see orRedactedOutput
 */
enum class ContentKind {
    INPUT, OUTPUT
}

/**
 * Same as [orRedactedInput] but with a [ContentCapturePolicy] instance taken from [TracingManager].
 */
fun String.orRedactedInput(): String = this.orRedactedInput(TracingManager.contentCapturePolicy)

/**
 * Returns either [this] string if the [ContentCapturePolicy] instance
 * is configured to capture sensitive request/response **input** content into span,
 * or a "REDACTED" placeholder otherwise.
 *
 * _[this] is expected to be an input (e.g., user/system messages).
 * For the output (e.g., assistant response messages) see [orRedactedOutput]._
 *
 * @return Either [this] string or "REDACTED".
 */
fun String.orRedactedInput(policy: ContentCapturePolicy): String {
    return when (policy.captureInputs) {
        true -> this
        false -> "REDACTED"
    }
}

/**
 * Same as [orRedactedOutput] but with a [ContentCapturePolicy] instance taken from [TracingManager].
 */
fun String.orRedactedOutput() = this.orRedactedOutput(TracingManager.contentCapturePolicy)

/**
 * Returns either [this] string if the [ContentCapturePolicy] instance
 * is configured to capture sensitive request/response **output** content into span,
 * or a "REDACTED" placeholder otherwise.
 *
 * _[this] is expected to be an output (e.g., assistant response messages).
 * For the input (e.g., user/system messages) see [orRedactedInput]._
 *
 * @return Either [this] string or "REDACTED".
 */
fun String.orRedactedOutput(policy: ContentCapturePolicy): String {
    return when (policy.captureOutputs) {
        true -> this
        false -> "REDACTED"
    }
}