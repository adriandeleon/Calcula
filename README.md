# Calcula

A keyboard-driven symbolic calculator in the spirit of Emacs Calc. JDK 25 + JavaFX 26, Maven, modular
(JPMS, module `com.calcula`).

The stack is a document, not a display: trail, stack, echo area, mode line — plus a strip above the
input that sets the line as mathematics while it is being typed. The status strip sits at the bottom
edge rather than above the input, so the top of the stack and the line being typed into it are
adjacent. Input is keystrokes with
prefix dispatch; the only buttons are four labelled toolbar entries, and each names its own chord in
its tooltip.

## Commands

- Run: `mvn package -DskipTests && mvn -pl calcula-app javafx:run`
  — **`javafx:run` does not compile.** It is a plugin goal, not a lifecycle phase, so running it
  alone launches whatever classes were last built; a change you have just made will not be in it.
  The `package` is also what stages the CAS jars into `calcula-app/target/cas`.
- Test: `mvn verify` — or `mvn test -DexcludedGroups=fx` for the pure suite alone
  — **an FX test that measures anything must use `FxTestSupport.realizeThemed`, not `realize`.**
  The bare one gives a Scene with no stylesheets, so the window under test has AtlantaFX Primer and
  neither `app.css` nor the theme tokens. Structure is fine; sizes, spacing, fonts and every
  `-color-*` are not, and a rule `app.css` exists to *override* is simply absent — so the test
  asserts the defect and passes. That is not hypothetical: it is how a seven-row matrix came to
  overflow its row by 41px with 550 tests green.
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

Foundation complete and green (550 tests), and it packages into a native app. The layers,
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
| `export` | TeX, MathML and Typst writers over the same tree |
| `doc` | The `.calc` file: a sheet, and the plain-text format it saves as |
| `help` | The worked examples, each of which lands on the input line when picked |
| `pdf` | A hand-rolled one-page PDF, so the sheet exports without a dependency |
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

### Saying what is there

A toolbar under the menu bar: **Commands**, **Functions**, **Settings**, **About** — each an outline
glyph beside its label, and each naming its **chord** in its tooltip. That is what makes them
allowable in a keyboard-driven application: they say a thing EXISTS, and tell you the key that
reaches it faster. Everything a calculator actually does stays on the keyboard, the palette and the
menu.

Labelled rather than icon-only, because an icon alone is a guess — and these are exactly the surfaces
someone reaches for when they do not yet know what the application can do, which is the worst moment
to make them hover four unfamiliar glyphs to find out. On macOS the menu bar is the system menu bar,
so the toolbar is the top of the window there.

`ui.Icons` follows the same rules as Dicta's set: a 16-unit grid, **stroked rather than filled** so a
glyph tracks the palette and its button's state through `-fx-stroke` with no per-icon colour in Java,
and path data written longhand. JavaFX's `SVGPath` parser is stricter than a browser's and **fails
silently** — a compacted elliptical arc with its flags run together renders nothing at all — so every
arc spaces its flags and a test asserts each glyph parses to a non-empty shape that fits the grid,
because nothing else would say so.

The mode line's own segments were reporting state nobody had been told was meaningful, so the modes
and the CAS status now carry tooltips explaining what they are. The CAS one used to appear only when
the engine had FAILED, which left a working engine as an unexplained label.

### Fonts

Inter, JetBrains Mono and **STIX Two Text** are bundled (4.1 MB, all OFL-1.1, see `NOTICE`). Naming a font you
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

Formulas are set in **STIX Two Text**, the face used for mathematics in scientific publishing.
`MathLayout` registers the fonts itself rather than relying on a theme having been applied, because a
formula is also rendered into the offscreen scene the clipboard picture uses. It was the logical
`Serif`, which is portable in the sense that it always resolves and not in the sense that it resolves
to the same thing — macOS ships STIX, which is exactly why the gap was invisible here.

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
symbolic, `f` for fractions, and `n`/`x`/`e` for how an inexact number is written — every
digit, a fixed number of places, or scientific. Each is an operation on the machine, so a
mode change lands in the undo history beside the answers it changed.

The display format is **display**, and the distinction is load-bearing: the value keeps
every digit it had, so `fix 2` shortens what reaches the screen and changes nothing that is
saved. Rounding the value instead would be silent data loss the moment a sheet was written,
because a stack is saved by formatting it — which is why `Formatter` never consults it and
why the digit grouping added just before it lives in the layout for the same reason. The
count comes off the input line, like precision: type 4, then `M-m x`. Engineering notation
is on the palette rather than a chord, because there is no free letter that means it.

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

### Variables

