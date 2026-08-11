# Calcula

A keyboard-driven symbolic calculator in the spirit of Emacs Calc. JDK 25 + JavaFX 26, Maven, modular
(JPMS, module `com.calcula`).

The stack is a document, not a display: four regions — trail, stack, mode line, echo area — and no
buttons anywhere. Input is keystrokes with prefix dispatch.

## Commands

- Run: `mvn -pl calcula-app javafx:run` (needs a prior `mvn package` so the CAS jars are staged;
  works from the reactor root or from inside `calcula-app/`)
- Test: `mvn verify` — or `mvn test -DexcludedGroups=fx` for the pure suite alone
- Format: `mvn spotless:apply` **before committing** — `spotless:check` runs at `verify`

## Layout

```
calcula-app/         modular, jlink'd. Owns the CasEngine INTERFACE.
calcula-cas-symja/   plain non-modular jar. Owns the Symja IMPLEMENTATION.
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

Foundation complete and green (154 tests). The layers, innermost first:

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

### Not built yet, in intended order

1. Packaging (`-Pdist`): moditect for the app's own few automatic modules, jpackage,
   AOT training, and staging `cas/*.jar` into the app image beside the launcher.
2. Smaller things deferred along the way: a shaded plot area labelled with the
   closed-form integral, a tangent line at the cursor, and — still open — which input
   model is the default.

## Notes

- `-Dcalcula.cas.dir` names the CAS directory; the dev run points it at `calcula-app/target/cas`, and
  the packaged app will point it at `$APPDIR/cas`.
- `-Dcalcula.config.dir` overrides `~/.calcula`, which holds `calcula-session.log` — a delivered app has
  no stderr anyone will read.
- `java.awt.headless=true` is the first statement of `main` and must stay there: anything that later
  touches Java2D (plot rasterisation, JLaTeXMath) otherwise makes the macOS AWT pipeline contend with
  JavaFX's Glass for the AppKit run loop, and the app intermittently deadlocks.
