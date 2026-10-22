package dev.cardadvisor.domain;
import java.time.*;
/** Official thresholds and registration grace; an unknown previous month is never zero. */
public final class ActualQualification {
 public static final String VERSION="ACTUAL_QUALIFICATION_20261022";
 public record Result(Boolean qualified,Integer tier,int minimumTier,boolean registrationGrace,LocalDate graceThrough,String performancePeriod,String benefitMonth){}
 public static Result calculate(String rule,YearMonth month,Long previousWon,LocalDate firstUseDate){
  if(!ActualCardRules.actual(rule))throw new IllegalArgumentException("ACTUAL_CARD_REQUIRED");
  if(previousWon!=null&&(previousWon< -100_000_000L||previousWon>100_000_000L))throw new IllegalArgumentException("INVALID_PERFORMANCE");
  LocalDate through=firstUseDate==null?null:YearMonth.from(firstUseDate).plusMonths(1).atEndOfMonth();
  boolean grace=firstUseDate!=null&&!month.atEndOfMonth().isBefore(firstUseDate)&&!month.atDay(1).isAfter(through);
  if(ActualCardRules.LOG.equals(rule))return new Result(true,0,0,false,null,month.minusMonths(1).toString(),month.toString());
  Integer tier=previousWon==null?null:ActualCardRules.KB.equals(rule)?(previousWon>=1500000?3:previousWon>=1000000?2:previousWon>=500000?1:0):(previousWon>=500000?2:previousWon>=200000?1:0);
  int minimum=grace?1:0;if(tier!=null)tier=Math.max(tier,minimum);
  return new Result(tier==null?(grace?Boolean.TRUE:null):Boolean.valueOf(tier>0),tier,tier==null?minimum:tier,grace,through,month.minusMonths(1).toString(),month.toString());
 }
}
