# React Best Practices

1. **Functional Components Only**: Use functional components with Hooks. Do not use class components.
2. **State Segregation**: Strictly separate UI state (Zustand/Context) from Server state (React Query / SWR / Apollo). Do not fetch data in `useEffect` and store it in Redux.
3. **Custom Hooks**: Extract complex logic out of components into testable custom hooks (`useAuth`, `useBilling`). Components should primarily be for rendering.
4. **Memoization**: Use `useMemo` for expensive calculations and `useCallback` for functions passed as props to memoized children to prevent unnecessary re-renders.
5. **Prop Drilling**: Avoid passing props down more than 2 levels. Use Context API or Zustand for global state.
6. **Key Prop Integrity**: Never use array index as a `key` prop in a list of components, as it breaks reconciliation and state during re-ordering.
7. **Absolute Imports**: Configure TS/Webpack to use absolute imports (`import { Button } from '@/components/Button'`) rather than deep relative paths (`../../components`).
8. **Error Boundaries**: Wrap major route components in React Error Boundaries to prevent a single component crash from bringing down the entire application.
9. **Component Composition**: Prefer passing React Nodes as children (`<Layout><Sidebar /></Layout>`) rather than passing massive configuration objects as props.
10. **Strict Mode**: Always develop with `<React.StrictMode>` enabled to catch lifecycle bugs and legacy API usage.
