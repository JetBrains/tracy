# Documentation

The setup and use of this module is the same as for [Koog](https://github.com/JetBrains/koog). Refer to this [README](https://github.com/JetBrains/koog/tree/main/docs) for in-depth directions.

## Module Structure

The docs module is organized as follows:

| Folder         | Description                                                                          |
|----------------|--------------------------------------------------------------------------------------|
| **docs/**      | Contains Markdown files with user documentation.                                     |
| **overrides/** | Contains custom overrides for the MkDocs theme.                                      |
| **prompt/**    | Prompting guidelines with extensions for popular modules.                            |
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


### Add Code Snippets

There are two options of adding new code snippets into your documentation. The syntax remains the same; the only difference is how they are included in the documentation md-files.

#### Syntax



_Note: If your code snippets contain some components that require a dependency on a specific module, you should add this module into [build.gradle.kts](./build.gradle.kts) as an entry in the `dependencies {}` Gradle block._



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


### Generate API reference:

Run the following command and navigate to the output directory in your browser:

```bash
./gradlew dokkaGenerate
```



## Local Development

### Add New Documentation Pages

