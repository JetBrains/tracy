package ai.dev.kit.providers.wandb.tracing

import ai.dev.kit.fluent.TestAutologTracingBase
import ai.dev.kit.fluent.TestFluentTracingBase
import ai.dev.kit.fluent.TestSuspendFluentTracingBase
import ai.dev.kit.providers.wandb.KotlinWandbClient
import ai.dev.kit.providers.wandb.WandbTracingTests
import ai.dev.kit.providers.wandb.getAllTracesForProject

class TestDumbAutologTracingWandb : TestAutologTracingBase(
    ::getAllTracesForProject,
    KotlinWandbClient
), WandbTracingTests

class TestDumbFluentTracingWandb : TestFluentTracingBase(
    ::getAllTracesForProject,
    KotlinWandbClient
), WandbTracingTests

class TestDumbSuspendFluentTracingWandb : TestSuspendFluentTracingBase(
    ::getAllTracesForProject,
    KotlinWandbClient
), WandbTracingTests
