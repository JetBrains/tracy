# Contributing Guidelines

One can contribute to the project by reporting issues or submitting changes via pull request.

## Reporting Issues

Please use the [AI Dev Kit official YouTrack project (TODO)]() for filing feature requests and bug reports,
using one of the provided templates:

- [Bug Report Template](https://youtrack.jetbrains.com/newIssue?project=JBAI&summary=bug%3A+_add+a+name+here_&description=%23%23+Description+of+the+issue%0A%0A*Briefly+describe+the+problem*%0A%0A---%0A%0A%23%23+**Steps+to+reproduce**%0A%0A*How+can+we+reproduce+the+issue%3F*%0A%0A1.+%0A%0A2.+%0A%0A3.+%0A%0A---%0A%0A%23%23+Expected+behavior%0A%0A*What+did+you+expect+to+happen%3F*%0A%0A---%0A%0A%23%23+Actual+behavior%0A%0A*What+happened+instead%3F*%0A%0A---%0A%0A%23%23+Environment%0A%0A*+Tracy+version%3A%0A*+Kotlin%2FJava+version%3A%0A*+Build+tool%3A%0A*+Tracing+backend%3A%0A++...%0A%0A---%0A%0A%23%23+Additional+information%0A%0A*Include+any+extra+context%2C+examples%2C+screenshots%2C+or+ideas+that+may+help+us+investigate+the+issue.+Screenshots%2C+code+snippets%2C+and+traces+can+also+be+placed+in+the+relevant+sections+above+%28e.g.%2C+Steps+to+reproduce+or+Actual+behaviour%29.*)
- [Feature Request Template](https://youtrack.jetbrains.com/newIssue?project=JBAI&summary=feature%3A+_add+a+name+here_&description=%23%23+Summary%0A%0A*Briefly+describe+the+feature+you+would+like+to+see*%0A%0A---%0A%0A%23%23+Motivation%0A%0A*Why+do+you+need+this+feature%3F+What+problem+does+it+solve+for+your+workflow+or+users%3F*%0A%0A---%0A%0A%23%23+Proposed+solution%0A%0A*How+should+this+feature+work%3F+What+API%2C+behavior%2C+or+configuration+would+you+expect%3F*%0A%0A---%0A%0A%23%23+Alternatives+considered%0A%0A*Have+you+tried+any+existing+workarounds%3F+Why+are+they+not+enough%3F*%0A%0A---%0A%0A%23%23+Additional+information%0A%0A*Include+any+extra+context%2C+examples%2C+screenshots%2C+or+ideas+that+may+help+us+understand+the+request*)

Questions about usage and general inquiries are better suited for StackOverflow or the [#tracy (TODO)]() channel in
the KotlinLang Slack.

## Submitting Changes

Submit pull requests [here](https://github.com/JetBrains/tracy/pulls).  
Keep in mind that maintainers will need to support any code you contribute, so please follow these guidelines:

* Fill out the automatically applied message in PR
    * Add a clear description
    * Complete all relevant checklist items
    * Delete unrelated sections
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

> [!NOTE]
> Provider-specific environment variables take precedence over `LLM_PROVIDER_API_KEY` in their corresponding tests.
> For example, if both `LLM_PROVIDER_API_KEY` and `OPENAI_API_KEY` are set, the OpenAI tests will use `OPENAI_API_KEY`.

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
./gradlew :tracing:core:allTests
```

### Running a specific test class

To run a specific test class:

```bash
./gradlew :<module>:allTests --tests "fully.qualified.TestClassName"
```

For example:

```bash
./gradlew :tracing:core:jvmTest --tests "ai.dev.kit.tracing.fluent.FluentTracingTest"
```

### Skipping tests for specific LLM providers
If you don't have API keys for certain LLM providers, you can skip the associated tests using the `skip.llm.providers` system property.

To skip tests for specific providers, use the `-Dskip.llm.providers` flag with a comma-separated list of provider IDs.

For example, to skip tests for OpenAI and Gemini LLMs when running all tests in the project:

```bash
./gradlew allTests -Dskip.llm.providers=openai,gemini
```

Available provider IDs:
- `openai` - skipping OpenAI tests
- `gemini` - skipping Gemini tests
- `anthropic` - skipping Anthropic tests


### Running integration tests

#### Requirements for integration tests

TODO
