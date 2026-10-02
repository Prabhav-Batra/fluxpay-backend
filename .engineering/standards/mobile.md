# Global Standard: Mobile Development

## Architecture
1. **State Management**: Use a predictable state management solution (e.g., BLoC or Riverpod for Flutter, Redux for React Native, MVVM for native). Never mix business logic with UI rendering.
2. **Offline-First by Default**: Assume the network is hostile and unreliable. Cache critical data locally. Use local databases (SQLite, Hive) and sync when connectivity returns.
3. **Repository Pattern**: Abstract all data sources (network, local cache) behind repository interfaces so the UI doesn't know where data originates.

## Performance
4. **UI Thread**: Never block the main UI thread with heavy computations, parsing, or network requests. Move these to background isolates/threads.
5. **List rendering**: Always use recycling/lazy-loading list views (e.g., `ListView.builder` in Flutter, `RecyclerView` in Android). Never render full lists of off-screen items.
6. **Image optimization**: Cache network images locally to prevent re-downloading. Use appropriate image resolutions; do not download 4K images to render in a 100x100 thumbnail.
7. **App size**: Monitor APK/IPA sizes. Use app bundles, shrink resources, strip debug symbols, and optimize assets.

## UI/UX
8. **Platform conventions**: Respect platform-specific paradigms (e.g., back button behavior on Android, swipe-to-go-back on iOS). Use native-feeling animations.
9. **Responsive layouts**: Support variable screen sizes (phones, tablets, foldables). Do not hardcode fixed widths or heights; use constraints and flex layouts.
10. **Dark mode**: Support system-level dark mode by default. Use semantic color tokens, not hardcoded hex values.
11. **Accessibility**: All interactive elements must have semantic labels for screen readers. Support dynamic text sizing. Ensure touch targets are at least 48x48 dp.

## Networking
12. **Connection timeouts**: Enforce strict connection and read timeouts for all API calls. Mobile connections drop frequently.
13. **Retry logic**: Automatically retry idempotent network requests on transient failures (e.g., timeouts, 5xx errors) using exponential backoff.
14. **Pagination**: Never load entire collections over the network. Always implement infinite scrolling with pagination.

## Security
15. **Local storage**: Never store sensitive data (tokens, passwords, PII) in plain text `SharedPreferences` or `UserDefaults`. Use the platform's secure keystore/keychain.
16. **Certificate pinning**: For high-security apps (fintech, healthcare), implement SSL certificate pinning to prevent Man-in-the-Middle attacks.
17. **Jailbreak/Root detection**: High-security apps should detect compromised OS environments and refuse to run or degrade gracefully.

## Lifecycle & Memory
18. **App lifecycle**: Handle backgrounding and foregrounding gracefully. Save UI state when the app goes to the background and restore it when returning.
19. **Memory leaks**: Clean up controllers, listeners, streams, and animations when the associated UI component is destroyed.

## Distribution
20. **Automated releases**: Use Fastlane or similar CI/CD tools to automate building, code signing, and publishing to App Store and Google Play.
