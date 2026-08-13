# Rule & Plate

The visual language for Calcula. Not a component library — Calcula has no buttons, no dialogs and no
cards, so there is nothing to catalogue. What it has instead is four regions, a type system for
setting mathematics, and a small set of colours that each mean exactly one thing.

The reference is a plate in a mathematics monograph: cool paper, engineered spacing, and rules drawn
only where a rule carries meaning. Deliberately not a notebook — warm cream and a sepia serif are the
wrong register for a CAS, which is an instrument.

---

## Themes

Two: **Plate** (light) and **Slab** (dark). Both live in
`resources/com/calcula/styles/themes/` and are applied by `com.calcula.ui.Themes`.

They are **override sheets, not control themes.** AtlantaFX's Primer stays the user-agent stylesheet
and keeps styling every standard control; the theme sheet redefines only the semantic `-color-*`
tokens Primer resolves against. A scene stylesheet outranks the user-agent one, so the whole window
re-colours without a single component rule being restated.

Authoring a complete AtlantaFX theme was the alternative. It means carrying roughly 4 800 lines of
component CSS that has nothing to do with Calcula, and re-vendoring it on every AtlantaFX bump. The
token block is about seventy lines and is the only part that is actually ours.

Load order is owned by `Themes.apply(scene, theme)` and is **Primer → theme tokens → `app.css`**.
`app.css` is written entirely in tokens, so applying it before the sheet that defines them leaves
every colour unresolved.

### Plate

| Token | Value | Job |
|---|---|---|
| `-color-bg-default` | `#f6f7f9` | Window, stack, echo area |
| `-color-bg-subtle` | `#eceef2` | Trail, mode line |
| `-color-fg-default` | `#1b1f27` | Stack values, typed input |
| `-color-fg-muted` | `#5c6472` | Trail text |
| `-color-fg-subtle` | `#8b93a1` | Stack index, notes |
| `-color-border-default` | `#d5dae1` | Rules |
| `-color-accent-fg` | `#2563a8` | Results, prompt, selection |
| `-color-success-fg` | `#2c7355` | CAS available |
| `-color-danger-fg` | `#b3392c` | Errors |
| `-calc-inexact` | `#9a6a10` | Not exact, or not finished |

### Slab

Same roles: `#14171c` / `#1b1f26` / `#e4e8ee` / `#99a2b1` / `#6a7383` / `#2c323b` / `#6ba6e8` /
`#54bd8e` / `#ef7167` / `#d9a441`.

Not an inversion. The dark steps are chosen against the slab ground rather than derived by flipping
the light ones — an accent that reads well on `#f6f7f9` is too dark on `#14171c`, and a naive flip is
how a dark theme ends up with a muddy accent and unreadable status colours.

---

## The state language

Six meanings. Each is named once as a token and referenced; never restate a hex at the call site, or
the meaning drifts the first time one is tuned.

| Meaning | Token | Where it appears |
|---|---|---|
| **Ink** — what you said | `-color-fg-default` | Stack values, echo text, `Kind.INPUT` |
| **Azure** — what came back | `-color-accent-fg` | `Kind.RESULT`, the prompt, selection, plot series 1 |
| **Amber** — not exact, or not finished | `-calc-inexact` | Stack gutter, index, pending chord, held result |
| **Vermilion** — why nothing came back | `-color-danger-fg` | `Kind.ERROR`, `CAS unavailable` |
| **Verdigris** — the engine is here | `-color-success-fg` | Mode line CAS slot |
| **Faint** — the calculator on itself | `-color-fg-subtle` | `Kind.NOTE`, echo-area notes, mode flags, stack index |

Vermilion never means "important" and never decorates a heading, so when it appears it is
unambiguous. Verdigris has exactly one use: green is a poor accent and a good confirmation.

### Amber is the argument for the whole kit

A CAS spends its life moving between exact and approximate. `Modes` already models the **policy**
(`symbolic`, `fractions`) and the mode line reports it — but nothing reported the **value**.

So every stack row carries a 3 px gutter rail before its index: transparent when the value is exact,
amber when it is not. `5/6` is exact ink; `0.833333333333` is marked. Metadata lives in the gutter so
the value itself stays clean — the same division an editor makes between its gutter and its text. The
rail is *always present* and usually transparent, because adding it only when a marker applies would
shift the text beside it by three pixels the moment a value changed.

