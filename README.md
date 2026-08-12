# Calcula

A keyboard-driven symbolic calculator in the spirit of Emacs Calc. JDK 25 + JavaFX 26, Maven, modular
(JPMS, module `com.calcula`).

The stack is a document, not a display: trail, stack, mode line, echo area, and no buttons anywhere.
Input is keystrokes with prefix dispatch.

## Commands

- Run: `mvn -pl calcula-app javafx:run` (needs a prior `mvn package` so the CAS jars are staged;
  works from the reactor root or from inside `calcula-app/`)
- Test: `mvn verify` — or `mvn test -DexcludedGroups=fx` for the pure suite alone
- Format: `mvn spotless:apply` **before committing** — `spotless:check` runs at `verify`
- Package: `mvn clean -Pdist package` ⇒ `calcula-dist/target/dist/Calcula-<version>.dmg`
  (`.deb` on Linux, `.msi` on Windows). The `clean` is not optional — see below.

## Layout

```
calcula-app/         modular, jlink'd. Owns the CasEngine INTERFACE.
calcula-cas-symja/   plain non-modular jar. Owns the Symja IMPLEMENTATION.
calcula-dist/        delivery only. Nothing is built here; it exists to run last.
```

The split is load-bearing. `calcula-app` does **not** depend on `calcula-cas-symja`; the engine is
discovered at runtime by `CasEngineLoader` through a `URLClassLoader` whose parent is the app's own
loader, and its jars live on the classpath, never the module path.

### Why

`matheclipse-core` resolves to **58 jars, 40 MB**, of which only **9** carry a real `module-info.class`
— 23 have just an `Automatic-Module-Name` and 26 have nothing at all. Putting that on the module path
means hand-writing ~49 moditect descriptors, including:

- the Guava trio (`jsr305` split-packaging `javax.annotation`, plus the empty
  `listenablefuture-9999.0-empty-to-avoid-conflict-with-guava` jar),
- `commons-logging`, whose `requires` jlink silently drops under `--ignore-missing-deps`,
- `pdfbox 2.0.26`, pulled in because choco-solver depends on xchart which depends on a PDF writer.

Classpath classes live in the unnamed module and need no descriptors at all, so none of that applies.

It also keeps the licence boundary clean. Symja's core/parser/external are **LGPL v3**, as is the
transitively-pulled JAS, while Calcula is MIT. Shipping them as replaceable jars in a plain `cas/`
directory satisfies the LGPL's relink requirement in the simplest available form — drop in a different
jar, restart. See `NOTICE`.

### Rules for the seam

- The CAS is a **capability, not a precondition**. `CasEngineLoader.unavailable(...)` returns a
  working null-object so the window opens and stays usable with no engine; the mode line says so.
- Load **off the FX thread**. Symja's static init measures ~650 ms, and the first `Integrate` another
  ~650 ms while the Rubi rule set warms.
- The CAS jar must depend on `calcula-app` at **`provided`** scope. Bundling a second copy of
  `CasEngine` loads it in two loaders, and the cast fails with a `ClassCastException` naming the same
  class on both sides.
- `com.calcula.cas` must be exported **unqualified** — the unnamed module cannot read a qualified
  `exports ... to`, and the failure looks like a missing jar rather than a missing export.

## Design

The visual language is **Rule & Plate** — two themes (`Plate` light, `Slab` dark), a six-colour state
language, and a three-face type system. See [docs/design.md](docs/design.md).

Two things there are load-bearing and easy to undo by accident:

- `Themes.apply` owns the stylesheet order — **Primer → theme tokens → `app.css`**. `app.css` is
  written entirely in `-color-*`/`-calc-*` tokens, so applying it before the sheet that defines them
  leaves every colour unresolved.
- The stack's amber gutter marker uses `Exprs.containsInexact`, **not** `!isExact`. The latter is
  shallow and answers false for every `Call`, so negating it marks every symbolic result the CAS
  returns as approximate.

## Status

Foundation complete and green (296 tests), and it packages into a native app. The layers,
innermost first:

