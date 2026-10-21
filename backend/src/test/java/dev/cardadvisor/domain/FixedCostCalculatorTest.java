package dev.cardadvisor.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;

class FixedCostCalculatorTest {
 final FixedCostCalculator c=new FixedCostCalculator();
 FixedCostCalculator.Payment p(long amount,long refund,boolean included){return new FixedCostCalculator.Payment(amount,refund,included);}
 @Test void unpaid(){assertEquals(30000,c.calculate(30000,true,List.of()).remainingWon());}
 @Test void paidIsNotReservedTwice(){assertEquals(0,c.calculate(30000,true,List.of(p(30000,0,true))).remainingWon());}
 @Test void partialPayment(){assertEquals(10000,c.calculate(30000,true,List.of(p(20000,0,true))).remainingWon());}
 @Test void multiplePayments(){assertEquals(0,c.calculate(30000,true,List.of(p(20000,0,true),p(10000,0,true))).remainingWon());}
 @Test void partialCancellationRestoresReservation(){assertEquals(10000,c.calculate(30000,true,List.of(p(30000,10000,true))).remainingWon());}
 @Test void fullCancellation(){assertEquals(30000,c.calculate(30000,true,List.of(p(30000,30000,true))).remainingWon());}
 @Test void excludedExpenseDoesNotReleaseReservation(){var b=c.calculate(30000,true,List.of(p(30000,0,false)));assertEquals(30000,b.linkedNetWon());assertEquals(0,b.includedNetWon());assertEquals(30000,b.remainingWon());}
 @Test void overpaymentDoesNotCreateNegativeReservation(){assertEquals(0,c.calculate(30000,true,List.of(p(50000,0,true))).remainingWon());}
 @Test void inactivePlanKeepsPaymentsButNoReservation(){var b=c.calculate(30000,false,List.of(p(10000,0,true)));assertEquals(10000,b.includedNetWon());assertEquals(0,b.remainingWon());}
 @Test void monthEndAndLeapYear(){assertEquals(LocalDate.of(2028,2,29),c.dueDate(YearMonth.of(2028,2),31));assertEquals(LocalDate.of(2027,2,28),c.dueDate(YearMonth.of(2027,2),31));assertEquals(LocalDate.of(2026,4,30),c.dueDate(YearMonth.of(2026,4),31));}
 @Test void invalidAmountsRejected(){assertThrows(IllegalArgumentException.class,()->c.calculate(30000,true,List.of(p(100,101,true))));assertThrows(IllegalArgumentException.class,()->c.dueDate(YearMonth.of(2026,1),0));}
}