The predicate lives in `RowMarker` — the cell asks a question rather than working one out — and the
core of it is `Exprs.containsInexact`, which is **not** `!isExact`:

```java
Exprs.isExact(x + 1)          // false — it is a Call, not an exact *number*
Exprs.containsInexact(x + 1)  // false — but it carries no numeric error
```

`isExact` is shallow and answers false for every symbol and every `Call`. Negating it to mean
"inexact" marks every symbolic result the CAS returns, which is both wrong and the most visible thing
in the window. What the UI wants is contamination: one `Flt` buried anywhere in a sum makes the whole
value approximate, and nothing else does.

The same amber marks a half-entered chord, because `C-x-` is likewise a value that has not settled.

### Where a value came from

The stack shows what you have; it never showed how you got it. A list of seven pairs is the answer to
`FactorInteger(2^64 - 1)` and is indistinguishable, sitting there, from a matrix somebody typed.

So a stack entry is a value **and its origin** — the expression it was worked out from, before
evaluation. Enter `1/3 + 1/6` and the value is `1/2` while the origin is the sum. Rows say it in a
tooltip and to a screen reader, and say nothing at all when the origin is the value itself, which is
most of the time: `42` produces `42`, and "from: 42" on every row would be a window full of tooltips
saying nothing.

The pair is held in **one list, not two**. Every operation the machine performs is list surgery —
remove the top two, swap them, rotate a group, clear a range — and surgery on pairs carries the origin
along without a line of code that knows it exists. Two parallel lists would be the same twelve
operations written twice, and the day they disagree is the day a value wears somebody else's history,
which reads as data rather than as a bug.

It lives in `CalcState` rather than beside it in the window, so undo restores it for free — a snapshot
is the whole state, and history that had to be re-derived after an undo is history that would
eventually be wrong.

It is **session-only**. The `.calc` format saves the mathematics, and where a value came from is not
part of the mathematics; a loaded sheet answers with silence, which is true — it came from a file.
The alternative is a second thing on every `stack` line, and the format rests on Formatter and Parser
being inverses.

### Reading a result as the thing it is

`FactorInteger(2^64 - 1)` comes back as seven pairs of integers. That is a faithful answer and an
unreadable one, and it was drawn in matrix brackets — a claim about linear algebra. Set as
`3·5·17·257·641·65537·6700417` it is the factorisation it actually is.

This needs the **origin**, and is the reason provenance was worth having. The value alone cannot be
told apart: `[[2, 2], [3, 1]]` typed by hand and `[[2, 2], [3, 1]]` returned by `FactorInteger` are
the same expression, and guessing from shape would mis-set real matrices to fix this one. Knowing
what produced it settles the question with no guessing at all — the two are drawn differently in the
same window, and a test says so in both directions.

A reading is **not addressable**. The tree being drawn is not the one on the stack, so a click
resolving to a path inside it would hand a transform an address into an expression that does not
exist — an edit applied to something the reader cannot see. It is rendered with a null root path,
which is the same mechanism a reassembled product already used for the same reason.

The recogniser is deliberately unforgiving: anything that is not exactly the expected shape gets no
reading and is drawn as it always was. A reading that is sometimes wrong is worse than none, because
it is a wrong answer in the confident voice of a right one.

### Long numbers

`18446744073709551615` is twenty digits nobody counts correctly on the first try, so a run of five or
more is grouped in threes with a **thin space**: `18 446 744 073 709 551 615`.

Not a comma. A comma is a list separator everywhere else in this window — inside `[1, 2, 3]`, inside
`f(x, y)` — and using it inside a single number as well would make `[1,234, 5]` genuinely ambiguous.
A thin space cannot be mistaken for anything, which is why it is the SI convention.

Five, because four digits is a year and wants to stay one word: `2 026` reads as two numbers. Only
the integer part is grouped; a number grouped on both sides of the point is harder to read, not
easier.

Display only, and it happens in the layout rather than in `Formatter` — the formatter's job is
producing something the parser can read back, and this is how the number is *read*. Copying,
exporting and saving are untouched.

### The other margin

The rail says *whether* a value is exact. The right-hand margin says **how big it is**: `5/6` with
`≈ 0.833333333333` out at the edge. Same division as the gutter — metadata goes beside the value,
never inside it — applied at the other end of the row, in a margin that measurement showed was empty
for about half the column.

