# Documentation

## Module Structure

The docs module is organized as follows:

| Folder         | Description                                                                          |
|----------------|--------------------------------------------------------------------------------------|
| **docs/**      | Contains Markdown files with user documentation.                                     |
| **overrides/** | Contains custom overrides for the MkDocs theme.                                      |
| **src/**       | Knit generated source code from documentation code snippets, should not be commited. |


## Initial Setup

### View Documentation Locally

1. To run the documentation website locally, you need to install [uv](https://docs.astral.sh/uv/getting-started/installation/).
2. Sync the project (this will create proper `.venv` and install dependencies, no manual Python setup required):
```bash
uv sync --frozen --all-extras
```
3. Start the local server with the documentation:
```bash
uv run mkdocs serve
```
4. To generate static HTML files:
```bash
uv run mkdocs build
```

The documentation will be available at the URL printed in the output and will automatically reload when you make changes to the documentation files.

---

## Documentation System

### MkDocs

The documentation is built using [MkDocs](https://www.mkdocs.org/) with the Material theme. The configuration is defined in [mkdocs.yml](./mkdocs.yml) which specifies:

1. Navigation structure
2. Theme configuration
3. Markdown extensions
4. Repository links

**The documentation is available at:** _TODO(add link)_.

---

### Code Snippets Verification

We use the [kotlinx-knit](https://github.com/Kotlin/kotlinx-knit) library to ensure code snippets in documentation are compilable and up to date with the latest framework version. Knit provides a Gradle plugin that extracts specially annotated Kotlin code snippets from Markdown files and generates Kotlin source files._

There are two options of adding new code snippets into your documentation. The syntax remains the same; the only difference is how they are included in the documentation md-files.

#### Syntax

1. Put an example annotation comment (`<!--- KNIT example-[md-file-name]-01.kt -->`) after every code block. You do not need to put correct indexes, set the `01` for each example, and they will be updated automatically after the first knit run:
   ````
   ```
   val result = 123
   println(result)
   ```
   <!--- KNIT example-[md-file-name]-01.kt -->
   ````
2. To add imports, you need to write the include directive `<!--- INCLUDE ... -->` before the code block:
   ````
   <!--- INCLUDE
    import com.example.Component
    -->
   ```
   val c = Component(...)
   ```
   <!--- KNIT example-[md-file-name]-01.kt -->
   ````
3. If you need to wrap your code with main or other functions, use the include comment `<!--- INCLUDE ... -->` for prefix, and the suffix comment `<!--- SUFFIX ... -->` for suffix:
   ````
    <!--- INCLUDE
    import com.example.Component
    fun main() {
    -->
    <!--- SUFFIX
    }
    -->
    ```kotlin
    val c = Component()
    ```
    <!--- KNIT example-[md-file-name]-01.kt -->
   ````

When compiled, the above code snippet will turn into:
```kotlin
// example-[md-file-name]-01.kt
import com.example.Component

fun main() {
   val c = Component()
}
```

_For more information, follow the examples in the [kotlinx-knit](https://github.com/Kotlin/kotlinx-knit) repository or refer to already annotated code snippets in the documentation._

> [!NOTE]
> If your code snippets contain some components that require a dependency on a specific module,
> you should add this module into [build.gradle.kts](./build.gradle.kts) as an entry in the `dependencies {}` Gradle block.


#### Add Code Snippets

You can add code snippets via one of the options:

1. Inline the code snippet following the syntax mentioned above directly into your md-file.
2. Create a separate md-file with only the code snippet and import it into another md-file. For instance, you may create `docs/examples/code-snippet.md` and import it into `docs/examples.md` as follows:
   ````
   # docs/examples/code-snippet.md
   
   <!--- INCLUDE
   import java.time.Duration
   
   fun main() {
   -->
   <!--- SUFFIX
   }
   -->
   ```kotlin
   val d = Duration.ofSeconds(60)
   println(d)
   ```
   <!--- KNIT example-code-snippet-01.kt -->
   ````
   
   ````
   # docs/examples.md
   
   Here are the examples of my code:
   {% include 'examples/code-snippet.md' %}
   ````


#### Fix Code Snippets

Here are the steps how to fix compilation errors that occur in your documentation code snippets.

1. Run `:docs:knitAssemble` task to clean old knit-generated files, extract fresh code snippets to `./src/test/kotlin`, and assemble the docs project:
    ```bash
    ./gradlew :docs:knitAssemble
    ```
2. Navigate to the file with the compilation error `example-[md-file-name]-[index].kt`.
3. Fix the error in the file.
4. Navigate to this code snippet in Markdown `md-file-name.md` by searching for `<!--- KNIT example-[md-file-name]-[index].kt -->`.
5. Update the code snippet to reflect the fixing changes you just introduced in the `kt`-file:
   1. Update imports (usually they are provided in the `<!--- INCLUDE -->` section).
   2. Edit code (remember the tabulation when you copy and paste from the `kt`-file).


> [!NOTE]
> If your documentation contains instructions with code snippets,
> use manual numbering (for example, `1) 2) 3)`) instead of Markdown built-in numbered lists.
> This ensures compatibility with the KNIT tool, as KNIT annotations must remain unindented (starting at column 0) and cannot be nested within numbered Markdown lists.
>
> Here is an example:
> ``````markdown
> 1) Step description for the first action:
> 
> <!--- INCLUDE
> import com.example.Component -->
> ```kotlin
> // Code snippet
> ```
> <!--- KNIT example-[md-file-name]-01.kt -->
> 
> 
> 2) Step description for the second action:
> 
> <!--- INCLUDE
> import com.example.Component
> fun main() {
> -->
> <!--- SUFFIX
> }
> -->
> ```kotlin
> // Another code snippet
> ```
> <!--- KNIT example-[md-file-name]-02.kt -->
> ``````


---


### API reference


API reference documentation is generated using [Dokka](https://github.com/Kotlin/dokka), a documentation engine for Kotlin. The API documentation is built with:

```bash
./gradlew dokkaGenerate
```

The generated API documentation is deployed at _TODO(add link)_.
