package dev.cardadvisor.domain;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ActualBenefitCalculatorTest {
 ActualBenefitCalculator.Purchase p(String a,long n){return new ActualBenefitCalculator.Purchase(a,n,n);}
 @Test void tierCapsAndSelectionAreBySpend(){var rows=List.of(p("FOOD",50000),p("MEDICAL",100000),p("EDUCATION",80000),p("COFFEE",100000),p("TAXI",30000));var first=ActualBenefitCalculator.kb(1,rows);assertEquals(7000,first.usedWon());assertEquals(9000,first.remainingWon());assertEquals(16000,first.capWon());var second=ActualBenefitCalculator.kb(2,rows);assertEquals(12400,second.usedWon());assertEquals(19600,second.remainingWon());assertEquals(48000,ActualBenefitCalculator.kb(3,rows).capWon());}
 @Test void partialAndFullRecalculateOriginalMonthEstimate(){var before=ActualBenefitCalculator.kb(1,List.of(p("FOOD",90000)));assertEquals(2700,before.usedWon());var partial=ActualBenefitCalculator.kb(1,List.of(new ActualBenefitCalculator.Purchase("FOOD",90000,70000)));assertEquals(2100,partial.usedWon());assertEquals(13900,partial.remainingWon());var full=ActualBenefitCalculator.kb(1,List.of(new ActualBenefitCalculator.Purchase("FOOD",90000,0)));assertEquals(0,full.usedWon());assertEquals(16000,full.remainingWon());}
 @Test void fractionsRemainUnknownRatherThanInventingRounding(){var c=ActualBenefitCalculator.kb(1,List.of(p("COFFEE",11)));assertNull(c.usedWon());assertNull(c.remainingWon());assertEquals(1,c.lowerWon());assertEquals(2,c.upperWon());}
 @Test void minimumPerApprovalAndZeroTier(){assertEquals(0,ActualBenefitCalculator.kb(1,List.of(p("FOOD",9999))).usedWon());assertEquals(300,ActualBenefitCalculator.kb(1,List.of(p("FOOD",10000))).usedWon());assertEquals(0,ActualBenefitCalculator.kb(0,List.of(p("COFFEE",100000))).usedWon());assertThrows(IllegalArgumentException.class,()->ActualBenefitCalculator.kb(4,List.of()));}
 @Test void areaCapWinsOverAmbiguousRounding(){var c=ActualBenefitCalculator.kb(1,List.of(p("COFFEE",40001)));assertEquals(4000,c.usedWon());assertEquals(12000,c.remainingWon());}
}
