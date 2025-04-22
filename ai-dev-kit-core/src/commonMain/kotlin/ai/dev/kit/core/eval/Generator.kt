package ai.dev.kit.core.eval

/**
 * An encapsulation of the AI feature under test.
 */
interface Generator<AIInputT: AIInput, AIOutputT: AIOutput> {
    suspend fun generate(input: AIInputT): AIOutputT
    val metadata: GeneratorMetadata
}

/**
 * Identifier for this particular [Generator] subclass
 * that will be used in tracing to distinguish between
 * different [Generator]s.
 */
interface GeneratorMetadata {
    // TODO: get rid of these params, generalize away from MLFlow ModelParams
    val modelName: String
    val temperature: Double
    val prompt: String
}
