# SkullShell Design System

Internal reference for the app's visual language, kept short so it stays read. All source lives
under `app/src/main/kotlin/dev/aicli/app/ui/design/` and `.../ui/components/`.

## The premise

SkullShell is monochrome. Not "mostly grey with an accent" — there is no hue anywhere in the app
chrome, and no Material component library on the classpath. Everything a coloured design would
encode as a hue is encoded here as a position on one black-to-white ramp, plus **inversion** for
the loudest states.

This is a design decision with two consequences worth stating up front:

1. **Every distinction has to survive greyscale**, so status is never conveyed by fill alone —
   each `StatusTone` gets a distinct glyph *and* a distinct box treatment.
2. **Inversion is a scarce resource.** An ink block with ground-coloured text is the strongest
   signal available, so it is spent on at most one thing per screen. Four inverted buttons in a
   column is the same as none.

## No Material

`androidx.compose.material3` and `material-icons-extended` are **not dependencies** of either the
`app` or `terminal` module, and `MaterialTheme` appears nowhere. Every widget the screens use is
defined in `ui/design/` on top of Compose Foundation:

| Material component | Replacement | File |
|---|---|---|
| `MaterialTheme` | `SkullTheme` / `TerminalTheme` | `Theme.kt` |
| `Text` | `Text`, `Label` (on `BasicText`) | `Text.kt` |
| `Icon` + icon library | `Glyph` + `Glyphs` (hand-drawn) | `Surfaces.kt`, `Glyphs.kt` |
| `Card` / `Surface` | `Panel` | `Surfaces.kt` |
| `HorizontalDivider` | `Rule` / `VRule` | `Surfaces.kt` |
| `Scaffold` / `TopAppBar` | `Screen` / `TopBar` / `PageTitle` | `Chrome.kt`, `Surfaces.kt` |
| `NavigationBar` / `NavigationRail` | `NavBar` / `NavRail` (+ items) | `Chrome.kt` |
| `Button` / `OutlinedButton` / `TextButton` | `PrimaryButton` / `OutlineButton` / `GhostButton` | `Buttons.kt` |
| `IconButton` | `IconAction` | `Buttons.kt` |
| `Switch` / `RadioButton` / `Slider` / `TextField` | `Toggle` / `RadioMark` / `Slider` / `InputField` | `Controls.kt` |
| M3 `Shapes` | `Shapes` (pill / small / panel / modal / sheet) | `Tokens.kt` |
| `AlertDialog` / `ModalBottomSheet` / `DropdownMenu` | `Modal` / `Sheet` / `Menu` | `Overlay.kt` |
| `CircularProgressIndicator` / `LinearProgressIndicator` | `Spinner` / `LinearProgress` / `LoadingBody` | `Progress.kt` |
| ripple (`LocalIndication`) | `Modifier.pressable` | `Surfaces.kt` |

Two structural consequences of dropping Material: the ripple implementation lives in the Material
artifacts, so every interactive surface passes `indication = null` and draws its own press wash;
and `TerminalKeyboardBar` moved from `terminal/` to `app/ui/terminal/` so it can be themed, with
its byte encodings left behind in `terminal/TerminalKeys.kt`.

## Colour roles

`Tokens.kt`. Four ink levels, four ground levels, deliberately no more — a short ramp is what
stops a greyscale UI from turning to mush, because every step is a visible step.

| Role | Dark | Light | Used for |
|---|---|---|---|
| `bg` | `#000000` | `#FFFFFF` | the page. Pure, no "almost" |
| `panel` | `#0B0B0B` | `#FAFAFA` | a `Panel` on the page, read via its border |
| `panelHi` | `#161616` | `#F0F0F0` | pressed/nested surface |
| `line` | `#232323` | `#E2E2E2` | hairline rules and borders |
| `lineStrong` | `#3D3D3D` | `#BDBDBD` | focus, selection, modal border |
| `ink` | `#FFFFFF` | `#000000` | primary text, glyphs, filled surfaces |
| `inkMuted` | `#9B9B9B` | `#6A6A6A` | descriptions, metadata, unselected nav |
| `inkFaint` | `#5A5A5A` | `#A6A6A6` | disabled, unlit dots, decorative marks |
| `onInk` | `#000000` | `#FFFFFF` | text on an `ink` fill. Always ground, never grey |

There is **no elevation** — no shadows, no tonal lift. A surface is defined by its hairline and
one step of ground colour. There is also no dynamic/wallpaper colour: a palette derived from
someone's wallpaper is the opposite of this design, which is why the "Dynamic color" setting was
removed rather than left inert.

The terminal's own ANSI 16/256-colour palette (`terminal/TerminalColor.kt`) is intentionally
**untouched**. That output is a program's data, not this app's chrome; greyscaling a `git diff`
or a red error line would destroy information the design has no right to touch. Only the canvas
background and default foreground are reconciled with the theme by the caller.

## Typography

`Typography.kt`. Two families, two jobs, never interchangeable:

- **Sans** (`display` 30 / `title` 20 / `heading` 15 / `body` 14 / `bodySm` 12) for anything a
  person wrote — screen titles, descriptions, error prose.
- **Mono** (`label` 11 / `labelSm` 9.5 / `mono` 12.5 / `monoSm` 11) for anything a machine
  produced or that labels a machine — section headings, buttons, nav, versions, paths, ids, logs.

