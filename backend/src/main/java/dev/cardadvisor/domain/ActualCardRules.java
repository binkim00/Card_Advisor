package dev.cardadvisor.domain;
import java.util.*;

/** Reviewed domestic scope only. Amounts remain estimates until the issuer posts them. */
public final class ActualCardRules {
 public static final String KB="OFFICIAL_KB_EASY_ALL_AUTO_20261019";
 public static final String LOG="OFFICIAL_HANA_TRAVELOG_20240308";
 public static final String GO="OFFICIAL_HANA_TRAVELGO_BANK_20250115";
 public record Facts(String merchant,String purchaseKind,Boolean confirmed){
  public Facts{if(merchant==null||purchaseKind==null)throw new IllegalArgumentException("PURCHASE_FACTS_REQUIRED");}
 }
 public record Product(String id,String name,String ruleVersion,String sourceUrl,long targetWon,int rateBp,boolean noPerformanceRequirement,String scope){}
 public static final List<Product> PRODUCTS=List.of(
  new Product("KB_EASY_ALL_AUTO","KB국민 Easy all 티타늄 · AUTO",KB,"https://card.kbcard.com/CRD/DVIEW/HCAMCXPRICAC0076?cooperationcode=09256&mainCC=a",500000,0,false,"네이버·무신사 일반 상품: 선택서비스 대상 아님. 다른 AUTO 영역은 현재 추천 범위 밖"),
  new Product("HANA_TRAVELOG","하나카드 트래블로그 체크카드",LOG,"https://m.hanacard.co.kr/leaflet/13/13889_20240308.pdf",0,30,true,"국내 일반 일시불 0.3%, 건별 반올림, 한도·전월 실적 조건 없음"),
  new Product("HANA_TRAVELGO_BANK","하나 트래블GO 체크카드 · 계좌 출금형",GO,"https://m.hanacard.co.kr/leaflet/15/15252_20250115.pdf",200000,0,false,"네이버·무신사 일반 상품: 5개 적립 영역 대상 아님. 기타 영역은 현재 추천 범위 밖"));
 public static Optional<Product> product(String source){return PRODUCTS.stream().filter(p->p.ruleVersion.equals(source)).findFirst();}
 public static boolean actual(String source){return product(source).isPresent();}
 public static boolean supported(Facts facts){return facts!=null&&Boolean.TRUE.equals(facts.confirmed)&&Set.of("NAVER_PLUS_STORE","MUSINSA").contains(facts.merchant)&&Set.of("GENERAL_GOODS","GIFT_CARD","PREPAID_TOPUP","TAX","PUBLIC_UTILITY","EDUCATION","INSURANCE").contains(facts.purchaseKind);}
 public static boolean eligible(Facts facts){return supported(facts)&&"GENERAL_GOODS".equals(facts.purchaseKind);}
 public static long benefit(String source,long charge,Facts facts){
  if(!actual(source)||!supported(facts)||charge<0)throw new IllegalArgumentException("ACTUAL_RULE_SCOPE_UNCONFIRMED");
  return LOG.equals(source)&&eligible(facts)?roundHana(charge):0;
 }
 public static long roundHana(long charge){return Math.addExact(Math.multiplyExact(charge,30),5000)/10000;}
}
