# ProjectOS Initialization Guide

Congratulations on installing ProjectOS. This directory acts as the engineering brain for this repository. To get the most out of it, you must configure it for your specific tech stack.

## Quick Start (5 Minutes)

### 1. Configure Your Stack
Open `.engineering/config/stack.yaml` and `.engineering/config/architecture.yaml`.
Replace the `<template>` placeholders with your actual technologies.

**Example for a Flutter App:**
```yaml
# stack.yaml
languages:
  - name: "dart"
frameworks:
  - name: "flutter"
```

**Example for a Next.js App:**
```yaml
# stack.yaml
languages:
  - name: "typescript"
frameworks:
  - name: "react"
  - name: "nextjs"
```

### 2. Verify AI Bootloader
ProjectOS uses an automatic bootloader. If your AI IDE supports `.agents/AGENTS.md` (like this one), the AI will automatically execute the boot sequence every time you start a new conversation.

To verify it is working, start a new chat and type:
> "Have you executed the ProjectOS boot sequence?"

The AI should respond confirming that it has read the architecture and loaded the constraints.

### 3. Give Your First Command
Instead of "Build a login page", try using the ProjectOS workflows:
> "Execute the `new-feature.md` workflow to build a login page. Use the `flutter-feature-module` template."

## Adding New Tech Packs
ProjectOS comes with several standard technology plugins. If you added `python` to your `stack.yaml`, the AI will automatically load the rules from `.engineering/plugins/Python.md`.

If you are using a technology that isn't supported yet, read `.engineering/SDK/extension-guide.md` to learn how to add custom rules that the AI will follow.
