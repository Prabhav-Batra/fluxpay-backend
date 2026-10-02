# Workflow: New Client (Frontend/Mobile)

## 1. Required Inputs
- Figma / UI Designs.
- Target platforms (Web, iOS, Android).
- Approved API Contracts.

## 2. Required Context
- `standards/frontend.md` or `standards/mobile.md`.
- `config/architecture.yaml` (Backend URLs).

## 3. Required Reviews
- UI/UX Review (`reviews/ui-ux.md`)
- Code Review (`reviews/code.md`)

## 4. Expected Outputs
- New client application scaffolded.
- CI/CD configured for web deployment or App Store/Play Store testing tracks.

## 5. Exit Criteria
- Lighthouse / Performance scores pass thresholds.
- App launches without crashing on target device/browser.

## 6. Execution Steps
1. **Scaffold**: Generate the project using the official template (e.g., `create-next-app` or `flutter create`). Apply the `ProjectOS` client template structure.
2. **Design System Init**: Configure global tokens (colors, typography, spacing) in CSS/Tailwind or Flutter ThemeData to match Figma.
3. **API Integration**: Generate API client SDKs from the backend OpenAPI spec if possible, or build the core networking layer with interceptors.
4. **State Architecture**: Set up global state management (Zustand/Redux/Riverpod) following the separation of concerns standard.
5. **Component Build**: Build UI components bottom-up. Test isolated components in Storybook or Flutter's widget tests.
6. **Integration & Layout**: Assemble pages/screens and connect them to real APIs.
7. **Accessibility & Responsive Check**: Run automated a11y audits and manually verify responsive behavior across target breakpoints.
