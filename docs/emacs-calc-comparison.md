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

Confirmed against the staged jar: `Quantity`, `UnitConvert`, `Interval`, `DateObject`, `BaseForm`,
`IntegerDigits`, `Mean`, `Median`, `StandardDeviation`, `BesselJ`, `Gamma` all resolve today. Type
`StandardDeviation([1,2,3,4])` and it answers.

None of them appears in `Functions`, so none of them is findable — which is precisely the failure that
table's own javadoc describes. Most of what looks missing below is surface: a name, a key, a mode, a
printed form. Not capability.

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

### Surface only — the engine can already do it

| | Symja has | Issue |
|---|---|---|
| Units — `u c` convert, `u b` base, `u d` define | `Quantity`, `UnitConvert` | [#3](https://github.com/adriandeleon/Calcula/issues/3), [#5](https://github.com/adriandeleon/Calcula/issues/5) |
| Statistics — mean, median, sdev, covariance | `Mean`, `Median`, `StandardDeviation`, `Variance` | [#3](https://github.com/adriandeleon/Calcula/issues/3), [#6](https://github.com/adriandeleon/Calcula/issues/6) |
| Radix 2–36, `d 2` / `d 6` | `BaseForm`, `IntegerDigits` | [#3](https://github.com/adriandeleon/Calcula/issues/3), [#12](https://github.com/adriandeleon/Calcula/issues/12) |
| Gamma, erf, Bessel, Bernoulli, Stirling | all present | [#3](https://github.com/adriandeleon/Calcula/issues/3) |

### Not built

**Variables** ([#2](https://github.com/adriandeleon/Calcula/issues/2)) — and this one is a bug rather
than an absence. `Op.Store` and `Op.Recall` are implemented in `Machine`, `CalcState` carries the map,
`SheetFormat` reads and writes `var n 42`. Nothing emits either operation, nothing is bound under `s`,
and `Evaluator` never reads the bindings — it is `(Expr, Modes) -> Expr` and has no access to them. So
the map is empty for the life of every session, a hand-written `.calc` carrying variables loads and is
then unusable, and the application cannot produce one. The file format writes state only the file
format knows how to make. Calc spends a chapter on `s t`, `s r`, `s 0`–`s 9`, `s l` and `s d`.

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

**Vectors as something you can operate**
([#10](https://github.com/adriandeleon/Calcula/issues/10)) — `V M` map, `V R` reduce, `V A` apply,
`v p`/`v u` pack and unpack the stack, `v x` ranges, set operations. Calcula can invert a matrix and
find its eigenvalues, and cannot build a vector except by typing it out in full. Pack and unpack are
the two that matter most on a stack machine and neither exists. This is what makes statistics feel
further away than the function list suggests.

**Binary and word operations** ([#12](https://github.com/adriandeleon/Calcula/issues/12)) — and, or,
xor, shifts and rotates against a configurable word size, which is a mode because the operations are
not meaningful without one.

**Financial** ([#13](https://github.com/adriandeleon/Calcula/issues/13)) — pv, fv, npv, pmt, nper,
rate, and three depreciation methods. Self-contained: ordinary functions of numbers, no new kind, no
round-trip risk.

**A user-extension path** ([#14](https://github.com/adriandeleon/Calcula/issues/14)) — keyboard macros
with loops and conditionals, user-defined functions, `Z D` to bind one to a key. `CommandRegistry` is a
good foundation, since every surface already routes through `runCommand` and a recorded sequence of
command ids is a plausible macro. Worth being honest that Calc's programming layer exists partly
because it lives inside Emacs and inherits the macro machinery for free.

**Display and mode gaps** — no float format at all
([#4](https://github.com/adriandeleon/Calcula/issues/4)): `Formatter` prints a `Flt` with
`toPlainString()` and nothing else, so at the precision of 1000 that `Modes` permits, one number is a
thousand characters across the stack. And four missing modes
([#16](https://github.com/adriandeleon/Calcula/issues/16)): simplification level, matrix/scalar,
polar, working. The README's own argument — that a mode which displays and changes nothing is a wrong
answer the user has been told to expect — runs in reverse as well. A mode line with four entries reads
as though those are the four that exist.

**Stack ergonomics** ([#15](https://github.com/adriandeleon/Calcula/issues/15)) — last-args (`M-RET`),
editing an entry in place, yanking a line back out of the trail, searching it. Last-args is the one
worth naming: undo restores the *state*, last-args restores the *inputs*, and after a mistyped
operator the second is what you wanted. The trail already records both.

### Structurally different, and not deficiencies

Embedded mode, grabbing a region out of a buffer, keypad mode, quick-calc, and the language *input*
modes are Calc being part of Emacs. The equivalent here is the multi-format clipboard — Word takes the
MathML, Overleaf takes the TeX, chat takes the picture — and for a standalone application that is the
better answer. Gnuplot likewise: a plot as a stack value that participates in the trail and drops with
the same key as a number is a different bargain, and mostly a better one.

## Where to start

1. **[#2](https://github.com/adriandeleon/Calcula/issues/2), variables.** The machine half is written
   and tested and the file format exists; what is missing is two commands and a substitution step.
   Nothing else on this list is worth less work.
2. **[#3](https://github.com/adriandeleon/Calcula/issues/3), grow the catalogue.** Table edits only,
   and it makes a whole tier of already-working capability findable.
3. **[#4](https://github.com/adriandeleon/Calcula/issues/4), float display.** One `Modes` field and one
   `Formatter` branch, and it fixes something visible on every inexact answer.
4. **[#7](https://github.com/adriandeleon/Calcula/issues/7), rewrite rules.** The large one, and the
   one that would most change what Calcula is.
