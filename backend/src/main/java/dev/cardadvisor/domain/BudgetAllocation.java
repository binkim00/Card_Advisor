package dev.cardadvisor.domain;
import java.util.*;
/** Original purchase allocation; cancellation reduces each part proportionally, largest remainder first. */
public final class BudgetAllocation {
 public static Map<String,Long> net(Map<String,Long> original,long total,long remaining){
  if(total<=0||remaining<0||remaining>total||original==null||original.isEmpty()||original.size()>7)throw new IllegalArgumentException("INVALID_ALLOCATION");
  long sum=0;for(var v:original.values()){if(v==null||v<0||v>total)throw new IllegalArgumentException("INVALID_ALLOCATION");sum=Math.addExact(sum,v);}
  if(sum!=total)throw new IllegalArgumentException("ALLOCATION_SUM_MISMATCH");
  var result=new TreeMap<String,Long>();var remainders=new HashMap<String,Long>();long used=0;
  for(var e:original.entrySet()){long product=Math.multiplyExact(e.getValue(),remaining);long part=product/total;result.put(e.getKey(),part);remainders.put(e.getKey(),product%total);used+=part;}
  var order=new ArrayList<>(original.keySet());order.sort(Comparator.<String>comparingLong(remainders::get).reversed().thenComparing(Comparator.naturalOrder()));
  for(int i=0;i<remaining-used;i++){String key=order.get(i);result.put(key,result.get(key)+1);}
  return Collections.unmodifiableMap(result);
 }
 private BudgetAllocation(){}
}