`M-s t` binds the top of the stack to the name on the input line and takes it, `M-s s` binds and
leaves it, `M-s r` pushes what a name is bound to, `M-s u` forgets one, and `M-s l` lists the lot —
typeset, at the size the stack is using, because a binding is a value.

**A name resolves at `=` and nowhere else**, which is Calc's division rather than an accident of where
the map was reachable. Binding something to `x` must not change what `deriv(x^2, x)` means, so storing
never rewrites the stack; `C-x e` is where you ask for the substitution, and it is one pass, so a
binding that mentions itself terminates instead of recursing.

### Ranges

`1 .. 2` is a range, and arithmetic carries the bounds: `(1 .. 2) * (3 .. 4)` is `3 .. 8`. Typed with
dots and set as `[1 .. 2]`, the same split as `+/-` against `±`.

**No interval arithmetic is implemented here, deliberately.** A range parses to `Interval({a, b})` —
the *engine's* own shape, not a two-argument call, because `Interval(1, 2)` means two degenerate
intervals to Symja and would have been wrong in a way nothing reported. Building what it already
understands means its arithmetic works untouched, and it knows things local code would not: `sin(1 ..
2)` is `sin(1) .. 1`, because the range contains a half-turn and the sine peaks at one inside it.
That is the opposite trade from measurements, which are folded locally and work with no engine —
there, the arithmetic is a page of algebra; here, it is the engine's whole point.

### Measurements

`2 +/- 0.1` is a measurement and how far out it might be, and arithmetic carries the error through:
adding it to `3 +/- 0.2` gives `5 +/- 0.2236`, because errors combine in **quadrature**. That assumes
the two measurements are independent, which is the assumption every laboratory makes and is worth
stating — it is why `x - x` is not zero-error, and why `x^2` and `x*x` disagree. Squaring is one
measurement raised; multiplying is two things combined. A calculator that made them agree would be
inventing a correlation in one direction or losing one in the other.

**It is not a new kind of number.** The tree has exactly three leaves and everything structured is a
call, so a measurement is `PlusMinus(value, error)` like every other structure here — which meant no
change to the arithmetic, the tower, or the round trip, and the propagation went in beside the money
functions. Typed `+/-`, set as `±`: what a keyboard has and what mathematics looks like are different
things, and the formatter keeps the first while the layout shows the second.

### Words and bases

`M-m w` sets the word size and `M-m b` the base; `M-m d`… no, `mode.decimal` on the palette puts the
base back to ten. Both appear in the mode line only when they are not what everybody assumes, because
a mode line that always states the base is one nobody reads.

The word size is a mode rather than an argument because every bitwise operation is meaningless
without one: `BitNot(12)` is 3 in four bits, 243 in eight and 4294967283 in thirty-two, and there is
no answer that is right independently of the width. Values go in as two's complement and come out
unsigned, which is the half people disagree about, so it is stated rather than assumed.

A number can be *typed* in a base as `16#ff`, and that is entirely separate from being *shown* in one.
Entry produces an ordinary integer, so the formatter — which is how a sheet is saved — never sees the
spelling; display happens in the layout, beside the float format and the digit grouping, for the same
reason. Grouping stays a base-ten convention: threes applied to hexadecimal would make it harder to
read, not easier.

### Rewrite rules

`M-r` applies the rule on the input line — to the **selected part** when there is one, and to the top
value otherwise:

```
sqrt(1 - x^2) + arcsin(x)      select 1 - x^2
1 - x^2 -> (1 - x)*(1 + x)     M-r
sqrt((1 - x)*(1 + x)) + arcsin(x)
```

A pattern is written `x_`, and the notation for it cost nothing: the lexer already reads an underscore
as an identifier character, so `x_` arrives as one name and the parser turns a **trailing** underscore
into `Pattern(x, Blank())` — which is not a translation but the engine's own form, so a pattern
reaches Symja through the same totality that carries any unrecognised head. The adapter needed no
change at all. `my_var` is still a variable; only trailing underscores mean anything.

The rule is read rather than evaluated, and it has to be: `x -> 3` handed to an evaluator is a
question about what `x` is. A line that is not a rule is refused before the engine is asked, because
from there a rule that matched nothing and a thing that was never a rule are the same answer.

Not yet: `:>` and `/;`, so a conditional rule has to be written in function form —
`RuleDelayed(n_, Condition(n^2, n > 2))` — which works, because an unrecognised head passes through.

### Working the stack

`M-RET` puts back what the top value was worked out from, keeping the answer — the thing you actually
want after a mistyped operator, where undo restores the state and this restores the inputs. It needed
no new state: every entry already carries the expression it came from, so the arguments are the origin
call's arguments, which also means it works on a value from any point in the session rather than only
on the most recent command.

