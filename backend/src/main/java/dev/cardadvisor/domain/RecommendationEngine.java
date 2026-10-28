package dev.cardadvisor.domain;

import java.util.*;
import java.util.stream.*;

public final class RecommendationEngine {
 public enum Mode { BENEFIT, PERFORMANCE, BALANCED }
 public record Card(String id, String name, Integer rateBp, Boolean qualified, Long performanceWon,
                    Long targetWon, Long remainingCapWon, Boolean performanceEligible, String source) {
  public Card {
   Objects.requireNonNull(id); Objects.requireNonNull(name); Objects.requireNonNull(source);
   if((rateBp==null&&!"IMPORTED_UNVERIFIED".equals(source)) || rateBp!=null&&(rateBp<0 || rateBp>10000) || negative(performanceWon)||negative(targetWon)||negative(remainingCapWon))
    throw new IllegalArgumentException("INVALID_CARD_STATE");
  }
 }
 public record Checkout(long orderValueWon,long pointUsedWon,long prepaidMoneyUsedWon,long cardChargeWon,
                        String category,String paymentMethod,int installments,String splitApproval,boolean confirmedByUser,ActualCardRules.Facts purchaseFacts,Map<String,Long> allocationsWon) {
  public Checkout(long o,long p,long m,long c,String category,String payment,int installments,String split,boolean confirmed,ActualCardRules.Facts facts){this(o,p,m,c,category,payment,installments,split,confirmed,facts,null);}
  public Checkout(long o,long p,long m,long c,String category,String payment,int installments,String split,boolean confirmed){this(o,p,m,c,category,payment,installments,split,confirmed,null);}
  public Checkout {
   if(orderValueWon<0||orderValueWon>10_000_000||pointUsedWon<0||prepaidMoneyUsedWon<0||cardChargeWon<0
     ||Math.addExact(Math.addExact(pointUsedWon,prepaidMoneyUsedWon),cardChargeWon)!=orderValueWon)
    throw new IllegalArgumentException("AMOUNT_MISMATCH");
   if(installments<1||installments>60) throw new IllegalArgumentException("INVALID_INSTALLMENTS");
   Objects.requireNonNull(category); Objects.requireNonNull(paymentMethod); Objects.requireNonNull(splitApproval);
   if(!Categories.ALL.contains(category)||!Set.of("DIRECT_CARD","NAVER_PAY_CARD","OTHER").contains(paymentMethod)||!Set.of("NO","YES","UNKNOWN").contains(splitApproval))throw new IllegalArgumentException("INVALID_CHECKOUT_CONDITION");
   if(allocationsWon==null)allocationsWon=Map.of(category,Math.addExact(prepaidMoneyUsedWon,cardChargeWon));
   if(allocationsWon.isEmpty()||!Categories.ALL.containsAll(allocationsWon.keySet())||!allocationsWon.containsKey(category))throw new IllegalArgumentException("INVALID_CHECKOUT_ALLOCATION");
   long sum=0;for(Long v:allocationsWon.values()){if(v==null||v<0)throw new IllegalArgumentException("INVALID_CHECKOUT_ALLOCATION");sum=Math.addExact(sum,v);}
   if(sum!=Math.addExact(prepaidMoneyUsedWon,cardChargeWon))throw new IllegalArgumentException("CHECKOUT_ALLOCATION_MISMATCH");allocationsWon=Map.copyOf(allocationsWon);
  }
  public long expenseWon(){return Math.addExact(prepaidMoneyUsedWon,cardChargeWon);}
 }
 public record CategoryBudget(Long limitWon,Long spentWon){}
 public record Budget(long totalWon,long spentWon,Long categoryLimitWon,Long categorySpentWon,long fixedRemainingWon,Map<String,CategoryBudget> categories) {
  public Budget(long total,long spent,Long limit,Long categorySpent,long fixed){this(total,spent,limit,categorySpent,fixed,Map.of());}
  public Budget {
   if(totalWon<0||spentWon<0||fixedRemainingWon<0||negative(categoryLimitWon)||negative(categorySpentWon))
    throw new IllegalArgumentException("INVALID_BUDGET");
  }
 }
 public record Candidate(String cardId,String name,Long benefitWon,Long performanceAfterWon,Long gapAfterWon,
                         boolean targetReached,Long opportunityCostWon,List<String> uncertaintyReasons,String source,String ruleVersion) {
  public Candidate(String id,String name,Long benefit,Long after,Long gap,boolean reached,Long cost,List<String> reasons,String source){this(id,name,benefit,after,gap,reached,cost,reasons,source,source);}
 }
 public record Result(String status,String recommendedCardId,long expenseWon,long overallAfterWon,
                      Long categoryAfterWon,long availableAfterWon,boolean budgetWarning,List<Candidate> candidates,
                      List<String> warnings,Map<String,Long> categoryAfterByCode) {}
 private static boolean negative(Long x){return x!=null&&x<0;}
 public Result evaluate(List<Card> cards,Checkout checkout,Budget budget,Mode mode,long tolerance) {
  if(tolerance<0||tolerance>10_000_000) throw new IllegalArgumentException("INVALID_TOLERANCE");
  long overall=Math.subtractExact(Math.subtractExact(budget.totalWon,budget.spentWon),checkout.expenseWon());
  var categoryAfter=new TreeMap<String,Long>();
  for(var allocation:checkout.allocationsWon.entrySet()){
   CategoryBudget cb=budget.categories==null?null:budget.categories.get(allocation.getKey());
   if(cb==null&&checkout.allocationsWon.size()==1)cb=new CategoryBudget(budget.categoryLimitWon,budget.categorySpentWon);
   categoryAfter.put(allocation.getKey(),cb==null||cb.limitWon==null||cb.spentWon==null?null:Math.subtractExact(Math.subtractExact(cb.limitWon,cb.spentWon),allocation.getValue()));
  }
  Long category=checkout.allocationsWon.size()==1?categoryAfter.get(checkout.category):null;
  long available=Math.subtractExact(overall,budget.fixedRemainingWon);
  boolean over=overall<0||available<0||categoryAfter.values().stream().anyMatch(v->v!=null&&v<0);
  List<String> warnings=new ArrayList<>();
  if(over) warnings.add("BUDGET_EXCEEDED");
  if(!checkout.confirmedByUser) warnings.add("CONFIRM_CHECKOUT");
  if(checkout.cardChargeWon==0) warnings.add("NO_CARD_CHARGE");
  if(categoryAfter.values().stream().anyMatch(Objects::isNull)) warnings.add("CATEGORY_BUDGET_UNKNOWN");
  List<Candidate> candidates=new ArrayList<>();
  for(Card c:cards){
   List<String> unknown=new ArrayList<>();
   boolean actual=ActualCardRules.actual(c.source);
   if(!"SYNTHETIC".equals(c.source)&&!actual) unknown.add("PRODUCT_RULES_NOT_VERIFIED");
   if(actual&&!ActualCardRules.supported(checkout.purchaseFacts))unknown.add("ACTUAL_PURCHASE_FACTS_REQUIRED");
   if(!actual&&checkout.purchaseFacts!=null)unknown.add("TEST_CARD_EXCLUDED_FROM_ACTUAL_PURCHASE");
   if(c.qualified==null&&!actual) unknown.add("CURRENT_QUALIFICATION_UNKNOWN");
   if(c.remainingCapWon==null && !Boolean.FALSE.equals(c.qualified)&&!actual) unknown.add("CAP_UNKNOWN");
   if(!Set.of("DIRECT_CARD","NAVER_PAY_CARD").contains(checkout.paymentMethod)) unknown.add("PAYMENT_METHOD_UNSUPPORTED");
   if(checkout.installments!=1) unknown.add("INSTALLMENT_RULE_UNVERIFIED");
   if(!"NO".equals(checkout.splitApproval)) unknown.add("SPLIT_APPROVAL_UNVERIFIED");
   Long benefit=actual?(unknown.isEmpty()?ActualCardRules.benefit(c.source,checkout.cardChargeWon,checkout.purchaseFacts):null):unknown.isEmpty()?(Boolean.TRUE.equals(c.qualified)?
    Math.min(Math.multiplyExact(checkout.cardChargeWon,(long)c.rateBp)/10000,c.remainingCapWon):0L):null;
   Long after=c.performanceWon==null||c.performanceEligible==null?null:
    Math.addExact(c.performanceWon,(actual?ActualCardRules.eligible(checkout.purchaseFacts):Boolean.TRUE.equals(c.performanceEligible))?checkout.cardChargeWon:0);
   // Unverified purchase conditions also make performance changes conditional.
   if(!unknown.isEmpty() && (unknown.contains("PRODUCT_RULES_NOT_VERIFIED")||
      unknown.contains("PAYMENT_METHOD_UNSUPPORTED")||unknown.contains("INSTALLMENT_RULE_UNVERIFIED")||
      unknown.contains("SPLIT_APPROVAL_UNVERIFIED")||unknown.contains("ACTUAL_PURCHASE_FACTS_REQUIRED")||unknown.contains("TEST_CARD_EXCLUDED_FROM_ACTUAL_PURCHASE"))) after=null;
   if(ActualCardRules.LOG.equals(c.source)&&unknown.isEmpty())after=0L;
   Long gap=after==null||c.targetWon==null?null:Math.max(0,c.targetWon-after);
   boolean reached=after!=null&&c.targetWon!=null&&c.performanceWon<c.targetWon&&after>=c.targetWon;
   if(after==null) unknown.add("PERFORMANCE_UNKNOWN");
   candidates.add(new Candidate(c.id,c.name,benefit,after,gap,reached,null,List.copyOf(unknown),actual?"TERMS_ESTIMATE":c.source,c.source));
  }
  long best=candidates.stream().filter(c->c.benefitWon!=null).mapToLong(Candidate::benefitWon).max().orElse(0);
  candidates=candidates.stream().map(c->new Candidate(c.cardId,c.name,c.benefitWon,c.performanceAfterWon,c.gapAfterWon,
   c.targetReached,c.benefitWon==null?null:best-c.benefitWon,c.uncertaintyReasons,c.source,c.ruleVersion)).collect(Collectors.toCollection(ArrayList::new));
  Comparator<Candidate> byBenefit=Comparator.comparing(Candidate::benefitWon,Comparator.nullsLast(Comparator.reverseOrder()))
    .thenComparing(Candidate::cardId);
  candidates.sort(byBenefit);
  List<Candidate> known=candidates.stream().filter(c->c.benefitWon!=null).toList();
  Candidate selected=known.isEmpty()?null:known.get(0);
  if(mode==Mode.PERFORMANCE && !over){
   selected=known.stream().filter(c->c.gapAfterWon!=null&&cards.stream().anyMatch(p->p.id.equals(c.cardId)&&p.targetWon!=null&&p.targetWon>0))
    .sorted(Comparator.comparing(Candidate::targetReached).reversed()
     .thenComparing(c->c.targetReached?c.opportunityCostWon:c.gapAfterWon)
     .thenComparing(byBenefit)).findFirst().orElse(selected);
  }
  if(mode==Mode.BALANCED&&!over){
   selected=known.stream().filter(c->c.targetReached&&c.opportunityCostWon<=tolerance)
    .sorted(Comparator.comparing(Candidate::opportunityCostWon).thenComparing(Candidate::cardId)).findFirst().orElse(selected);
  }
  if(!checkout.confirmedByUser||checkout.cardChargeWon==0) selected=null;
  return new Result(selected==null?"HELD":"VALID",selected==null?null:selected.cardId,
   checkout.expenseWon(),overall,category,available,over,List.copyOf(candidates),List.copyOf(warnings),Collections.unmodifiableMap(categoryAfter));
 }
}
