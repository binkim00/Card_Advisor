package dev.cardadvisor.ai;
import java.util.*;
/** Local suggestions only. Merchant strings never leave this process for AI classification. */
public final class MerchantCategoryClassifier {
 public record Suggestion(String category,List<String> features,boolean ambiguous){}
 private static final Map<String,List<String>> WORDS=Map.of(
  "FOOD",List.of("커피","카페","식당","음식","치킨","피자","베이커리","coffee","cafe","스타벅스"),
  "TRANSPORT",List.of("택시","철도","지하철","고속버스","주차","교통","taxi"),
  "HOUSING",List.of("관리비","도시가스","전기요금","수도요금","임대료"),
  "HEALTH",List.of("병원","약국","치과","의원","의료"),
  "LEISURE",List.of("영화","시네마","미술관","박물관","볼링","수영","헬스"),
  "SHOPPING",List.of("무신사","네이버쇼핑","네이버플러스","백화점","마트","올리브영","문구","서점"));
 public static Suggestion classify(String raw){if(raw==null||raw.length()>512)return new Suggestion("OTHER",List.of(),false);String value=raw.toLowerCase(Locale.ROOT).replaceAll("[0-9]","");var hits=new TreeSet<String>();for(var category:WORDS.entrySet())if(category.getValue().stream().anyMatch(value::contains))hits.add(category.getKey());return new Suggestion(hits.size()==1?hits.first():"OTHER",hits.stream().map(c->"CATEGORY_HINT_"+c).toList(),hits.size()>1);}
}