`M-e` takes the top value onto the input line to change it. The entry really comes off the stack and
really is on the line — not copied with the original left behind, and not held in a hidden editing
mode the next keystroke has to know about — so submitting goes through the ordinary reader and `$`
references, RPN entry and evaluation all keep working with no second path to drift. Abandoning the
edit leaves the value off the stack; undo brings it back.

`M-t y` puts the selected trail line back on the stack, and `M-t s` searches the trail. Only an input
or a result is offered: a note is prose about the calculator, and a menu item that failed when picked
would be worse than its absence.

### Vectors on the stack

`M-v p` takes the top values and makes one list of them — the count off the input line, two if it is
empty — and `M-v u` puts a list's elements back. Two commands, and they are what the list half of the
engine was waiting for: `Map`, `Fold`, `Apply`, `Union`, `Sort` and every statistic all worked
already, and the only way to hand one a list was to type it out in full, on a stack that was holding
the numbers.

Packing needs no operation of its own. It is `Apply` with the list head, which already pops the right
number of values, refuses politely when the stack is too short, and records the call it built as the
provenance. Unpacking does need one, because nothing else in the vocabulary turns one value into many.

### Sheets, tabs and files

A sheet is a stack, a trail, the variables and the modes it was worked under. It saves as
`.calc` — one keyword per line, in the calculator's own notation:

```
Calcula 2
mode angle radians
mode float fixed 4
var n 42
stack (x + 1)/(x - 1)
trail input 1/3 + 1/6
```

Plain text on purpose. The file is something you can open in an editor, diff, hand-write,
or paste half of into a chat, and it cost no dependency — which matters here because the
modular half of this build has none, and that is why there is no moditect step.

The property the format rests on is that `Formatter` and `Parser` are inverses. A value is
saved by formatting it and loaded by parsing that back, so an expression printed in a form
the parser cannot read would be data loss at *save* time — the file looks healthy and the
value comes back wrong. Twenty shapes are checked over the round trip.

It is strict about its own version and forgiving about nothing else: a line it does not
understand is an error naming the line number, not a line quietly dropped. Half a sheet
loaded without complaint is worse than a refusal, because the missing half is found later,
by which time it has been saved over.

Several sheets can be open at once, each with **its own machine** — so undo, the trail and
the stack belong to the sheet rather than to the window. The tab strip is hidden while
there is only one, because a row of chrome that always reads "Untitled" is chrome for its
own sake. New always opens a tab and so destroys nothing; Open loads into the current
sheet when it is untouched and into a new one otherwise; quitting asks about every sheet
with unsaved work, not just the visible one.

### The window remembers how it was left

The divider was set once at construction and written down nowhere, so dragging it and restarting put
it back at 0.28 — and there was no way to close the trail at all, in an application otherwise shaped
like Emacs, where `C-x 1` is the gesture someone reaches for the moment the mathematics gets tall.

`view.trail` (`C-x 1`) closes and reopens it; the width and the open/closed state are separate keys,
because closing something should not forget the size it had. A closed trail leaves the split
entirely rather than being driven to a zero-width divider — a divider at zero is still there to be
grabbed, and a column of pure border is a worse answer than no column.

The window's own size and position are kept too — but the position is only restored when enough of
the window would land on a screen that currently exists. A position saved on a monitor since
unplugged, or on a laptop since undocked, otherwise opens the calculator somewhere it cannot be
dragged back from, and the failure is total because there is nothing on screen to grab. The screens
are passed to the check rather than read inside it, so a display arranged to the left with negative
coordinates and a display that has gone away are both ordinary test cases.

While maximised the stage reports the screen rather than a choice anyone made, so only the
un-maximised geometry is written down and the flag is kept beside it — otherwise un-maximising
returns to a full-screen "window" forever after.

The migration is that there is nothing to migrate: an added scalar reads as its default, so a file
from an earlier build opens the trail at its usual width and the window centred. A test writes an
older file by hand and says so.

### Where a value came from

A stack entry is a value and the expression it was worked out from. `1/3 + 1/6` leaves `1/2` whose
origin is the sum; `FactorInteger(2^64 - 1)` leaves a list of seven pairs that would otherwise be
indistinguishable from a matrix somebody typed. The row says so in its tooltip and to a screen
reader, and says nothing when the origin is the value itself — which is most rows.

The value and its origin are **one entry in one list**, which is the whole reason this is not a
source of bugs: every machine operation is list surgery, and surgery on pairs carries the origin
without any code that knows about it. It lives in `CalcState`, so undo restores it along with
everything else.

