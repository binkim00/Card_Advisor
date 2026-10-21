package dev.cardadvisor.domain;
import java.util.List;
/** SYNTHETIC_V1: one-time card approval minus linked card refunds, attributed to purchase month. */
public final class PerformanceCalculator {
 public record Movement(long cardChargeWon, Long cardRefundWon, Boolean eligible) {
  public Movement {
   if(cardChargeWon<0 || (cardRefundWon!=null&&(cardRefundWon<0||cardRefundWon>cardChargeWon)))
    throw new IllegalArgumentException("INVALID_CARD_MOVEMENT");
  }
 }
 public record Totals(Long ledgerWon, Long totalWon) {}
 public Totals calculate(Long untrackedWon,List<Movement> movements) {
  if(untrackedWon!=null&&untrackedWon<0)throw new IllegalArgumentException("INVALID_BASELINE");
  long known=0;boolean unknown=false;
  for(var m:movements){
   if(Boolean.FALSE.equals(m.eligible()))continue;
   if(m.cardRefundWon()==null){unknown=true;continue;}
   long net=Math.subtractExact(m.cardChargeWon(),m.cardRefundWon());
   if(net==0)continue;
   if(m.eligible()==null){unknown=true;continue;}
   known=Math.addExact(known,net);
  }
  Long ledger=unknown?null:known;
  return new Totals(ledger,ledger==null||untrackedWon==null?null:Math.addExact(untrackedWon,ledger));
 }
}
