package dev.cardadvisor.domain;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import java.util.List;
import dev.cardadvisor.domain.PerformanceCalculator.Movement;
class PerformanceCalculatorTest {
 final PerformanceCalculator calculator=new PerformanceCalculator();
 @Test void partialCancelAtThreshold(){assertEquals(300000L,calculator.calculate(270000L,List.of(new Movement(50000,20000L,true))).totalWon());}
 @Test void fullCancelRestoresBaseline(){assertEquals(270000L,calculator.calculate(270000L,List.of(new Movement(50000,50000L,true))).totalWon());}
 @Test void exclusionsAndMixedTender(){assertEquals(315000L,calculator.calculate(270000L,List.of(new Movement(45000,0L,true),new Movement(10000,0L,false))).totalWon());}
 @Test void unknownBaselineNotZero(){var r=calculator.calculate(null,List.of(new Movement(50000,0L,true)));assertNull(r.totalWon());assertEquals(50000L,r.ledgerWon());}
 @Test void unknownRuleNotZero(){assertNull(calculator.calculate(270000L,List.of(new Movement(50000,0L,null))).totalWon());}
 @Test void unknownRefundNotZero(){assertNull(calculator.calculate(270000L,List.of(new Movement(50000,null,true))).totalWon());}
 @Test void fullyCancelledUnknownHasNoRemainingEffect(){assertEquals(270000L,calculator.calculate(270000L,List.of(new Movement(50000,50000L,null))).totalWon());}
 @Test void overRefundRejected(){assertThrows(IllegalArgumentException.class,()->new Movement(1,2L,true));}
 @Test void checkedOverflow(){assertThrows(ArithmeticException.class,()->calculator.calculate(Long.MAX_VALUE,List.of(new Movement(1,0L,true))));}
}