Most rows show nothing, which is what keeps it quiet. An integer *is* its decimal; anything already
carrying float error has nothing to add and wears the rail instead; anything with a free symbol has
no decimal at all, and asking gets `1 + x` back — a round trip spent to learn nothing. That
structural refusal is what keeps the engine out of it: the question is only put for a closed form.

A ratio is worked out with `BigDecimal` and never reaches the engine at all. That is deliberate
rather than an optimisation: the CAS is a capability and not a precondition, so a window with no
engine still adds up fractions — and it should still be able to say how big the answer is.

**Two things the rail learned later, both of which are the same idea applied properly.**

*Not finished* was written into the meaning from the start and nothing was asking for it.
`Hold(Fibonacci(100))` reached the stack in the same ink, at the same weight, with a transparent
rail, sitting beside real answers — a failure wearing the costume of a result. It is marked now, and
`RowMarker.explanation` says which name the engine handed back, because a rail that cannot be
interrogated is a puzzle rather than a signal.

Only `Hold` is detected, and that limit is deliberate. The engine distinguishes one of the two shapes
of "nothing happened": it wraps `Fibonacci(100)`, but an unrecognised head passes through untouched —
`Frobnicate(3)` comes back as `frobnicate(3)`, which is structurally identical to a perfectly good
symbolic result. Marking on "did not reduce to a number" would paint the marker over most of what a
CAS correctly returns, which is the `!isExact` mistake in a new coat.

*A plot is judged by what it graphs.* `PlotValue.of` carries the viewport as two doubles, so a plot
is a `Call` with two `Flt` arguments and the predicate — which walks every argument — marked **every
plot ever drawn** approximate. The bounds say where the picture was cropped, not that the function is
inexact. A marker that appears on something it does not describe teaches the eye to stop reading it,
which costs more than a missing one.

---

## Typography

Three faces, one job each.

| Face | Region | Why |
|---|---|---|
| **STIX Two Text** | Stack | A true math italic and full Greek. Times-metric, OFL, and it has the glyphs the stack will need the moment it sets an integral. |
| **Inter** | Mode line | A status strip, not data. Legible at 11 px with tight tracking, and it should not compete with the mathematics above it. |
| **JetBrains Mono** | Trail, echo area | A log wants its `=` and `!` sigils in a column; the echo area is a text field being edited character by character. |

Monospace is wrong for the stack. It has no real italic, and a fixed advance width destroys the
spacing that carries meaning — a binary operator wants more air than a function applied to its
argument.

**None of the three is bundled yet.** Each is named with a full fallback chain in `app.css`, so a
machine with none of them installed still lands on a serif rather than on nothing. Bundling them as
resources and registering them with `Font.loadFont` before any stylesheet is applied is the
outstanding follow-up; until then the chain is load-bearing.

### Setting the mathematics

Implemented in `ui.math` (`MathLayout` and friends): `Expr` renders to a tree of JavaFX nodes, one
per subexpression, which is also the tree selection mode will hit-test against. These are the rules
it follows.

- **Math italic for variables, upright for function names and digits.** A single-letter symbol is a
  variable; a multi-letter name is a function. That distinction is the whole reason `sin` is not read
  as `s·i·n`.
- **Real operator glyphs.** Minus is U+2212, not a hyphen — it is wider and sits on the math axis.
  Multiplication is a centre dot, or nothing between a coefficient and its variable.
- **Space by operator class**, in TeX's mu units: ordinary 0; binary operator 4 mu (0.222 em) both
  sides; relation 5 mu (0.278 em) both sides; punctuation 3 mu after only; function to argument 3 mu.
- **The 100 / 70 / 50 script cascade.** A superscript is 70 % of its parent; a superscript on a
  superscript is 70 % of that, which is 50 % of text size and the floor.

Stretchy delimiters are `Path`: JavaFX cannot read an OpenType MATH table, so tall brackets and
radicals get drawn, not typed.

`Formatter` still emits `-x*cos(x) + sin(x)` — it is the *textual* form, used by the trail and by
tests. The stack is set from the same `Expr` by `MathLayout`, which is why the two do not have to
agree character for character.

---

## The regions