`label` — uppercase, 600 weight, +1.5sp tracking — is the signature of the design and does most
of the work colour would otherwise do. Uppercasing happens in the `Label` composable, so callers
pass ordinary sentence-case strings.

`includeFontPadding` is off globally and line height is trimmed at both ends, because the layout
depends on tight optical alignment (a label centred against a 1px rule).

## Shapes and spacing

Curvature is the counterweight to the hairline grid. The rules that structure a page are dead
straight; everything that *sits on* that grid is rounded, and the contrast between the two is
what stops the layout reading as a spreadsheet. Radius scales with the size of the element — a
chip and a bottom sheet rounded by the same number look wrong together.

| `Shapes.` | Value | Used for |
|---|---|---|
| `pill` | 50% | buttons, chips, toggles, text fields, progress tracks, nav markers |
| `small` | 12dp | menu, key caps |
| `panel` | 18dp | `Panel` — the workhorse |
| `modal` | 26dp | `Modal` |
| `sheet` | 30dp top only | `Sheet`, rounded only where it leaves the screen edge |

Anything with a fixed small height goes fully round rather than picking a number, so its
curvature is defined by its own height and never needs re-tuning. Circles (`CircleShape`) carry
the marks: icon-button targets, the provider tile, the toggle knob, the slider thumb, radio
marks. `Metrics.hairline` is 1dp and stays straight — a rule is a rule.

`Space.x1..x16` is a 4dp scale named by step, not by intent. `Metrics.gutter` (20dp) is the
screen edge inset. Use these instead of raw `.dp` literals.

## Layout conventions

- **A screen opens editorially**: a quiet `TopBar` carrying only a breadcrumb
  (`SKULLSHELL / PROJECTS`), then a large `PageTitle` in the scroll area, then a hairline.
- **`SectionHeader`** is a tracked label followed by a rule that runs to the end of the row. The
  rule is what turns a label into structure.
- **Rows for records, panels for objects.** Lists (projects, settings, diagnostics checks) are
  full-bleed rows separated by a `Rule`. A `Panel` is reserved for something carrying its own
  state and actions — currently only `ProviderPanel`.
- **Wide screens get a centred 720dp measure**, not a grid of tiles. Hairline rows are already
  dense; stretching them to tablet width just makes them hard to track across.

## Component conventions

- **One state-driven `ProviderPanel`** (`ui/components/ProviderPanel.kt`) covers all 8
  `ProviderState` cases via `variant: Compact | Full` — don't add a second provider composable.
  Its action button inverts only for states asking for attention relative to normal operation
  (update waiting, sign-in expired, error); a fresh `Install` is the starting state, not an
  alert, so it gets an outline.
- **`InstallProgressSheet`** is the one install/update/repair/uninstall progress UI, shared by
  Providers and Settings' runtime repair. Other progress types (e.g. `BootstrapState`) get a pure
  mapper into `InstallEvent` (`ui/install/InstallEventMapper.kt`), not a parallel UI.
- **`EmptyState`/`ErrorState`/`LoadingBody`** back every screen's empty, error and loading
  presentation, and share one silhouette so they are readable at a glance. `ErrorState` takes a
  `glyph` override — `Glyphs.NoSignal` for offline, so it reads distinctly from a generic failure.
- **`ExpandableDetails`** holds anything a human cannot act on (logs, stack traces), collapsed,
  in mono, scrolling horizontally rather than wrapping.

## Navigation / breakpoints

Adaptive at `600.dp` (`MainActivity.RAIL_BREAKPOINT`): below it a bottom `NavBar`
(Home/Projects/Providers/Settings, hidden on Terminal, Sign-in and Diagnostics); at/above it a
`NavRail` that also includes Terminal, since a rail coexists with content instead of costing the
terminal vertical space. Selection is shown twice — full-strength ink *and* a 2dp slot marker —
because with no accent colour one signal doesn't survive a glance.

`LocalBottomBar` tells `Screen` whether the shell is already drawing a bottom bar. Exactly one of
the two applies the navigation-bar window inset: both gives a dead strip, neither puts content
under the gesture bar.

## Status → tone mapping

Used by `StatusChip` and `ProviderPanel`. The escalation is grey → ink → stronger border →
inversion, and that is the whole severity scale.

| State | Tone | Treatment |
|---|---|---|
| Not installed / Installed (idle) / Exited | `NEUTRAL` | hairline box, muted ink, no glyph |
| Installing / in progress | `INFO` | hairline box, muted ink, info glyph |
| Ready / Running / Signed in | `SUCCESS` | hairline box, full ink, check glyph |
| Update available / Auth required / Stopped by OS | `WARNING` | emphasised hairline, full ink, triangle |
| Error / Incompatible | `ERROR` | **inverted block** — the only chip that inverts |

## Icons

`Glyphs.kt` — the app's own set, drawn on a 24-unit grid at a constant 1.8-unit stroke with
**round caps and round joins**, matching the pills and discs around them. At this weight the join
radius is most of what the eye sees of a corner, so square joins were what made the whole UI read
as hard-edged. Status marks (`CheckCircle`, `ErrorCircle`, `Info`, `Clock`) are discs rather than
squares, and small solid marks (menu dots, slider knobs, the `Grid` mark, the dot on an `i`) are
circles — a square that small is all corner. Everything is tinted at draw time by `Glyph`, so the
baked-in colour is irrelevant. Add new icons here rather than reintroducing an icon dependency.
