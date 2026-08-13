package com.calcula.cas.symja;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.calcula.cas.CasEngine;
import com.calcula.cas.CasException;
import com.calcula.parse.Formatter;
import com.calcula.parse.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Giving up on a computation.
 *
 * <p>The claim being tested is deliberately narrow, because the wider one is false: nothing ends a
 * running Symja computation from outside. Measured against this engine, a factorisation survives a
 * thread interrupt, its own {@code setStopRequested} flag and its own time constraint. What can be
 * had is the <b>caller</b> stopping waiting, and an engine that is usable again immediately
 * afterwards — which is the difference between an application that recovers and one that is furniture.
 *
 * <p>Uses a real engine and a genuinely hard input, because the whole question is what happens when
 * the computation does <em>not</em> cooperate. A stub that returned promptly would assert nothing.
 */
class CancelTest {

    /** Hard enough to still be running a second later; small enough to type. */
    private static final String SLOW = "FactorInteger(2^128 + 1)";

    @Test
    void givingUpFreesTheCallerAndLeavesTheEngineUsable() throws Exception {
        CasEngine engine = new SymjaEngine();
        engine.eval(Parser.parse("1+1")); // warm, so the timing is about evaluation

        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<String> outcome = new AtomicReference<>("still waiting");
        Thread caller = new Thread(() -> {
            started.countDown();
            try {
                engine.eval(Parser.parse(SLOW));
                outcome.set("finished on its own");
            } catch (CasException e) {
                outcome.set("gave up: " + e.getMessage());
            }
        });
        caller.setDaemon(true);
        caller.start();

        assertTrue(started.await(5, TimeUnit.SECONDS));
        Thread.sleep(1200);
        assertTrue(caller.isAlive(), "precondition: this input really is slow enough to be worth cancelling");

        long began = System.nanoTime();
        engine.cancel();
        caller.join(5000);
        long freedMs = (System.nanoTime() - began) / 1_000_000;

        assertTrue(!caller.isAlive(), "the caller must stop waiting, whatever the computation does");
        assertTrue(freedMs < 3000, "and promptly: took " + freedMs + "ms");
        assertTrue(outcome.get().startsWith("gave up"), outcome.get());

        // The point of replacing the evaluator: the engine works immediately afterwards, while the
        // abandoned computation is very probably still going on a thread nobody is waiting for.
        assertEquals("5/6", Formatter.format(engine.eval(Parser.parse("1/2 + 1/3"))));
        engine.close();
    }

    /** Cancelling when there is nothing to cancel is not an error. */
    @Test
    void cancellingAnIdleEngineDoesNothing() throws Exception {
        CasEngine engine = new SymjaEngine();
        engine.cancel();
        assertEquals("2", Formatter.format(engine.eval(Parser.parse("1+1"))));
        engine.close();
    }

    /** An engine that cannot be interrupted says so by doing nothing, and stays correct. */
    @Test
    void theSeamIsOptional() {
        CasEngine plain = new CasEngine() {
            @Override
            public String id() {
                return "plain";
            }

            @Override
            public String version() {
                return "1";
            }

            @Override
            public com.calcula.expr.Expr eval(com.calcula.expr.Expr input) {
                return input;
            }

            @Override
            public String texForm(com.calcula.expr.Expr input) {
                return "";
            }

            @Override
            public String mathmlForm(com.calcula.expr.Expr input) {
                return "";
            }
        };
        plain.cancel();
        assertNotNull(plain.id());
    }
}
