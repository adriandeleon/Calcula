package com.calcula.finance;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Worked examples, not self-consistency.
 *
 * <p>Every figure here is one a spreadsheet gives for the same arguments. A finance function tested
 * against its own formula proves the formula was typed twice; the failure worth catching is a sign
 * convention or an off-by-one period, and only an outside answer catches those.
 */
class FinanceTest {

    private static final MathContext MC = new MathContext(12);

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static String cents(BigDecimal value) {
        return Finance.cents(value).toPlainString();
    }

    @Test
    void theMonthlyPaymentOnAMortgage() {
        // 200,000 over 30 years at 6% a year, monthly. A spreadsheet says -1199.10: negative because
        // it is money leaving your hand, which is the convention everything here follows.
        BigDecimal payment = Finance.payment(d("0.005"), d("360"), d("200000"), BigDecimal.ZERO, MC);
        assertEquals("-1199.10", cents(payment));
    }

    @Test
    void whatThosePaymentsAreWorthNow() {
        // The other direction: paying 1199.10 a month for 30 years at the same rate is worth what was
        // borrowed, and comes back positive because it is money arriving.
        BigDecimal present = Finance.presentValue(d("0.005"), d("360"), d("-1199.10"), BigDecimal.ZERO, MC);
        assertEquals("199999.82", cents(present), "the rounding in the payment, and nothing else");
    }

    @Test
    void savingTheSameAmountEveryMonth() {
        // 100 a month for 10 years at 6%: 16,387.93.
        BigDecimal future = Finance.futureValue(d("0.005"), d("120"), d("-100"), BigDecimal.ZERO, MC);
        assertEquals("16387.93", cents(future));
    }

    @Test
    void aLumpSumLeftToGrow() {
        BigDecimal future = Finance.futureValue(d("0.05"), d("10"), BigDecimal.ZERO, d("-100"), MC);
        assertEquals("162.89", cents(future), "which is what the engine's own TimeValue answers");
    }

    @Test
    void howLongUntilItIsPaidOff() {
        // 200 a month against 10,000 at 6%: a spreadsheet says 57.68 periods.
        BigDecimal periods = Finance.periods(d("0.005"), d("-200"), d("10000"), BigDecimal.ZERO, MC);
        assertEquals(
                "57.68", periods.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
    }

    @Test
    void aZeroRateIsTheLimitRatherThanADivisionByZero() {
        // 1200 owed, 100 a month, no interest: twelve months, and the annuity factor is just n.
        assertEquals("12.00", cents(Finance.periods(BigDecimal.ZERO, d("-100"), d("1200"), BigDecimal.ZERO, MC)));
        assertEquals("-100.00", cents(Finance.payment(BigDecimal.ZERO, d("12"), d("1200"), BigDecimal.ZERO, MC)));
    }

    @Test
    void theRateThatMakesItBalance() {
        // The inverse of the mortgage above: it should find its way back to half a percent.
        BigDecimal rate = Finance.rate(d("360"), d("-1199.10"), d("200000"), BigDecimal.ZERO, MC);
        assertEquals("0.0050", rate.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString());
    }

    @Test
    void aRateThatCannotBalanceSaysSoRatherThanReturningItsLastGuess() {
        assertThrows(
                ArithmeticException.class,
                () -> Finance.rate(d("10"), d("1"), d("1"), d("1"), MC),
                "every term positive: nothing balances that");
    }

    @Test
    void netPresentValueDiscountsFromTheSecondAmountOn() {
        // -1000 now, then 500 three times at 10%: 243.43. The first amount is not discounted, which
        // is the half of this that implementations disagree about.
        BigDecimal npv = Finance.netPresentValue(d("0.10"), List.of(d("-1000"), d("500"), d("500"), d("500")), MC);
        assertEquals("243.43", cents(npv));
    }

    @Test
    void straightLineIsTheSameEveryPeriod() {
        assertEquals("1800.00", cents(Finance.straightLine(d("10000"), d("1000"), d("5"), MC)));
    }

    @Test
    void sumOfYearsWritesOffMoreEarly() {
        assertEquals("3000.00", cents(Finance.sumOfYears(d("10000"), d("1000"), d("5"), d("1"), MC)));
        assertEquals("600.00", cents(Finance.sumOfYears(d("10000"), d("1000"), d("5"), d("5"), MC)));
    }

    @Test
    void decliningBalanceNeverWritesPastTheSalvageValue() {
        // Double declining on 10,000 over 5 years is 40% a year: 4000, 2400, 1440, 864 — and then a
        // fifth period that wants 518.40 and may only have 296, because 9,000 is everything there is
        // to write off. That cap is the whole difference between this and applying a percentage.
        BigDecimal fourth = Finance.decliningBalance(d("10000"), d("1000"), d("5"), d("4"), d("2"), MC);
        BigDecimal fifth = Finance.decliningBalance(d("10000"), d("1000"), d("5"), d("5"), d("2"), MC);
        assertEquals("864.00", cents(fourth));
        assertEquals("296.00", cents(fifth), "capped at what is left rather than 518.40");
        assertTrue(fourth.signum() >= 0 && fifth.signum() >= 0, "and never a negative write-off");
    }
}
