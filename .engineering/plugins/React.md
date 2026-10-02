# React Plugin

## Registration
- ID: `projectos.plugins.react`
- Version: `1.0.0`
- Compatibility: `React 18+`, `Next.js 14+`

## Capabilities
- **Standards**: Injects React-specific rendering, hook, and composition rules.
- **Workflows**: Hooks into `npm run build` (Vite, Next.js, Create React App).
- **Knowledge Packs**: Injects the `react` tech pack from the registry.

## Core Rules
1. **Hooks**: Strictly adhere to the Rules of Hooks. Hooks must be called at the top level, unconditionally.
2. **Server Components (Next.js)**: Default to React Server Components (RSC). Only use `'use client'` when state (`useState`), effects (`useEffect`), or browser APIs are required.
3. **Data Fetching**: Use dedicated libraries like React Query, SWR, or Next.js `fetch` extensions. Never fetch data directly inside a `useEffect` without handling race conditions and cleanup.
4. **State Colocation**: Keep state as close to where it's used as possible. Don't throw everything into Redux/Zustand if it's only needed by a single sub-tree.

## Common Anti-Patterns
- ❌ Missing or incorrect dependency arrays in `useEffect`, `useMemo`, or `useCallback`.
- ❌ Using `index` as a `key` prop when rendering a dynamic list that can be reordered or filtered.
- ❌ Mutating state directly instead of using the setter function.

## Dependencies
- Requires: Node.js >= 18.
- Links to Pack: `registry/packs/react/manifest.yaml`
