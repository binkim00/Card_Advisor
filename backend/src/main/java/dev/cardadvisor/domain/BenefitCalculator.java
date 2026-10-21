package dev.cardadvisor.domain;

import java.util.List;

/** Synthetic monthly estimate only: round each net approval down, sum, then cap.
 * Cancellations restore the original purchase month's pool, recomputing the whole month.
 * This deliberately makes no claim about issuer settlement or refund policies. */
public final class BenefitCalculator {
 public static final String RULE="SYNTHETIC_BENEFIT_V1";
 public record Movement(long cardChargeWon,Long cardRefundWon,Integer rateBp,String ruleVersion){
  public Movement {
   if(cardChargeWon<0||(cardRefundWon!=null&&(cardRefundWon<0||cardRefundWon>cardChargeWon))||
      (rateBp!=null&&(rateBp<0||rateBp>10000)))throw new IllegalArgumentException("INVALID_BENEFIT_MOVEMENT");
  }
 }
 public record Totals(Long ledgerBenefitUsedWon,Long remainingCapWon){}
 public Totals calculate(Long baseRemainingCapWon,Boolean currentQualified,List<Movement> movements){
  if(baseRemainingCapWon!=null&&baseRemainingCapWon<0)throw new IllegalArgumentException("INVALID_BASE_CAP");
  if(Boolean.FALSE.equals(currentQualified)||Long.valueOf(0).equals(baseRemainingCapWon))return new Totals(0L,baseRemainingCapWon);
  long sum=0;boolean unknown=false;
  for(var m:movements){
   if(m.cardChargeWon()==0)continue;
   if(m.cardRefundWon()!=null&&m.cardChargeWon()==m.cardRefundWon())continue;
   if(!RULE.equals(m.ruleVersion())||m.rateBp()==null){unknown=true;continue;}
   if(m.rateBp()==0)continue;
   if(currentQualified==null||m.cardRefundWon()==null){unknown=true;continue;}
   long net=Math.subtractExact(m.cardChargeWon(),m.cardRefundWon());
   sum=Math.addExact(sum,Math.multiplyExact(net,(long)m.rateBp())/10000);
  }
  if(unknown)return new Totals(null,null);
  if(sum==0)return new Totals(0L,baseRemainingCapWon);
  if(baseRemainingCapWon==null)return new Totals(null,null);
  long used=Math.min(baseRemainingCapWon,sum);
  return new Totals(used,baseRemainingCapWon-used);
 }
}
