package dev.cardadvisor.domain;
import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class HanaGoBenefitsTest{
 ActualBenefitCalculator.Purchase p(String a,long v){return new ActualBenefitCalculator.Purchase("GO_"+a,v,v);}
 @Test void tiersAndSharedCaps(){var rows=List.of(p("CONVENIENCE",60000),p("COFFEE",60000),p("UTILITIES",100000));assertEquals(0,ActualBenefitCalculator.go(0,rows).usedWon());assertEquals(5000,ActualBenefitCalculator.go(1,rows).usedWon());assertEquals(10000,ActualBenefitCalculator.go(2,rows).usedWon());assertEquals(0,ActualBenefitCalculator.go(2,rows).remainingWon());}
 @Test void thresholdsTransitAggregateAndRounding(){assertEquals(0,ActualBenefitCalculator.go(2,List.of(p("DIGITAL",6999),p("CONVENIENCE",9999),p("TRANSIT",49999))).usedWon());assertEquals(2850,ActualBenefitCalculator.go(2,List.of(p("DIGITAL",7000),p("TRANSIT",25000),p("TRANSIT",25000))).usedWon());assertEquals(501,ActualBenefitCalculator.go(1,List.of(p("COFFEE",10010))).usedWon());}
 @Test void frequencyKeepsUncertainOrderAsBounds(){var rows=new ArrayList<ActualBenefitCalculator.Purchase>();for(int i=0;i<5;i++)rows.add(p("COFFEE",10000));rows.add(p("COFFEE",20000));var v=ActualBenefitCalculator.go(1,rows);assertNull(v.usedWon());assertNull(v.remainingWon());assertEquals(2500,v.lowerWon());assertEquals(3000,v.upperWon());}
 @Test void cancellationAndFullRefund(){var v=ActualBenefitCalculator.go(1,List.of(new ActualBenefitCalculator.Purchase("GO_COFFEE",20000,10000)));assertEquals(500,v.usedWon());assertEquals(0,ActualBenefitCalculator.go(1,List.of(new ActualBenefitCalculator.Purchase("GO_COFFEE",20000,0))).usedWon());assertThrows(IllegalArgumentException.class,()->ActualBenefitCalculator.go(3,List.of()));}
}
