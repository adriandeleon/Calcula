package com.calcula.cas.symja;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A condition is only worth having if it actually gates the rewrite. */
class ConditionalRuleEngineTest {

    private static CasEngine engine;

    @BeforeAll
    static void start() {
        engine = new SymjaEngine();
    }

    private static String eval(String source) throws CasException {
        return Formatter.format(engine.eval(Parser.parse(source)));
    }

    /**
     * The same rule, applied twice, firing once.
     *
     * <p>Both halves matter. A rule that fires when the condition holds could equally be a rule with
     * the condition ignored — it is the case that must NOT fire that says the condition arrived.
     */
    @Test
    void aConditionDecidesWhetherTheRuleFires() throws CasException {
        assertEquals("25", eval("ReplaceAll(5, x_ :> x^2 /; x > 3)"));
        assertEquals("2", eval("ReplaceAll(2, x_ :> x^2 /; x > 3)"));
    }

    /** A delayed rule is a different head from an immediate one, and the engine treats it so. */
    @Test
    void aDelayedRuleReachesTheEngineAsOne() throws CasException {
        assertEquals("9", eval("ReplaceAll(3, x_ :> x^2)"));
    }
}
