# SkullShell Design System

Internal reference for the app's visual language, kept short so it stays read. All source
lives under `app/src/main/kotlin/dev/aicli/app/ui/theme/` and `.../ui/components/`.

## Color roles

A violet + cyan duotone on near-black — distinct from any single provider's own brand color,
since Claude/Codex/OpenCode/Antigravity sit side by side as peer cards.

| Role | Meaning | Source |
|---|---|---|
| `primary` | Brand accent, primary actions | `Theme.kt` (violet `#8B5CF6` dark / `#6D3FD6` light) |
| `tertiary` | Doubles as the "info" role | cyan `#22D3EE` dark / `#0E7C90` light |
| `error` / `errorContainer` | Failures, destructive actions | M3 `ColorScheme` |
| `LocalExtendedColors.success` | Ready/passing/signed-in | `ExtendedColors.kt` — CompositionLocal, not a fixed constant, so it's correct in both themes |
| `LocalExtendedColors.warning` | Update available/auth required | `ExtendedColors.kt` |
| `surface` / `surfaceContainer(High/Highest)` | Background → elevated surface hierarchy | M3 tonal surfaces, not pure black |

Status is never color-only: every `StatusChip`/`ProviderCard` pairs its tone with an icon and text.

The terminal's own ANSI 16/256-color palette (`terminal/.../TerminalColor.kt`) is intentionally
**independent** of this theme — terminal colors must stay faithful to what the CLI actually
sends, not shift with app light/dark mode. Only the canvas's plain background is reconciled with
`MaterialTheme.colorScheme.background` (passed in by the caller — see `TerminalScreen.kt`).

## Typography

Full M3 `Typography` scale in `Type.kt`. Display/headline = page-level headings, title =
card/section headings, body = descriptions, label = metadata/status/buttons. `labelMedium` is
monospace for app-chrome "code-like" strings (versions, paths, provider ids) — unrelated to the
terminal surface, which draws its own text via `android.graphics.Paint`/`Typeface.MONOSPACE`
directly on a `Canvas`, never through Compose `Text`/`MaterialTheme.typography`.

## Shapes

`Shape.kt`: `extraSmall` (4dp, chips/tags) → `small` (8dp, buttons/controls) → `medium` (12dp,
cards/dialogs) → `large` (20dp, bottom sheets) → `extraLarge` (28dp, rare).

## Spacing

`Dimens.kt`: `space4/8/12/16/20/24/32/40`, plus `iconSmall/Medium/Large` and `minTouchTarget`
(48dp). Use these instead of raw `.dp` literals in new screens.

## Component conventions

- **One state-driven `ProviderCard`** (`ui/components/ProviderCard.kt`) covers all 8
  `ProviderState` cases via a `variant: Compact | Full` param — don't add a second provider-card
  composable.
- **`InstallProgressSheet`** is the one install/update/repair/uninstall progress UI, shared by
  Home, Providers, and Settings' runtime repair. Other runtime progress types (e.g.
  `BootstrapState`) get a pure mapper into `InstallEvent` (`ui/install/InstallEventMapper.kt`)
  rather than a parallel progress UI.
- **`EmptyState`/`ErrorState`/`LoadingState`** back every list/screen's empty, error, and loading
  presentation. `ErrorState` takes an optional `icon` override — used for `Offline` states
  (`Icons.Filled.WifiOff`) so offline reads distinctly from a generic failure.
- Prefer stock M3 components (`Button`/`FilledTonalButton`/`Card`/`ListItem`/`ModalBottomSheet`/
  `NavigationBar`/`NavigationRail`/...) over custom reimplementations.

## Navigation / breakpoints

Adaptive at `600.dp` width (`MainActivity.kt`'s `RAIL_BREAKPOINT`, reused as `WIDE_BREAKPOINT` in
`ProjectsScreen.kt`): below it, a bottom `NavigationBar` (Home/Projects/Providers/Settings,
hidden entirely on the Terminal route); at/above it, a `NavigationRail` that also includes
Terminal (a rail coexists with content instead of costing the terminal vertical space). This is
a manual `BoxWithConstraints` check, not `NavigationSuiteScaffold` — that artifact isn't in the
project's Compose BOM yet; adopting it is a deliberate follow-up, not done here.

## Terminal-specific styling

`terminal/` is a separate Gradle module with no dependency on `provider-api` or `app`. Its
composables (`TerminalView`, `TerminalKeyboardBar`, `TerminalToolbar`) take only primitives —
the `app` module resolves provider identity/state and passes plain values down. `TerminalView`'s
`backgroundColor`/`defaultForeground` are hex `Int` params (not `Color`) so the module stays
Compose-Color-free at its rendering core; the app-level caller passes
`MaterialTheme.colorScheme.background.toArgb()`.

## State colors (status → tone mapping)

Used consistently by `ProviderCard`, `StatusChip`, `SessionTabRow`/`SessionSwitcherSheet`:

| State | Tone |
|---|---|
| Ready / Running / Signed in | `SUCCESS` |
| Update available / Auth required / Stopped (killed by OS) | `WARNING` |
| Error / Incompatible | `ERROR` |
| Not installed / Installed (idle) / Exited | `NEUTRAL` |
| Installing / in-progress | `INFO` (or a progress indicator in place of a chip) |
