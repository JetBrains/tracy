# Langfuse Integration Setup Guide

This guide explains how to configure and use `ai-dev-kit` with the internally hosted **Langfuse** instance for tracing and evaluation.

---

## 🔐 VPN Access

Before accessing the Langfuse instance, ensure you are connected to the internal VPN.

If you do not yet have VPN access, follow the official guide here:  
👉 [JetBrains VPN Setup Guide](https://youtrack.jetbrains.com/articles/ITKB-A-5/VPN)

---

## 🌐 Log in to Langfuse

Once connected to the VPN, open the internal Langfuse instance:  
👉 [https://langfuse.labs.jb.gg/](https://langfuse.labs.jb.gg/)

Sign up or log in using your credentials.

---

## 🏗️ Create a Project

After logging in:

1. Create a new **organization** (or select an existing one).
2. Within that organization, create a new **project**.

---

## 🔑 Generate API Keys

1. Navigate to your project.
2. Go to **Project Settings** → **API Keys**.

Or open it directly by replacing `{your_project_id}` in the URL:  
👉 https://langfuse.labs.jb.gg/project/{your_project_id}/settings/api-keys

3. Click **"New Key"** to generate a pair of API keys.

> ⚠️ **Important:** The **secret key** is visible **only once** — make sure to store it securely!

---

## ⚙️ Configure `ai-dev-kit`

You can configure Langfuse integration either through environment variables or directly in code.

### Option 1: Environment Variables

Set the following environment variables in your shell, `.env` file, or CI:

```env
LANGFUSE_PUBLIC_KEY=your_public_key
LANGFUSE_SECRET_KEY=your_secret_key
```

### Option 2: Programmatic Setup

a. Setup Langfuse Tracing Manually

```kotlin
TracingManager.setup(
    LangfuseConfig(
        langfuseUrl = "https://langfuse.labs.jb.gg/", 
        langfusePublicKey = "...", 
        langfuseSecretKey = "...")
    )
)
```

b. Use in Evaluation Tests

```kotlin
class MyTest : LangfuseEvaluationTest<..., ..., ..., ...>(
    numberOfRuns = ...,
    langfuseConfig = LangfuseConfig(...)
) {
    // Your test logic here
}
```

---

## 🧪 Verifying the Setup

Run the app or tests that include tracing or evaluation logic.
Go to your project in Langfuse.

1. Navigate to your project.
2. Go to **Home**.


👉 https://langfuse.labs.jb.gg/project/{your_project_id}

You should see new traces appear in the **Tracing/Traces** tab:

👉 https://langfuse.labs.jb.gg/project/{your_project_id}/traces,

runs in **Tracing/Sessions** tab:

👉 https://langfuse.labs.jb.gg/project/{your_project_id}/sessions,

and metrics in **Tracing/Scores** tab:

👉 https://langfuse.labs.jb.gg/project/{your_project_id}/scores,

---
For additional info refer to [Langfuse docs](https://langfuse.com/docs).