# Review Checklist: UI/UX & Accessibility

> **Reviewer Instructions**: Validate frontend changes against design systems, usability heuristics, and WCAG standards.

## 🔴 Blockers (Must Fix)
- [ ] **Responsive Design**: Does the UI break on mobile (320px), tablet, or standard desktop resolutions?
- [ ] **Keyboard Navigation**: Can all interactive elements (buttons, forms, modals) be reached and activated using only the `Tab` and `Enter` keys?
- [ ] **Contrast Ratios**: Does text contrast against its background meet WCAG AA (4.5:1) standards?
- [ ] **State Feedback**: Are loading states (spinners/skeletons) and error states clearly visible to the user during async operations?

## 🟡 Warnings (Should Fix)
- [ ] **Design System Fidelity**: Does the implementation use the standard design tokens (colors, spacing, typography) rather than hardcoded CSS values?
- [ ] **Semantic HTML**: Are `button`, `nav`, `header` tags used correctly instead of clickable `div`s?
- [ ] **Focus Management**: When a modal opens, is focus trapped inside? When it closes, is focus returned to the trigger element?
- [ ] **Touch Targets**: Are all clickable areas on mobile at least 48x48 pixels?

## 🟢 Advisories (Nice to Have)
- [ ] **Micro-Interactions**: Do buttons have subtle hover, active, and disabled visual states?
- [ ] **Screen Reader Labels**: Do icon-only buttons have appropriate `aria-label` attributes?
