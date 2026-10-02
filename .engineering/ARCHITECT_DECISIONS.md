# ProjectOS Architectural Decisions (The "Why")

This document records the fundamental design philosophies that shaped ProjectOS v1.0. Future maintainers must read this before proposing architectural changes via RFC.

## 1. Why Configuration over Prompts?
AI models suffer from "prompt drift." If every developer writes their own prompt, execution becomes non-deterministic. ProjectOS relies on rigid YAML and Markdown configurations so the AI behaves predictably, mathematically, and identically for every user.

## 2. Why Context over Conversation History?
Chatbot interfaces force the AI to remember thousands of tokens of useless pleasantries and dead-end code branches. ProjectOS aggressively clears the context window and relies solely on the `Context Loader` to inject mathematically relevant code and rules into the system prompt at runtime.

## 3. Why Plugins instead of Hardcoded Technologies?
The web framework ecosystem changes daily. If ProjectOS hardcoded rules for React, it would be obsolete in a month. By creating an immutable Core OS and delegating all technology-specific rules to the Plugin SDK, the OS is future-proof.

## 4. Why a Staged Runtime Pipeline?
To prevent the "AI rush to code." Human engineers understand the problem, check policies, plan the execution, write the code, and then verify it. The 4-Stage Runtime Pipeline forces the AI to mimic this methodical pacing, drastically reducing hallucinations.

## 5. Why the Universal Engine Contract?
As we added the 16th cognitive engine, the architecture became a black box. The Universal Contract forces every engine to output its `Confidence Score` and `Trace Metadata`, guaranteeing deep observability and making debugging trivial.
