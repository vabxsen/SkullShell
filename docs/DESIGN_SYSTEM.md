# SkullShell Material UI

SkullShell uses the official Jetpack Compose Material 3 library. The interface follows Android's
Material You conventions: tonal surfaces, rounded cards, Roboto typography, ripple feedback,
Material icons and standard controls. See the
[Android Material 3 guide](https://developer.android.com/develop/ui/compose/designsystems/material3).

## Theme

`ui/design/Theme.kt` applies `MaterialTheme` to the app. System, Light and Dark modes honor the
saved appearance preference. Wallpaper colors use Android's dynamic light/dark schemes on all
supported devices (minimum API 31). Disabling wallpaper colors selects the app's blue palette.
`SkullTheme.colors` adapts Material semantic roles for the shared application components.

Use `MaterialTheme.colorScheme` roles and paired `on…` colors. Do not override dynamic colors
with arbitrary accents. Terminal output uses a fixed dark palette so terminal programs remain
legible independently of the surrounding app theme.

`Typography.kt` maps app text roles to Material 3 typography. Monospace is reserved for paths,
terminal output and diagnostic details. Icons are the rounded/outlined Material icons;
screen toolbars provide a consistent Settings entry point.

## Components

The shared wrappers in `ui/design/` delegate to actual Material 3 components:

- `Screen` / `TopBar`: Scaffold and TopAppBar with system insets.
- Buttons: Button, FilledTonalButton, OutlinedButton, TextButton and IconButton.
- Controls: Switch, Slider, RadioButton and OutlinedTextField.
- Overlays: AlertDialog, ModalBottomSheet and DropdownMenu.
- Progress: CircularProgressIndicator and LinearProgressIndicator.
- Surfaces: Material Card, Surface and ListItem with native ripple interactions.

Projects use an ExtendedFloatingActionButton, a search field and real location FilterChips.
Settings use SingleChoiceSegmentedButtonRow for theme selection. Terminal sessions use a
Material scrollable tab row; the shortcut bar retains domain-specific key caps.

## Screens

Home contains a primary tonal terminal card, project and agent entry points, real recent
projects and actual sessions. The terminal setup action appears when the runtime is absent.
Session rows resume their existing session; New terminal creates one.

Projects supports search, All/On device/External filters, workspace creation, external-folder
registration and removal confirmation. Removing a project preserves its files.

Agents shows real install/authentication/version states in rounded cards with contextual actions.
Settings is the navigation hub: its Workspace section links to Home, Projects, Terminal, Agents and Diagnostics.
Appearance, terminal, environment, advanced and update controls follow in tonal groups.
Diagnostics reports actual health-check results with a summary card and detailed check list.
Authentication keeps the provider's real CLI login terminal visible.

## Adaptive layout and accessibility

Screens use the full window, with no persistent bottom navigation or tablet rail. Home and Projects content is
bounded to 840dp; Settings and Diagnostics to 760dp. Agents uses two columns when its available
content width reaches 680dp. Scrollable content and dialogs accommodate larger text and the
keyboard. Project list padding reserves room for the floating action button.
Back returns to the previous screen. Opening Settings reuses its existing entry to avoid navigation loops.

Material components provide their standard selection semantics, touch targets and interaction
states. Icon-only actions have content descriptions. Loading, empty, error and unavailable
states remain tied to real application data. No decorative metrics or simulated session output
are used.
