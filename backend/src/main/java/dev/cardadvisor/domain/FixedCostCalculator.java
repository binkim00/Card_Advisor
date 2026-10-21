package dev.cardadvisor.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public final class FixedCostCalculator {
 public record Payment(long amountWon,long refundedWon,boolean included){}
 public record Balance(long linkedNetWon,long includedNetWon,long remainingWon){}
 public LocalDate dueDate(YearMonth month,int day){
  if(day<1||day>31)throw new IllegalArgumentException("INVALID_DUE_DAY");
  return month.atDay(Math.min(day,month.lengthOfMonth()));
 }
 public Balance calculate(long expectedWon,boolean active,List<Payment> payments){
  if(expectedWon<0)throw new IllegalArgumentException("INVALID_FIXED_AMOUNT");
  long net=0,included=0;
  for(var p:payments){
   if(p.amountWon()<0||p.refundedWon()<0||p.refundedWon()>p.amountWon())throw new IllegalArgumentException("INVALID_FIXED_PAYMENT");
   long amount=Math.subtractExact(p.amountWon(),p.refundedWon());net=Math.addExact(net,amount);
   if(p.included())included=Math.addExact(included,amount);
  }
  return new Balance(net,included,active?Math.max(0,Math.subtractExact(expectedWon,included)):0);
 }
}