| Package | What it is |
|---|---|
| `expr` | Sealed tree — number, symbol, call — plus exact arithmetic over Int/Rat/Flt |
| `parse` | Lexer, precedence-climbing parser, precedence-aware formatter, name table |
| `machine` | Immutable `CalcState`, the `Op` vocabulary, trail, undo, `Evaluator` |
| `input` | `Reader` — RPN and algebraic, over the same Ops |
| `command` / `key` | Registry and prefix-chord dispatcher, both toolkit-free |
| `cas` + `calcula-cas-symja` | The engine seam and its Symja implementation |
| `ui` | Window, chord translation, the Rule & Plate themes |
| `ui.math` | Expr → JavaFX nodes: real two-dimensional typeset mathematics |
| `export` | TeX and MathML writers over the same tree |
| `plot` | Expr → double closure, sampler with pole breaks, viewport, ticks |
| `ui.plot` | The canvas, with drag and scroll |
| `calcula-dist` | Delivery only: jlink, jpackage, the AOT cache, and staging the CAS |

Everything below `ui` is toolkit-free and unit-tested.

Verified end to end against the real engine:

```
1/2 + 1/3                    -> 5/6
integrate(x*sin(x), x)       -> -x*cos(x) + sin(x)
deriv($, x)                  -> x*sin(x)          # $ takes the integral off the stack
solve(x^2 = 4, x)            -> [[x -> -2], [x -> 2]]
x^2 x deriv 3 *              -> 6*x               # the same thing in RPN
```

### Rendering

Stack entries are set as mathematics, not printed as text: built-up fractions, radicals,
raised scripts, brackets that grow with their content. `mvn test -Dtest=RenderSampleFxTest`
writes `calcula-app/target/math-sample.png` to look at.

Every node carries its own subexpression, so `MathLayout.exprAt(node)` resolves a click to
the exact subterm under it — which is what selection mode will be built on, and the one
thing a rasterised formula could never support.

### Plotting

`M-p` draws the top of the stack. A plot is an ordinary stack value (`$Plot`), so it sits
above the formula it came from, participates in the trail, and drops with the same key as
a number — rather than opening a window and vanishing, as Calc's gnuplot output does.

Curves are compiled to a `double` closure, not evaluated through the CAS: engine eval
measured 0.38 ms/point, about 450 ms for one 1200-pixel frame, so dragging would be
impossible. Poles break the polyline instead of being drawn through — `tan(x)` renders as
separate branches. `mvn test -Dtest=PlotSampleFxTest` writes
`calcula-app/target/plot-sample.png`.

Two things follow from having a CAS in the same process, and neither is available to a
purely numerical grapher:

- **Poles come from the algebra**, not a heuristic. `Solve(Denominator(Together(f)) = 0)`
  says where they are, the line breaks exactly there, and they are drawn as dashed rules.
  The jump threshold remains as a fallback for what the algebra cannot see.
- **Turning points are labelled exactly.** `Solve(D(f,x) = 0)` gives the root as an
  expression, so `sin(x)` is marked **(pi/2, 1)** rather than (1.5708, 1.0).

Both are best-effort: an annotation makes a plot better and is never a reason to fail one.

The engine's own pictures render too — `Plot(sin(x), [x, 0, 6])` returns a `Graphics` value
with adaptively-sampled `Line` primitives, which lands on the stack like any other result
and is drawn rather than typeset. That is the static-and-exact half; compiled curves are
the interactive half, and neither replaces the other.

Still to do: shaded area labelled with the closed-form integral, and a tangent line at the
cursor.

### Packaging

```
mvn clean -Pdist package
```

94 MB DMG, 40 MB of which is the CAS. `clean` is not optional: an incremental compile does
not regenerate a synthetic `$SwitchMap` class once it is missing, so jlink can ship an image
that throws `ClassNotFoundException` on first use — in the packaged build only.

The architecture pays off here. The modular half is JavaFX, AtlantaFX and our own jar, all
three of which carry a real `module-info`, so **there is no moditect step at all** — the 49
hand-written descriptors that made the classpath decision worth taking never have to exist.
The CAS is copied in as a plain `cas/` directory beside the launcher, which is also what
makes the LGPL relink obligation a non-event.

`calcula-dist` exists because a Maven reactor finishes every phase of one module before
starting the next, and the CAS jars are staged during `calcula-cas-symja:package`. Packaging
from `calcula-app` would therefore run before they existed and ship an app that opens fine
and says "CAS: unavailable" — a build-order fault wearing the costume of a code bug.

Two phases, because the AOT cache has to be trained against the image's own runtime, which
only exists after jlink and must be inside the installer. Training launches the real
application with a real window — the win is JavaFX's scene/control/CSS class loading, none
of which happens headless — and it exits itself after settling. Measured on this machine,
interleaved A/B: **810 ms cached against 1174 ms uncached, a 31% cold start**, for 62 MB.

