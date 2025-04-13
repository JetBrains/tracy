package ai.dev.kit.core.fluent

class SpanType private constructor() {
    companion object {
        const val LLM = "LLM"
        const val CHAIN = "CHAIN"
        const val AGENT = "AGENT"
        const val TOOL = "TOOL"
        const val CHAT_MODEL = "CHAT_MODEL"
        const val RETRIEVER = "RETRIEVER"
        const val PARSER = "PARSER"
        const val EMBEDDING = "EMBEDDING"
        const val RERANKER = "RERANKER"
        const val UNKNOWN = "UNKNOWN"
    }
}
