package com.firetracker.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Reference tests for the XIRR engine, written test-first. The headline case is Microsoft's
 * own documented {@code XIRR} example, which any spreadsheet reproduces — so this pins our
 * Newton-Raphson + bisection solver to an externally verifiable number. Pure unit tests: no
 * Spring, no database.
 */
class XirrTest {

    private static final double TOL = 1e-6;

    private static CashFlow cf(String date, String amount) {
        return new CashFlow(LocalDate.parse(date), new BigDecimal(amount));
    }

    @Test
    void matchesMicrosoftDocumentedExample() {
        // From Microsoft's XIRR documentation; Excel/Sheets both return 0.373362535.
        List<CashFlow> flows = List.of(
                cf("2008-01-01", "-10000"),
                cf("2008-03-01", "2750"),
                cf("2008-10-30", "4250"),
                cf("2009-02-15", "3250"),
                cf("2009-04-01", "2750"));

        assertThat(Xirr.compute(flows)).isCloseTo(0.373362535, within(TOL));
    }

    @Test
    void solvesExactTenPercentOverOneNonLeapYear() {
        // 365 days apart (2021 is not a leap year) => years = 1.0 exactly => r = 0.10.
        List<CashFlow> flows = List.of(
                cf("2021-01-01", "-1000"),
                cf("2022-01-01", "1100"));

        assertThat(Xirr.compute(flows)).isCloseTo(0.10, within(TOL));
    }

    @Test
    void solvesTenPercentCompoundedOverTwoYears() {
        // (1+r)^2 = 1.21 => r = 0.10.
        List<CashFlow> flows = List.of(
                cf("2021-01-01", "-1000"),
                cf("2023-01-01", "1210"));

        assertThat(Xirr.compute(flows)).isCloseTo(0.10, within(TOL));
    }

    @Test
    void solvesNegativeReturn() {
        List<CashFlow> flows = List.of(
                cf("2021-01-01", "-1000"),
                cf("2022-01-01", "500"));

        assertThat(Xirr.compute(flows)).isCloseTo(-0.50, within(TOL));
    }

    @Test
    void convergesNearTheLowerBoundaryWhereNewtonStruggles() {
        // Almost a total loss: (1+r) = 0.001 => r = -0.999. Newton-Raphson from 0.10 tends to
        // overshoot below -1 (out of domain) here, so this exercises the bisection fallback.
        List<CashFlow> flows = List.of(
                cf("2021-01-01", "-1000"),
                cf("2022-01-01", "1"));

        assertThat(Xirr.compute(flows)).isCloseTo(-0.999, within(1e-4));
    }

    @Test
    void isIndependentOfCashFlowOrder() {
        List<CashFlow> ordered = List.of(
                cf("2008-01-01", "-10000"),
                cf("2008-03-01", "2750"),
                cf("2008-10-30", "4250"),
                cf("2009-02-15", "3250"),
                cf("2009-04-01", "2750"));
        List<CashFlow> shuffled = List.of(
                cf("2009-04-01", "2750"),
                cf("2008-10-30", "4250"),
                cf("2008-01-01", "-10000"),
                cf("2009-02-15", "3250"),
                cf("2008-03-01", "2750"));

        assertThat(Xirr.compute(shuffled)).isCloseTo(Xirr.compute(ordered), within(TOL));
    }

    @Test
    void rejectsCashFlowsWithoutASignChange() {
        // All outflows: no rate makes the NPV cross zero, so there is no IRR.
        List<CashFlow> flows = List.of(
                cf("2021-01-01", "-1000"),
                cf("2022-01-01", "-500"));

        assertThatThrownBy(() -> Xirr.compute(flows))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFewerThanTwoCashFlows() {
        assertThatThrownBy(() -> Xirr.compute(List.of(cf("2021-01-01", "-1000"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
