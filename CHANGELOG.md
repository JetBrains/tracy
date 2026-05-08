## Unreleased

- **AUDIT-FIX** Fixed `http.response.status_code` attribute key (was incorrectly set as `http.status_code`) in `LLMTracingAdapter`.
- **AUDIT-FIX** Added `server.address` and `server.port` span attributes to all LLM request spans via `LLMTracingAdapter`.
- **AUDIT-FIX** Added `port` field to `TracyHttpUrl` interface and all implementations (`TracyHttpUrlImpl`, Ktor adapters) to support `server.port` tracing.
- **NEW-COVERAGE** `AnthropicLLMTracingAdapter` now routes each HTTP call by URL path, sets `anthropic.api.type` and `gen_ai.operation.name` on every span, and emits detailed span attributes for the following Anthropic API endpoints:
  - **Messages** (`/v1/messages`) — model, token usage, prompt/completion content, tool calls, media attachments (unchanged behavior, now routed explicitly).
  - **Count Tokens** (`/v1/messages/count_tokens`) — request model, message prompts, `gen_ai.usage.input_tokens`.
  - **Message Batches** (`/v1/messages/batches/...`) — `gen_ai.response.batch.id`, `processing_status`, `created_at`, `expires_at`, `request_counts.*`; `gen_ai.request.batch.size` on create.
  - **Files** (`/v1/files/...`) — `gen_ai.response.file.id/filename/mime_type/size_bytes/downloadable/created_at`; `gen_ai.request.file.*` from multipart form data on upload; `gen_ai.output.type=file_deleted` on delete.
  - **Models** (`/v1/models/...`) — `gen_ai.response.model.id/display_name/created_at/max_input_tokens/max_output_tokens/capabilities.vision|batch|citations`; `gen_ai.request.model` from URL path on retrieve.
  - List responses set `gen_ai.response.list.count/has_more/first_id/last_id`.
