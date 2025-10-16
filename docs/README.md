# Documentation

## Local Development

1. Install uv.
2. Sync the project:
```bash
uv sync --frozen --all-extras
```
3. Start the local server with the documetation:
```bash
uv run mkdocs serve
```

### Check the compilation of the code snippets:

Run `:docs:knitAssemble` task to clean old knit-generated files, extract fresh code snippets to `/src/main/kotlin`, and assemble the docs project:
```bash
./gradlew :docs:knitAssemble
```

### Generate API reference:

Run the following command and navigate to the output directory in your browser:

```bash
./gradlew dokkaGenerate
```