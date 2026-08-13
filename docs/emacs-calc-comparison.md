# Calcula against Emacs Calc

What this has that Calc has, what it does not, and which of the gaps are real.

Measured against `123a221` by reading the source rather than by using the application: the command
registry and keymap, the `Op` vocabulary, the `Expr` tower, `Modes`, the `Names` and `Functions`
tables, both readers, and the symbol table of the staged `matheclipse-core-3.0.0.jar`. Calc's side is
its manual.

Tracked as [#17](https://github.com/adriandeleon/Calcula/issues/17).

## The shape of it

Calc is roughly a thousand commands over fifteen data types with a Lisp programming layer underneath.
Calcula is thirty commands, four part-transforms, forty-four key bindings, eighty catalogued functions
and four modes — sitting on a real CAS and a real typeset renderer.

So it is narrower almost everywhere, and deeper in the two places Calc is weakest: symbolic algebra,
and showing you the answer.

## The fact that reorders everything

`Names.toHead` passes an unknown name through verbatim, and `Expr.Call` round-trips a head it has
never heard of rather than throwing. That totality was adopted so an engine upgrade could not break
the adapter — and its second consequence is that **Symja's entire library is already callable**.

Type `StandardDeviation([1,2,3,4])` and it answers, and until the catalogue grew, nothing in the
window said so — which is precisely the failure that table's own javadoc describes. Most of what looks
missing below is surface: a name, a key, a mode, a printed form. Not capability.

**With one correction, which cost an afternoon and is the reason every catalogue entry is now run
before it is written down.** Reading a symbol out of the jar's symbol table proves the engine has it
and nothing else. Two things break that inference, in opposite directions:

- **`Fit` is a real Symja symbol that comes back unevaluated**, so the name existing said the opposite
  of the truth. `FindFit` is the one that answers.
- **`Quantity` and `UnitConvert` cannot be called at all.** They take a string, and this notation has
  no string literal — the lexer refuses the quote long before the engine is asked. Units were the
  headline example of a capability one table edit away, and they are not: they need syntax first.

So "the engine has it" and "a user can reach it" are different claims, and only the second one counts.

## Where Calcula is ahead

|  | Calc | Calcula |
|---|---|---|
| Integration | rule-based, gives up often | Rubi, through Symja |
| Display | `d B` big mode, ASCII art | typeset: STIX Two, built-up fractions, radicals, fences that grow |
| Subterm work | `j` prefix, keyboard only | click and hover, `M-Up`/`M-Down`/`M-Left`/`M-Right`, every node carrying its own address |
| Plots | shells out to gnuplot, opens a window that vanishes | a stack value, interactive, poles from `Solve(Denominator(Together(f)) = 0)`, turning points labelled `(pi/2, 1)` rather than `(1.5708, 1.0)` |
| Matrices | no symbolic eigenvalues | `Eigenvalues`, `LinearSolve`, `MatrixRank` |
| Export | TeX and eqn as language modes | TeX, MathML, Typst, PDF, and all of them on the clipboard at once |
| Documents | one buffer | several sheets, each with its own machine; plain-text `.calc` |

Level: RPN and algebraic entry, stack operations, trail, undo and redo, exact `Int`/`Rat` with
arbitrary-precision `Flt`, `$` and `$2` stack references, command discovery.

On entry Calcula is arguably the cleaner design. Calc bolts algebraic entry on with `'`; here both
readers produce the same `Op` vocabulary, neither is privileged, and `M-i` switches at runtime — which
is why the question of which one is the default could honestly be left to a preference.

## Where Calc is ahead

### Surface only — the engine can already do it, and now the catalogue says so

Everything here was probed against the real engine and is listed in `Functions` as of #3.

| | What answers | Issue |
|---|---|---|
| Statistics — mean, median, sdev, covariance | `Mean`, `Median`, `StandardDeviation`, `Variance`, `Quartiles`, `Correlation` | [#6](https://github.com/adriandeleon/Calcula/issues/6) |
| Curve fitting — `a F` | `FindFit` (**not** `Fit`, which comes back unevaluated) | [#6](https://github.com/adriandeleon/Calcula/issues/6) |
| Map, reduce and apply over a list — `V M`, `V R`, `V A` | `Map`, `Fold`, `Apply`, and `Range`/`Table` to make one | [#10](https://github.com/adriandeleon/Calcula/issues/10) |
| Set operations — `V +`, `V ^` | `Union`, `Intersection` | [#10](https://github.com/adriandeleon/Calcula/issues/10) |
| Gamma, erf, Bessel, Bernoulli | `Gamma`, `Erf`, `BesselJ`, `BernoulliB` — symbolic until wrapped in `N` | — |
| Numeric root, minimum, integral — `a R`, `a N` | `FindRoot`, `FindMinimum`, `NMaximize`, `NIntegrate` | — |
| Intervals `[a..b]` | `Interval`, and arithmetic carries the bounds | [#9](https://github.com/adriandeleon/Calcula/issues/9) |
| Digits of a number in a base | `IntegerDigits`, `FromDigits` — but not a radix *display* | [#12](https://github.com/adriandeleon/Calcula/issues/12) |

**Units are not on this list, and that is the correction.** `Quantity` and `UnitConvert` are in the
jar and neither can be typed: both take a string, and the notation has no string literal. Units need
syntax before they need a table entry — see [#5](https://github.com/adriandeleon/Calcula/issues/5).

### Not built

~~**Variables**~~ ([#2](https://github.com/adriandeleon/Calcula/issues/2),
[#24](https://github.com/adriandeleon/Calcula/issues/24)) — done, and worth keeping the diagnosis
because it is the most instructive thing in this document. It was never an absence. `Op.Store` and
`Op.Recall` were implemented in `Machine` and covered by tests; `SheetFormat` read and wrote
`var n 42`. Nothing emitted either operation, nothing was bound to a key, and `Evaluator` never read
the map — so it was empty for the life of every session, and the file format persisted state only the
file format could make. **A feature can be complete, correct, tested, and unreachable**, and no pure
test can see that, because a pure test constructs the operation it is testing. Calc's remaining
storage chapter — `s 0`–`s 9`, `s l` let-bindings, `s d` declarations — is still open.

**Rewrite rules** ([#7](https://github.com/adriandeleon/Calcula/issues/7)) — `a r`, with patterns,
meta-variables, conditions and iteration control. Calc's extensibility story: how you teach it
something without modifying it. Calcula's transform set is the four entries of `PART_TRANSFORMS` and
is closed. Two things make this less daunting than it sounds — Symja already has `ReplaceAll`,
`ReplaceRepeated` and full pattern matching, and selection already resolves a click to an exact
subterm with `ExprPath.replace` to put the answer back. Applying a rule *to the selected part* is the
gesture, and half of it exists.

**Extra numeric kinds** — error forms `x +/- s` with propagation
([#8](https://github.com/adriandeleon/Calcula/issues/8)), intervals, modulo forms and HMS
([#9](https://github.com/adriandeleon/Calcula/issues/9)), dates and times
([#11](https://github.com/adriandeleon/Calcula/issues/11)). These are the ones pass-through cannot
give: a kind, not a function. Each needs a place in the tower, arithmetic, a printed form, a typeset
form, and `Formatter`/`Parser` inverses — because `.calc` rests on that property, a value that prints
in a form the parser cannot read is data loss at *save* time. `Expr.Num` being sealed to three records
means adding one is a deliberate widening with a compiler-enforced list of everywhere that must learn
about it, which is the right way round.

~~**Vectors, on the stack rather than in an expression**~~
([#10](https://github.com/adriandeleon/Calcula/issues/10)) — done. The *functions* were never
missing, only unfindable; what was absent was the gesture. `M-v p` packs the top values into a list
and `M-v u` unpacks one, so the list half of the engine finally has something to take. That was the
last thing #6 was waiting on.

~~**Binary and word operations**~~ ([#12](https://github.com/adriandeleon/Calcula/issues/12)) — done,
along with radix. Typing a number in a base (`16#ff`) and showing one in a base turned out to be
separate features, which is what kept both cheap: entry is in the lexer and yields an ordinary
integer, so a saved sheet never sees the spelling, and display is in the layout beside the float
format.

~~**Financial**~~ ([#13](https://github.com/adriandeleon/Calcula/issues/13)) — done, and it opened the
seam that #12 then reused: a place for functions this calculator implements itself, consulted inside
the numeric fold and therefore working with no engine at all. Symja answers `TimeValue` and nothing
else of it.

**A user-extension path** ([#14](https://github.com/adriandeleon/Calcula/issues/14)) — keyboard macros
with loops and conditionals, user-defined functions, `Z D` to bind one to a key. `CommandRegistry` is a
good foundation, since every surface already routes through `runCommand` and a recorded sequence of
command ids is a plausible macro. Worth being honest that Calc's programming layer exists partly
because it lives inside Emacs and inherits the macro machinery for free.

**Display and mode gaps** — the float format is in as of
[#4](https://github.com/adriandeleon/Calcula/issues/4) (normal, fixed, scientific and engineering on
`M-m n`/`x`/`e`), though scientific still renders as `1.23e5` rather than a typeset `1.23 × 10⁵`.
Still missing: four modes
([#16](https://github.com/adriandeleon/Calcula/issues/16)): simplification level, matrix/scalar,
polar, working. The README's own argument — that a mode which displays and changes nothing is a wrong
answer the user has been told to expect — runs in reverse as well. A mode line with four entries reads
as though those are the four that exist.

~~**Stack ergonomics**~~ ([#15](https://github.com/adriandeleon/Calcula/issues/15)) — done. `M-RET`
needed no new state at all: every entry already carries the expression it came from, so the arguments
are the origin call's arguments, which also makes it work on a value from any point in the session
rather than only on the most recent command. `M-e` edits, `M-t y` yanks, `M-t s` searches.

### Structurally different, and not deficiencies

Embedded mode, grabbing a region out of a buffer, keypad mode, quick-calc, and the language *input*
modes are Calc being part of Emacs. The equivalent here is the multi-format clipboard — Word takes the
MathML, Overleaf takes the TeX, chat takes the picture — and for a standalone application that is the
better answer. Gnuplot likewise: a plot as a stack value that participates in the trail and drops with
the same key as a number is a different bargain, and mostly a better one.

## Where to start

The three cheap ones are done. What is left, in the order I would take it:

1. ~~**[#2](https://github.com/adriandeleon/Calcula/issues/2), variables.**~~ Done. Storing binds,
   `=` resolves, and nothing else does — which is Calc's division and the only real decision in it.
2. ~~**[#3](https://github.com/adriandeleon/Calcula/issues/3), grow the catalogue.**~~ Done, and it
   was not the table edit it looked like: every candidate had to be run first, which is how the units
   correction above was found.
3. ~~**[#4](https://github.com/adriandeleon/Calcula/issues/4), float display.**~~ Done — and it turned
   out to be one `Modes` field and one *layout* branch, not a `Formatter` one. A stack is saved by
   formatting it, so rounding there would be data loss at save time; the format rides on `MathStyle`
   with the digit grouping instead.
4. ~~**[#24](https://github.com/adriandeleon/Calcula/issues/24), list what is bound.**~~ Done —
   `M-s l`, and it turned up a real bug on the way: `CalcState` was finishing with `Map.copyOf`, so a
   session's bindings came back in an unspecified order and a saved sheet reshuffled itself.
5. ~~**[#10](https://github.com/adriandeleon/Calcula/issues/10), pack and unpack the stack.**~~ Done.
6. ~~**[#7](https://github.com/adriandeleon/Calcula/issues/7), rewrite rules.**~~ Done.
7. ~~**[#15](https://github.com/adriandeleon/Calcula/issues/15), stack ergonomics.**~~ Done.

**Nine closed**, and the three self-contained ones are done. What is left is four that are a new
numeric kind each — and the first one done will say what the rest cost:
[#5](https://github.com/adriandeleon/Calcula/issues/5) units (which needs notation first),
[#8](https://github.com/adriandeleon/Calcula/issues/8) error forms,
[#9](https://github.com/adriandeleon/Calcula/issues/9) intervals and friends,
[#11](https://github.com/adriandeleon/Calcula/issues/11) dates. One is large and open-ended:
[#14](https://github.com/adriandeleon/Calcula/issues/14), a user-extension path — though rewrite
rules have taken the pressure off it, since teaching this calculator something new no longer means
writing Java.