| Region | Face & size | Metrics | Behaviour |
|---|---|---|---|
| **Trail** | JetBrains Mono 11 | 1 / 10 padding | Closeable with `C-x 1`, and its width is remembered. Sigil and text in separate boxes, so `=` and `!` keep a column *and* a wrapped line resumes under the text. Scrolls to the tail on every publish. 28 % of the split, not resizable with the parent. |
| **Stack** | `MathLayout`, 17 pt | 3 px rail + 8 px gap, 38 px index, 10 px gap, decimal in the right margin | Bottom-aligned, so entry `1:` sits against the echo area. Two nested boxes: the rail fills height, and a formula aligns on its baseline while a *picture* aligns to its top — a chart has no baseline, and a block is labelled at its top. Renumbers whole-list on any change. |
| **Mode line** | Inter 11 | 4 / 12 padding, hairline on top | At the bottom edge of the frame. Modes left, CAS right. An *off* flag is omitted, not greyed — Calc's own convention, and it keeps the strip short. |
| **Echo area** | JetBrains Mono 14 | 8 / 12 / 10 padding | No border, no focus ring, transparent ground: a line of the page, not a widget on it. Carries transient notes at its right-hand end — what the *interface* just did, as against what happened to the mathematics. |
| **Preview** | `MathLayout`, follows the stack | 4 / 12 / 0 / 26 padding | The line being typed, set as mathematics directly above it. Parses, never evaluates. Unmanaged when quiet, so a blank line costs no height. |

The preview is a fifth region only in the sense that it occupies its own strip; it is part of the
echo area's job, which is the line you are working on rather than the record of what you have done.

**The strip is at the bottom, and that is a departure.** Emacs puts a window's mode line above the
echo area, so the faithful order — the one this had — was stack, modes, input. It separated the one
pair of regions that form a single conversation: entry `1:` and the line being typed into it, with a
band of status between them. Emacs was the starting point rather than the specification, and status
belongs at an edge: it is the part of the window you read when you go looking, not the part you work
against.

Densities differ on purpose. The stack is read by scanning down a column of values, so its rows are
loose; the trail is skimmed for a landmark and then read backwards, so it is tight. Two jobs, two
numbers — not one shared constant.

Nothing in the window is centred, and nothing is right-aligned except the stack index and the CAS
slot. Everything else hangs from the same left margin so the eye has one edge to track.

### The prompt is the status indicator

With no toolbar and no dialogs, the one glyph the eye is already resting on carries the machine's
state. `CalcWindow.setPrompt` owns text and style together so the two cannot drift.

| Form | State |
|---|---|
| `›` azure | Ready |
| `…` | Working — a CAS call is out |
| `C-x-` amber | Prefix held, waiting for the next key |
| `›` vermilion | Last line failed and was handed back |

---

## Identity

The mark is an **integral operating on a small stack of terms**. Masters in `branding/`, rasters in
`resources/com/calcula/icons/`.

The accented rule is the **bottom** one, because Calcula draws entry `1:` — the top of the stack —
nearest the input. Two muted rules against one heavy accent, never three equal bars: equal bars read
as lines of text, which is what the first attempt got wrong. It looked like a note-taking app.

**Reduction: one cut, at 32 px.** Above it, the full mark. Below it the rules stop resolving and turn
into a smudge, so they are dropped and the integral takes the accent colour — a single-element mark
should carry the brand colour rather than sit in neutral ink. There is no third form; every extra
composition is another thing to keep in sync.

The SVG masters **set** the integral rather than drawing it, so they depend on a math font being
installed. Convert to outlines before shipping anything that renders the SVG directly. The PNGs carry
no such dependency and are what `stage.getIcons()` loads.

---

## Plotting

Not implemented. When it lands, four series assigned in **fixed order and never cycled**:

| | Plate | Slab |
|---|---|---|
| 1 | `#2563a8` | `#4a90e2` |
| 2 | `#0e9488` | `#12a894` |
| 3 | `#8b45d6` | `#9a6fe0` |
| 4 | `#c4356b` | `#e05a80` |

Both sets are validated for colour-vision separation against their own ground; the dark steps are
chosen for the slab, not brightened from the light ones. Vermilion, verdigris and amber are
deliberately absent — they are status colours, and a series that borrows one makes both unreadable.

Curves are **directly labelled**, never legended. The name of a function is short and belongs at the
end of its own line, and direct labelling is also the secondary encoding that keeps adjacent series
distinguishable without relying on hue alone.

---

## Still open

- **Bundle the three faces.** Named with fallbacks today; not shipped.
- **Selection mode**, on the node tree `MathLayout` already produces.
- **Plotting**, against the palette above.
- **A theme setting.** `Themes` supports both and `App` hardcodes `DEFAULT`; nothing persists a
  choice yet, because Calcula has no settings file.
