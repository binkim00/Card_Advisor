package dev.cardadvisor.domain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import dev.cardadvisor.domain.BenefitCalculator.Movement;
class BenefitCalculatorTest {
 final BenefitCalculator calculator=new BenefitCalculator();
 Movement m(long charge,Long refund){return new Movement(charge,refund,300,BenefitCalculator.RULE);}
 @Test void purchaseConsumesPool(){assertEquals(new BenefitCalculator.Totals(1500L,8500L),calculator.calculate(10000L,true,List.of(m(50000,0L))));}
 @Test void partialCancelRecomputesNet(){assertEquals(new BenefitCalculator.Totals(900L,9100L),calculator.calculate(10000L,true,List.of(m(50000,20000L))));}
 @Test void fullCancelRestoresPool(){assertEquals(new BenefitCalculator.Totals(0L,10000L),calculator.calculate(10000L,true,List.of(m(50000,50000L))));}
 @Test void capExhaustion(){assertEquals(new BenefitCalculator.Totals(1000L,0L),calculator.calculate(1000L,true,List.of(m(50000,0L))));}
 @Test void cancelDoesNotRestoreMoreThanNetCapAllows(){assertEquals(new BenefitCalculator.Totals(1000L,0L),calculator.calculate(1000L,true,List.of(m(50000,10000L))));}
 @Test void entireMonthRecomputedAfterCancel(){assertEquals(new BenefitCalculator.Totals(1000L,0L),calculator.calculate(1000L,true,List.of(m(50000,50000L),m(50000,0L))));}
 @Test void roundPerApprovalNotMonthlyTotal(){assertEquals(new BenefitCalculator.Totals(0L,100L),calculator.calculate(100L,true,List.of(new Movement(50,0L,100,BenefitCalculator.RULE),new Movement(50,0L,100,BenefitCalculator.RULE))));}
 @Test void currentIneligibleDoesNotConsumePool(){assertEquals(new BenefitCalculator.Totals(0L,10000L),calculator.calculate(10000L,false,List.of(m(50000,0L))));}
 @Test void unknownEligibilityIsNotZero(){assertNull(calculator.calculate(10000L,null,List.of(m(50000,0L))).ledgerBenefitUsedWon());}
 @Test void unknownRefundIsNotZero(){assertNull(calculator.calculate(10000L,true,List.of(m(50000,null))).remainingCapWon());}
 @Test void unknownCapIsNotZero(){assertNull(calculator.calculate(null,true,List.of(m(50000,0L))).remainingCapWon());}
 @Test void unknownVersionIsHeld(){assertNull(calculator.calculate(10000L,true,List.of(new Movement(50000,0L,300,"UNVERIFIED"))).remainingCapWon());}
 @Test void noActivityDoesNotInventUsage(){assertEquals(new BenefitCalculator.Totals(0L,null),calculator.calculate(null,null,List.of()));}
 @Test void fullyCancelledUnknownHasNoEffect(){assertEquals(new BenefitCalculator.Totals(0L,10000L),calculator.calculate(10000L,null,List.of(new Movement(50000,50000L,null,null))));}
 @Test void zeroRateAndZeroCapAreKnown(){assertEquals(0L,calculator.calculate(0L,null,List.of(m(50000,null))).ledgerBenefitUsedWon());assertEquals(1000L,calculator.calculate(1000L,null,List.of(new Movement(10,null,0,BenefitCalculator.RULE))).remainingCapWon());}
 @Test void invalidAndOverflowRejected(){assertThrows(IllegalArgumentException.class,()->m(1,2L));assertThrows(ArithmeticException.class,()->calculator.calculate(10000L,true,List.of(m(Long.MAX_VALUE,0L))));}
}
