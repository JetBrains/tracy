package ai.dev.kit.providers.wandb.tracing

import ai.dev.kit.fluent.TestAutologTracingBase
import ai.dev.kit.fluent.TestFluentTracingBase
import ai.dev.kit.fluent.TestSuspendFluentTracingBase
import ai.dev.kit.providers.wandb.KotlinWandbClient
import ai.dev.kit.providers.wandb.WandbTracingTests
import ai.dev.kit.providers.wandb.getAllTracesForProject
import org.junit.jupiter.api.Tag

@Tag("SkipForNonLocal")
class TestAutologTracingWandb : TestAutologTracingBase(
    ::getAllTracesForProject,
    KotlinWandbClient
), WandbTracingTests

@Tag("SkipForNonLocal")
class TestFluentTracingWandb : TestFluentTracingBase(
    ::getAllTracesForProject,
    KotlinWandbClient
), WandbTracingTests

@Tag("SkipForNonLocal")
class TestSuspendFluentTracingWandb : TestSuspendFluentTracingBase(
    ::getAllTracesForProject,
    KotlinWandbClient
), WandbTracingTests