Verified by running the built app rather than by reading the build log: it loads
`symja 3.0.0` from inside the bundle, maps its cache, reports version 0.1.0 rather than the
jpackage placeholder, and passes `codesign -v` in both the DMG and the app-image delivery.

### Copying

`C-c` (`Cmd-c` on macOS) puts the top of the stack on the clipboard in **every** format at
once — MathML, MathML wrapped in HTML, LaTeX as the plain text, and a picture. A clipboard
is already a multi-format container and the consumer knows which form it can use, so
nobody is asked which format they meant: Word makes a real editable equation, Overleaf
takes the TeX, chat takes the image.

Both writers emit from the tree rather than through the engine. `TeXForm` produces
`\frac{1}{2} \cdot x \cdot \sqrt{\left( 1 - {x}^{2}\right)}` where this produces
`\frac{x \sqrt{1 - x^{2}}}{2}`, and `MathMLForm` renders a matrix as nested sets rather
than an `<mtable>` and prepends a DOCTYPE that has to be stripped. Writing them here also
means copying works with no engine loaded.

### Operating on part of an answer

Click any part of a rendered formula and it highlights; hovering shows what a click would take.
`M-Up`/`M-Down` widen and narrow the selection through the expression tree and `M-Left`/`M-Right`
step along a parent's arguments, so the second argument of a function is reachable without going up
and back down. Every transform is a command — bindable and palette-searchable — and the right-click
**Rewrite** menu runs those same commands, because a right-click selects first:

```
sqrt(1 - x^2) + arcsin(x)     select 1 - x^2, Rewrite > Factor
sqrt((1 - x)*(1 + x)) + arcsin(x)
```

There is no way to express that by retyping, and it is the one thing a CAS shell structurally cannot
offer. Extract, Copy and Plot work on the selected part too.

The subtlety is that **a rendering does not mirror its tree**. Canonical forms are reassembled for
display — a fraction synthesised from `Times`, a radical from `Power(x, 1/2)`, a minus lifted out of
a coefficient — so a node can show a subterm that is at no address at all. In `a - b` the second
term is drawn as `b` after a lifted minus, while argument 1 holds `Times(-1, b)`; claiming an
address there would rewrite the sign away.

So a node carries an address only when the thing displayed **is** the thing addressed, and
`selectionAt` walks past synthesised nodes to the nearest genuine ancestor. Expr and path come from
the same node, so they agree by construction. `SelectionAddressFxTest` renders fifteen formulas
covering every reassembly case and walks every node of each, because a mismatch would not throw — it
would quietly rewrite the wrong part of someone's answer.

`Times` is the exception worth making: a plain product of ordinary factors reassembles to exactly
its own arguments, and `x*sin(x)` inside a function is the commonest shape there is, so that case is
detected and stays addressable.

### Four ways in, one set of commands

A menu bar, a command palette (`M-x`), settings, and right-click menus — all of them **views of
`CommandRegistry`**, never parallel implementations. A hand-written menu is a second list of every
action and it drifts from the first one silently: still offering something that was renamed, never
offering something that was added. Here a command reaches the menu by being registered, and by
nothing else; `CommandGroups` files it from its id prefix.

Every surface shows the **chord**, and no menu item installs a JavaFX accelerator. `KeyDispatcher`
stays the only thing that dispatches a key — a second path could disagree with the keymap, and most
bindings here are multi-key sequences (`C-x u`, `M-m d`) that a `KeyCombination` cannot express
anyway. So the menu teaches the keyboard rather than competing with it. Where a command has several
bindings, `Keymap.invert` advertises the **fewest chords**: undo is bound to both `C-z` and `C-x u`,
and showing the two-key one would be true and useless.

The palette is the complete index; the menu is a curated view. `input.submit` is the Enter key, and
"Enter" as something to pick with the mouse is noise.

Right-click menus offer only what applies to the row clicked. Drop and Evaluate act on the *top* of
the stack, so they appear only when the clicked row is the top — there is no operation for "delete
entry 4", and a menu item that quietly acted on entry 1 instead would be worse than its absence.

Settings live in `settings.properties` under the config dir — `java.util.Properties`, because these
are eight scalars and the alternative is a dependency on the modular half, whose freedom from
non-modular jars is why there is no moditect step at all. A file from a newer schema is set aside
rather than reinterpreted. **This is also where the input-model question is finally answered** — as a
preference rather than a hardcoded default, which is the honest resolution: both readers are equally
supported and which one someone wants is not something the program can know.

