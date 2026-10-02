# React Pitfalls

1. ❌ **Missing Dependency Array**: Forgetting the dependency array in `useEffect` causes it to run on *every* render, potentially causing infinite API fetch loops.
2. ❌ **Stale Closures**: Capturing outdated state inside a `useEffect` or `useCallback` because a dependency was omitted from the array. Always use the ESLint exhaustive-deps rule.
3. ❌ **Mutating State Directly**: Doing `state.items.push(newItem)` breaks React's reactivity. Always return a new object/array (`setState({ ...state, items: [...state.items, newItem] })`).
4. ❌ **Massive Contexts**: Putting all global state in a single Context Provider causes every component in the app to re-render when *any* piece of state changes. Split contexts by domain.
5. ❌ **Derived State Anti-pattern**: Storing state that can be calculated from other state (e.g., storing `fullName` when you already have `firstName` and `lastName`). Just calculate it on the fly or use `useMemo`.
6. ❌ **Over-engineering Component APIs**: Creating components with 30 boolean props (`isLarge`, `isPrimary`, `hasShadow`). Prefer polymorphic variants or style objects.
7. ❌ **Memory Leaks in `useEffect`**: Failing to return a cleanup function in `useEffect` when setting up event listeners, intervals, or WebSockets.
8. ❌ **Index as Key**: Using map index as `key` prop when the list can be reordered or filtered. This will cause React to associate the wrong state with the wrong component.
