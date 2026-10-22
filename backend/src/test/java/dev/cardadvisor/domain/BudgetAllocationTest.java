package dev.cardadvisor.domain;
import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class BudgetAllocationTest {
 @Test void refundRoundingPreservesEveryWon(){var m=Map.of("FOOD",333L,"SHOPPING",333L,"OTHER",334L);for(long n=0;n<=1000;n++){var a=BudgetAllocation.net(m,1000,n);assertEquals(n,a.values().stream().mapToLong(Long::longValue).sum());}assertEquals(Map.of("FOOD",1L,"SHOPPING",0L,"OTHER",1L),BudgetAllocation.net(m,1000,2));}
 @Test void rejectsUnbalancedAndNegative(){assertThrows(IllegalArgumentException.class,()->BudgetAllocation.net(Map.of("FOOD",90L),100,50));assertThrows(IllegalArgumentException.class,()->BudgetAllocation.net(Map.of("FOOD",-1L,"SHOPPING",101L),100,50));}
}
