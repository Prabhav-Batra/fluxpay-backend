# Extension Guide: Building Custom Plugins

## Why Build an Extension?
You should build an extension for ProjectOS if:
1. You are introducing a new technology stack (e.g., Rust, Svelte).
2. Your organization has highly specific compliance workflows (e.g., SOC2 audit requirements).
3. You want the AI to adhere to custom, proprietary architectural patterns.

## Step-by-Step Guide

### 1. Create the Plugin Definition
Create a new file in `.engineering/plugins/[YourPluginName].md`.
Use the `technology-plugin-template.md` as a starting point.
Define your core rules and anti-patterns. These are injected directly into the AI's cognitive prompt.

### 2. Create the Tech Pack (Optional)
If your plugin is for a technology (e.g., a database or framework), create a folder in `.engineering/registry/packs/[your-tech]/`.
Add a `manifest.yaml`, `best-practices.md`, and `pitfalls.md`.
Link this pack in your plugin's `Dependencies` section.

### 3. Register Custom Workflows (Optional)
If your extension requires a new operational flow (e.g., `ml-model-training`), create a file in `.engineering/workflows/custom/`.
Use the `workflow-plugin-template.md` as a starting point.
Define the inputs, context, and explicit execution steps.

### 4. Test the Extension
Run `projectos validate` (or trigger the verification engine) to ensure your plugin's markdown is properly formatted and all cross-references are valid.
Ask the AI a test prompt: "According to the [YourPluginName] standards, how should I approach X?" to verify the Context Loader is picking it up.