Session-only, deliberately. The `.calc` file saves the mathematics; a value's history is not part of
the mathematics, and adding it would mean a second thing on every `stack` line in a format that rests
on `Formatter` and `Parser` being inverses.

### Reading a result as the thing it is

`FactorInteger(2^64 - 1)` is seven pairs of integers, which was drawn in matrix brackets. It is now
set as `3·5·17·257·641·65537·6700417`.

The origin is what makes this possible rather than a guess: `[[2, 2], [3, 1]]` typed by hand and the
same list returned by `FactorInteger` are the same expression, and the window now draws them
differently because it knows where each came from. A reading is not addressable — the tree being
drawn is not the one on the stack, so nothing inside it can be selected and handed to a transform.

### How big is it

An exact answer is the right answer and not always the useful one. Every stack row whose value has a
decimal shows it in the right margin — `5/6` beside `≈ 0.833333333333` — with `view.approximations`
to turn the column off.

Most rows show nothing. An integer is its own decimal, a value already carrying float error wears the
amber rail instead, and anything with a free symbol has none: `N(x + 1)` is `1 + x`, a round trip
spent to learn nothing. The structural check runs first, so the engine is only ever asked about a
closed form.

A ratio never reaches the engine — `BigDecimal` divides it here. The CAS is a capability rather than
a precondition, so a window with no engine still adds up fractions, and it should still be able to
say how big the answer is.

### Long numbers

A run of five or more digits is grouped in threes with a thin space:
`18 446 744 073 709 551 615`. Not a comma — a comma is a list separator everywhere else here, and
`[1,234, 5]` would be genuinely ambiguous. Four digits is a year and stays one word.

Display only. It happens in the layout and not in `Formatter`, whose job is producing something the
parser can read back, so copying, exporting and saving are untouched.

### Giving up on a computation

`C-g` already meant "abandon what I am in the middle of" for a half-entered chord. It now means the
same for a computation, which was the one thing in the window there was no way out of.

**It gives up rather than stops, and the distinction is not pedantry.** Nothing ends a running Symja
computation from outside — measured on `FactorInteger(2^128 + 1)`, a thread interrupt leaves it
running after 5 s, `EvalEngine.setStopRequested(true)` after 8 s (set on the evaluating thread's own
engine, since `EvalEngine` is thread-local and the caller's is a different object), and so does
`setTimeConstrainedMillis`. Symja polls those in its evaluation loop and not inside a CPU-bound
primitive.

So the engine evaluates on a thread of its own and the caller stops waiting. The abandoned
computation keeps a core busy until it finishes, and the message says so. Its evaluator is replaced
along with it: an evaluation nobody is waiting for still holds Symja state, and sharing that with the
next one is a data race with no upper bound on when it bites.

`CasEngine.cancel()` is a default no-op, so an engine that cannot be interrupted says so by doing
nothing.

### Still open

**Which input model is the default.** Deliberately undecided. Both readers work,
`input.toggleModel` (`M-i`) switches at runtime, and a test pins that `5 3 -` and
`5 - 3` reach identical states. Algebraic is the provisional start.

### Not built yet

- Release CI: a matrix building one installer per target. The build itself is done and
  runs on any of them; nothing automates it yet, and only macOS has been built for real.
- Notarization, so a downloaded DMG opens without a Gatekeeper warning. The app is
  ad-hoc-signed, which is enough for a locally built copy and not for a download.
- Implicit multiplication in algebraic entry: `2 x y` is a parse error where a CAS would
  usually read it as a product. Found by an example that would not run.
- A vector (non-raster) PDF. The page is currently a picture of the sheet, so its
  mathematics cannot be selected or searched; a text PDF means embedding fonts and
  re-implementing the layout in PDF operators, which is a second renderer and therefore
  the one that would drift.
- Smaller things deferred along the way: a shaded plot area labelled with the closed-form
  integral, a tangent line at the cursor, per-sheet input history, and — still open —
  which input model is the default.

## Notes

- `-Dcalcula.cas.dir` names the CAS directory; the dev run points it at `calcula-app/target/cas`, and
  the packaged app will point it at `$APPDIR/cas`.
- `-Dcalcula.config.dir` overrides `~/.calcula`, which holds `calcula-session.log` — a delivered app has
  no stderr anyone will read.
- `java.awt.headless=true` is the first statement of `main` and must stay there: anything that later
  touches Java2D (plot rasterisation, JLaTeXMath) otherwise makes the macOS AWT pipeline contend with
  JavaFX's Glass for the AppKit run loop, and the app intermittently deadlocks.
