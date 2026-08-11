package com.calcula.input;

import java.util.ArrayList;
import java.util.List;

import com.calcula.expr.Expr;
import com.calcula.machine.CalcState;
import com.calcula.machine.MachineException;
import com.calcula.machine.Op;
import com.calcula.parse.Parser;

/**
 * Type the whole expression: {@code 2 + 3 * sin(x)}.
 *
 * <p>One line is one {@link Op.Push} of the parsed tree, except where it refers to the stack through
 * {@code $}. Those references are resolved here and the values they name are dropped, so {@code $ + 1}
 * replaces the top value rather than piling a second one on top of it — which is what makes algebraic
 * entry usable on a stack at all.
 */
public final class AlgebraicReader implements Reader {

    @Override
    public String id() {
        return "algebraic";
    }

    @Override
    public String label() {
        return "alg";
    }

    @Override
    public List<Op> read(String line, CalcState state) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        Expr parsed = Parser.parse(line);
        int consumed = StackRefs.deepest(parsed);
        if (consumed == 0) {
            return List.of(new Op.Push(parsed));
        }
        if (state.depth() < consumed) {
            throw new MachineException("$" + consumed + " refers past the end of a stack holding " + state.depth());
        }
        Expr resolved = StackRefs.substitute(parsed, state.stack());
        List<Op> ops = new ArrayList<>(2);
        // Drop first, then push: the references were already resolved to values, so the order here is
        // what decides whether the entry replaces its inputs or accumulates on top of them.
        ops.add(new Op.Drop(consumed));
        ops.add(new Op.Push(resolved));
        return ops;
    }
}
