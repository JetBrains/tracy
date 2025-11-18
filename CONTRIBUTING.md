# Contributing Guidelines

One can contribute to the project by reporting issues or submitting changes via pull request.

## Reporting Issues

Please use the [AI Dev Kit official YouTrack project (TODO)]() for filing feature requests and bug reports.

Questions about usage and general inquiries are better suited for StackOverflow or the [#ai-dev-kit (TODO)]() channel in
the KotlinLang Slack.

## Submitting Changes

Submit pull requests [here](https://github.com/JetBrains/ai-dev-kit/pulls).  
Keep in mind that maintainers will need to support any code you contribute, so please follow these guidelines:

* If you make code changes:
    * Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/reference/coding-conventions.html).
    * [Build the project](#building) to ensure everything compiles and all tests pass.
* If you fix a bug:
    * Write a test that reproduces the bug.
    * Follow the existing test style used in this project.
* If you want to work on an existing issue:
    * Comment on the issue first.
    * Ensure the issue clearly describes both the problem and an agreed-upon solution.
    * If no solution has been suggested, propose one for discussion.

## Documentation

The documentation is published on TODO. To propose changes or improvements to the documentation, go to
the TODO repository.

## Building

### Prerequisites

- JDK 17+
- Kotlin 1.9.0+

### How to build

This library is built with Gradle.

* Run `./gradlew build` to build. It also runs all the tests.
* Run `./gradlew <module>:check` to test the module you are looking at to speed
  things up during development.

You can import this project into IDEA, but you have to delegate build actions
to Gradle (in Preferences -> Build, Execution, Deployment -> Build Tools -> Gradle -> Build and run).

## Running unit tests

### Unit Test Requirements

Some autotracing tests depend on external LLM services and require valid API tokens.
To run them locally, set the following environment variable:

To run all tests with a single LLM provider, set the following environment variables:
- `LLM_PROVIDER_API_KEY`
- `LLM_PROVIDER_URL`

To provide specific credentials for a single LLM provider, set the following environment variables:
- `OPENAI_API_KEY` for OpenAI
- `GEMINI_API_KEY` for Google
- `ANTHROPIC_API_KEY` for Anthropic

and leave the `LLM_PROVIDER_URL` variable unset.

### Running all unit tests

To run all JVM tests in the project:

```bash
./gradlew allTests
```

### Running tests from a specific module

To run tests from a specific module:

```bash
./gradlew :<module>:allTests
```

For example, to run JVM tests from the tracing core (with annotation-base functionality):

```bash
./gradlew :tracing:tracing-core:allTests
```

### Running a specific test class

To run a specific test class:

```bash
./gradlew :<module>:allTests --tests "fully.qualified.TestClassName"
```

For example:

```bash
./gradlew :tracing:tracing-core:jvmTest --tests "ai.dev.kit.tracing.fluent.FluentTracingTest"
```

### Skipping tests for specific LLM providers
If you don't have API keys for certain LLM providers, you can skip the associated tests using the 'skip.llm.providers' system property. This is useful when you want to run tests but only have API keys for some of the providers.

To skip tests for specific providers, use the -Dskip.llm.providers flag with a comma-separated list of provider IDs:

```bash
./gradlew :integration-tests:jvmIntegrationTest -Dskip.llm.providers=openai,gemini
```

Available provider IDs:
- `openai` - skipping OpenAI tests
- `gemini` - skipping Gemini tests
- `anthropic` - skipping Anthropic tests


### Running integration tests

#### Requirements for integration tests

TODO
