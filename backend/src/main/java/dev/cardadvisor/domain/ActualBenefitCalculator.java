package dev.cardadvisor.domain;
import java.util.*;

/** Bounds avoid inventing issuer rounding. AUTO selection is by spend, not by benefit. */
public final class ActualBenefitCalculator {
 public static final String VERSION="FINANCIAL_BENEFIT_20261020";
 public record Area(String id,String label,int group,int rateBp,long minimumWon){}
 public static final List<Area> AREAS=List.of(
  new Area("FOOD","음식",0,300,10000),new Area("EDUCATION","학원·독서실",0,300,0),new Area("MEDICAL","병원·약국",0,300,0),new Area("FOREIGN_DUTYFREE","해외·면세",0,300,0),new Area("UTILITIES","관리비·공과금",0,300,0),
  new Area("MART","지정 마트",1,500,0),new Area("FUEL","SK·GS 주유",1,500,0),new Area("TELECOM","지정 통신 자동이체",1,500,30000),new Area("ONLINE","지정 온라인 쇼핑",1,500,0),new Area("SOCIAL","지정 소셜커머스",1,500,0),
  new Area("SPORTS","스포츠",2,700,0),new Area("TRANSIT","버스·지하철",2,700,0),new Area("FASHION","지정 패션·인테리어",2,700,0),new Area("BEAUTY","뷰티",2,700,0),new Area("CONVENIENCE","CU·GS25",2,700,0),
  new Area("TAXI","택시",3,1000,0),new Area("COFFEE","커피",3,1000,0),new Area("BAKERY","제과·패스트푸드",3,1000,0),new Area("DELIVERY","지정 배달앱",3,1000,0),new Area("SUBSCRIPTION","지정 구독·영화",3,1000,0));
 public record Purchase(String area,long originalWon,long netWon){}
 public record Balance(String area,String label,int group,long netWon,long lowerBenefitWon,long upperBenefitWon,boolean selected,long capWon){}
 public record Result(Long usedWon,Long remainingWon,long lowerWon,long upperWon,long capWon,List<Balance> areas){}
 public static Result kb(int tier,List<Purchase> rows){
  if(tier<0||tier>3)throw new IllegalArgumentException("INVALID_BENEFIT_TIER");
  var balances=new ArrayList<Balance>();
  for(var area:AREAS){long net=0,low=0,high=0;for(var p:rows){if(!area.id.equals(p.area))continue;if(p.netWon<0||p.netWon>p.originalWon)throw new IllegalArgumentException("INVALID_BENEFIT_NET");if(p.originalWon<area.minimumWon)continue;
    net=Math.addExact(net,p.netWon);long numerator=Math.multiplyExact(p.netWon,area.rateBp);low=Math.addExact(low,numerator/10000);high=Math.addExact(high,Math.addExact(numerator,9999)/10000);
   }balances.add(new Balance(area.id,area.label,area.group,net,Math.min(low,4000),Math.min(high,4000),false,4000));}
  var selected=new HashSet<String>();for(int g=0;g<4;g++){final int group=g;balances.stream().filter(a->a.group==group).sorted(Comparator.comparingLong(Balance::netWon).reversed().thenComparing(Balance::area)).limit(tier).forEach(a->selected.add(a.area));}
  var out=balances.stream().map(a->new Balance(a.area,a.label,a.group,a.netWon,a.lowerBenefitWon,a.upperBenefitWon,selected.contains(a.area),a.capWon)).toList();long low=0,high=0;
  for(int group=0;group<4;group++){final int g=group;var ranked=out.stream().filter(a->a.group==g).sorted(Comparator.comparingLong(Balance::netWon).reversed().thenComparing(Balance::area)).toList();if(tier==0)continue;
   long cut=ranked.get(tier-1).netWon;int above=(int)ranked.stream().filter(a->a.netWon>cut).count();int slots=tier-above;
   for(var a:ranked)if(a.netWon>cut){low=Math.addExact(low,a.lowerBenefitWon);high=Math.addExact(high,a.upperBenefitWon);}
   var tied=ranked.stream().filter(a->a.netWon==cut).toList();low=Math.addExact(low,tied.stream().mapToLong(Balance::lowerBenefitWon).sorted().limit(slots).sum());high=Math.addExact(high,tied.stream().mapToLong(Balance::upperBenefitWon).boxed().sorted(Comparator.reverseOrder()).limit(slots).mapToLong(Long::longValue).sum());
  }
  long cap=Math.multiplyExact(tier,16000L);return new Result(low==high?low:null,low==high?cap-low:null,low,high,cap,out);
 }
 // Official bank-account TravelGO: two shared 5,000-won groups, not five separate caps.
 public static final List<Area> GO_AREAS=List.of(
  new Area("GO_CONVENIENCE","편의점 · 지정 오프라인 매장",0,500,10000),
  new Area("GO_COFFEE","커피·베이커리 · 지정 오프라인 매장",0,500,10000),
  new Area("GO_TRANSIT","버스·지하철 · 월 합계 5만원 이상",1,500,0),
  new Area("GO_DIGITAL","지정 디지털 콘텐츠 정기결제",1,500,7000),
  new Area("GO_UTILITIES","지정 통신·전기·가스 자동이체",1,500,50000));
 public static Result go(int tier,List<Purchase> rows){
  if(tier<0||tier>2)throw new IllegalArgumentException("INVALID_GO_TIER");
  var balances=new ArrayList<Balance>();long[] lows=new long[2],highs=new long[2];
  for(var area:GO_AREAS){var values=new ArrayList<Long>();long net=0;
   for(var p:rows){if(p.netWon<0||p.netWon>p.originalWon)throw new IllegalArgumentException("INVALID_BENEFIT_NET");if(!area.id.equals(p.area)||p.netWon==0||p.netWon<area.minimumWon)continue;net=Math.addExact(net,p.netWon);values.add(Math.addExact(Math.multiplyExact(p.netWon,500L),5000)/10000);}
   boolean eligible=tier>area.group&&(!area.id.equals("GO_TRANSIT")||net>=50000);
   // Terms do not identify which five receipts win when more than five qualify; preserve bounds.
   long low=eligible?values.stream().sorted().limit(5).mapToLong(Long::longValue).sum():0;
   long high=eligible?values.stream().sorted(Comparator.reverseOrder()).limit(5).mapToLong(Long::longValue).sum():0;
   lows[area.group]=Math.addExact(lows[area.group],low);highs[area.group]=Math.addExact(highs[area.group],high);
   balances.add(new Balance(area.id,area.label,area.group,net,low,high,eligible,5000));
  }
  long low=Math.min(lows[0],5000)+Math.min(lows[1],5000),high=Math.min(highs[0],5000)+Math.min(highs[1],5000),cap=tier*5000L;
  return new Result(low==high?low:null,low==high?cap-low:null,low,high,cap,balances);
 }
}