`mvn test -Dtest=SurfaceSampleFxTest` writes `palette-sample.png` and `settings-sample.png` to look
at. Worth doing: rendering these caught right-aligned section headings and a list clipped mid-row
while 340 structural tests stayed green.

### Fonts

Inter and JetBrains Mono are **bundled** (2.7 MB, both OFL-1.1, see `NOTICE`). Naming a font you
have not shipped is a preference rather than a decision: it looks right on the machine it was chosen
on and silently falls through to something else everywhere else.

They are registered by `ui.Fonts` before any stylesheet is applied — a family that is not registered
when the sheet naming it is parsed does not resolve, and JavaFX substitutes without complaining —
and named by `ui-font.css`, a **scene** sheet rather than an `app.css` rule, because every dialog
and popup has its own Scene and because a scene sheet survives the `setUserAgentStylesheet` a theme
switch performs.

**`-fx-font-family` cannot name a CSS variable.** JavaFX's looked-up values are colours only, so
`-fx-font-family: -calc-mono-face` parses, resolves to nothing, and lays out in the system face.
This file declared three such faces and referenced them in six rules, none of which had ever taken
effect. Faces are named literally at each use, and a test asserts what a styled node actually lays
out in — the only thing worth asserting, since the failure is invisible.

Typeset formulas are not affected either way: `MathLayout` picks fonts in code from `MathStyle`,
using the logical `Serif` family, which resolves everywhere without shipping a maths face.

### Finding out what exists

`C-h f` opens the function reference: every callable thing, grouped, filterable by **what it does**
as well as by name — "differentiate" finds `deriv`, "prime" finds `PrimeQ`. Clicking a row puts the
signature on the input line, because a reference you retype from is a reference you read once.

Completion answers a different question. It needs a prefix, so it can tell you how a name is spelled
and never that the name exists; the sheet is the same catalogue with that requirement removed.

Names in Capitals are the engine's own and work because an unrecognised head is passed through
untouched — the same totality that makes a wrong guess fail silently, which is what both surfaces
exist to fix.

### Modes

The mode line is not decoration — each entry is tested for the thing it claims to do,
because a mode that displays and changes nothing is a wrong answer the user has been told
to expect. `M-m` is the prefix: `r`/`d`/`g` for the angle unit, `p` for precision, `s` for
symbolic, `f` for fractions. Each is an operation on the machine, so a mode change lands in
the undo history beside the answers it changed.

Degree mode is a rewrite of the expression rather than a flag passed to the engine — every
CAS works in radians. `sin(x)` becomes `sin(x · π/180)`, and `arcsin(x)`, which returns an
angle rather than taking one, becomes `arcsin(x) / (π/180)`. The factor is carried as an
exact quotient and never as 0.017453…, which is the whole point: **`sin(30)` in degrees
answers `1/2`**, where multiplying by a rounded constant gives 0.49999999999999994 — and
would still look right to three decimal places on screen. Hyperbolic functions are
deliberately excluded; `sinh(2)` takes a real number, not an angle.

Precision is read from the input line: type `20`, then `M-m p`. The input line is this
application's minibuffer, so that is the gesture Calc uses for a numeric prefix, and it
needs no dialog.

### Still open

**Which input model is the default.** Deliberately undecided. Both readers work,
`input.toggleModel` (`M-i`) switches at runtime, and a test pins that `5 3 -` and
`5 - 3` reach identical states. Algebraic is the provisional start.

### Not built yet

- Release CI: a matrix building one installer per target. The build itself is done and
  runs on any of them; nothing automates it yet, and only macOS has been built for real.
- Notarization, so a downloaded DMG opens without a Gatekeeper warning. The app is
  ad-hoc-signed, which is enough for a locally built copy and not for a download.
- Smaller things deferred along the way: a shaded plot area labelled with the closed-form
  integral, a tangent line at the cursor, and — still open — which input model is the
  default.

## Notes

- `-Dcalcula.cas.dir` names the CAS directory; the dev run points it at `calcula-app/target/cas`, and
  the packaged app will point it at `$APPDIR/cas`.
- `-Dcalcula.config.dir` overrides `~/.calcula`, which holds `calcula-session.log` — a delivered app has
  no stderr anyone will read.
- `java.awt.headless=true` is the first statement of `main` and must stay there: anything that later
  touches Java2D (plot rasterisation, JLaTeXMath) otherwise makes the macOS AWT pipeline contend with
  JavaFX's Glass for the AppKit run loop, and the app intermittently deadlocks.
