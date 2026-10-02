# Global Standard: Frontend

## Component Architecture
1. **Functional components only**: Use functional components with hooks (React) or composition API (Vue). Class components are prohibited in new code.
2. **Single responsibility**: Each component renders one logical UI element. If a component exceeds 250 lines, split it into sub-components.
3. **Container/Presentational split**: Separate data-fetching logic (containers/hooks) from rendering logic (presentational components). Presentational components receive all data via props.
4. **Props interface**: Every component must declare its props interface/type explicitly. No `any` types. No implicit prop spreading (`{...props}`) without a declared contract.

## State Management
5. **Local state first**: Use component-local state for UI-only concerns (modals, form inputs). Elevate to global state only when multiple components need the same data.
6. **Server state separation**: Use dedicated server-state libraries (React Query, SWR, Apollo) for API data. Never put API responses directly into global state stores.
7. **Immutable updates**: All state mutations must produce new references, never mutate existing objects or arrays in place.
8. **No prop drilling beyond 2 levels**: If data must pass through more than 2 intermediate components, use context, state management, or composition patterns.

## Styling
9. **Scoped styles**: Styles must be scoped to their component (CSS Modules, styled-components, or framework scoping). No global CSS except for design tokens and resets.
10. **Design tokens**: Colors, spacing, typography, and breakpoints must come from a central design token file, never hardcoded as magic numbers.
11. **Responsive by default**: Every component must work on mobile (320px), tablet (768px), and desktop (1200px+). Test at all three breakpoints.

## Performance
12. **Lazy loading**: Routes and heavy components must be lazy-loaded. No single bundle should exceed 250KB gzipped for initial load.
13. **Image optimization**: All images must use modern formats (WebP/AVIF), responsive srcsets, and lazy loading. Never serve uncompressed PNGs.
14. **Memoization**: Expensive computations and components that receive stable props should use memoization (useMemo, React.memo). Do not memoize everything — measure first.

## Accessibility
15. **Semantic HTML**: Use correct semantic elements (`button`, `nav`, `main`, `article`). Never use `div` with onClick for interactive elements.
16. **Keyboard navigation**: All interactive elements must be reachable and operable via keyboard. Test Tab, Enter, Escape, and Arrow keys.
17. **ARIA labels**: All non-text interactive elements must have `aria-label` or `aria-labelledby`.
18. **Color contrast**: Text must meet WCAG 2.1 AA contrast ratios (4.5:1 for normal text, 3:1 for large text).

## Testing
19. **Component tests**: Every component must have at least one test verifying its rendered output. Use Testing Library, not implementation-detail queries.
20. **Visual regression**: Use snapshot or screenshot testing for design-critical components.
