package dev.cardadvisor.ai;
import dev.cardadvisor.api.*;import dev.cardadvisor.account.UserSettingsService;import dev.cardadvisor.auth.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;import java.time.*;

@Service
public class AiAssistanceService {
 private final OpenAiGateway gateway;private final UserSettingsService settings;private final AdvisorService advisor;private final JdbcTemplate db;private final RequestLimits limits;private final ObjectMapper json;
 public AiAssistanceService(OpenAiGateway gateway,UserSettingsService settings,AdvisorService advisor,JdbcTemplate db,RequestLimits limits,ObjectMapper json){this.gateway=gateway;this.settings=settings;this.advisor=advisor;this.db=db;this.limits=limits;this.json=json;}
 public record Fact(String id,String text,Map<String,Object> values){}
 public record Assistance(String source,String status,List<String> paragraphs,List<Fact> facts){}
 private long revision(UUID user){return db.queryForObject("SELECT revision FROM app_users WHERE id=?",Long.class,user);}
 private Assistance choose(UUID u,String task,List<Fact> facts){
  List<Fact> fallback=facts.stream().limit(3).toList();String status=!settings.allowed(u,"AI")?"CONSENT_REQUIRED":!gateway.ready()?"AI_NOT_CONFIGURED":"READY";
  if(!"READY".equals(status))return new Assistance("DETERMINISTIC_TEMPLATE",status,fallback.stream().map(Fact::text).toList(),facts);
  if(!limits.take("ai:"+u,20,86400))return new Assistance("DETERMINISTIC_TEMPLATE","LOCAL_QUOTA",fallback.stream().map(Fact::text).toList(),facts);
  long basis=revision(u);
  // Construct a fresh whitelist-only request: no user/transaction IDs, merchant names or free-text fields.
  var clean=facts.stream().map(f->Map.of("id",f.id(),"values",f.values())).toList();
  try{
   if(!settings.allowed(u,"AI"))throw new IllegalStateException("CONSENT_REVOKED");
   var selected=gateway.select(task,json.valueToTree(clean),facts.stream().map(Fact::id).toList());
   List<String> ids=validateSelection(selected,facts.stream().map(Fact::id).toList());
   if(!settings.allowed(u,"AI")||revision(u)!=basis)throw new IllegalStateException("DATA_CHANGED");
   var output=ids.stream().map(id->facts.stream().filter(f->f.id().equals(id)).findFirst().orElseThrow().text()).toList();
   return new Assistance("OPENAI_VALIDATED","READY",output,facts);
  }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();return new Assistance("DETERMINISTIC_TEMPLATE","AI_UNAVAILABLE_OR_REJECTED",fallback.stream().map(Fact::text).toList(),facts);}
 }
 static List<String> validateSelection(JsonNode output,List<String> allowed){
  if(!output.isObject()||output.size()!=1||!output.path("ids").isArray()||output.path("ids").isEmpty()||output.path("ids").size()>3)throw new IllegalArgumentException("INVALID_AI_SELECTION");
  var ids=new ArrayList<String>();for(var id:output.path("ids")){if(!id.isTextual()||!allowed.contains(id.asText())||ids.contains(id.asText()))throw new IllegalArgumentException("INVALID_AI_FACT");ids.add(id.asText());}return ids;
 }
 public Assistance explain(UUID user,UUID recommendation){
  var r=advisor.recommendation(user,recommendation);if(!"CURRENT".equals(r.get("validity")))throw new ApiError(409,"RECOMMENDATION_INVALIDATED");
  var result=(dev.cardadvisor.domain.RecommendationEngine.Result)r.get("result");var facts=new ArrayList<Fact>();
  facts.add(new Fact("BUDGET_AFTER","이번 구매 후 전체 예산은 "+money(result.overallAfterWon())+"입니다.",Map.of("remainingWon",result.overallAfterWon())));
  facts.add(new Fact("FIXED_AFTER","미반영 고정비까지 고려한 잔액은 "+money(result.availableAfterWon())+"입니다.",Map.of("availableWon",result.availableAfterWon())));
  if(result.recommendedCardId()!=null){var c=result.candidates().stream().filter(v->v.cardId().equals(result.recommendedCardId())).findFirst().orElseThrow();
   if(c.benefitWon()!=null)facts.add(new Fact("BENEFIT","추천 카드의 이번 결제 예상 혜택은 "+money(c.benefitWon())+"입니다. 실제 카드사 확정값이 아닙니다.",Map.of("benefitWon",c.benefitWon())));
   if(c.targetReached())facts.add(new Fact("TARGET","입력 기준으로 다음 달용 실적 기준을 채울 수 있습니다. 이번 혜택 자격을 올리거나 다음 달 혜택을 보장하지 않습니다.",Map.of("targetReached",true)));
  }else facts.add(new Fact("HELD","미확인 조건이 있어 카드 추천을 보류합니다. 입력과 출처를 확인해 주세요.",Map.of("held",true)));
  return choose(user,"EXPLAIN_RECOMMENDATION",facts);
 }
 public record Comparison(String month,int throughDay,String previousMonth,int previousThroughDay,long knownCurrentWon,long knownPreviousWon,Long differenceWon,String status){}
 public record Analysis(String month,AdvisorService.BudgetSummary summary,Assistance assistance,AdvisorService.BudgetInput proposedBudget,String proposalStatus,Comparison comparison){}
 private Comparison comparison(UUID user,String m){var current=YearMonth.parse(m);var today=LocalDate.now(ZoneId.of("Asia/Seoul"));int through=current.equals(YearMonth.from(today))?today.getDayOfMonth():current.lengthOfMonth();var previous=current.minusMonths(1);int priorThrough=Math.min(through,previous.lengthOfMonth());long now=0,before=0;
  for(var entry:advisor.entries(user,m))if(Boolean.TRUE.equals(entry.get("included"))&&LocalDate.parse(entry.get("purchased_at").toString()).getDayOfMonth()<=through)now=Math.addExact(now,((Number)entry.get("net_won")).longValue());
  for(var entry:advisor.entries(user,previous.toString()))if(Boolean.TRUE.equals(entry.get("included"))&&LocalDate.parse(entry.get("purchased_at").toString()).getDayOfMonth()<=priorThrough)before=Math.addExact(before,((Number)entry.get("net_won")).longValue());
  long held=db.queryForObject("SELECT count(*) FROM financial_transactions WHERE user_id=? AND status='HELD' AND (used_date IS NULL OR (used_date>=? AND used_date<?))",Long.class,user,previous.atDay(1),current.atDay(1).plusDays(through));return new Comparison(m,through,previous.toString(),priorThrough,now,before,held>0?null:Math.subtractExact(now,before),held>0?"PARTIAL_KNOWN_TRANSACTIONS":"RECORDED_TRANSACTIONS_ONLY");
 }
 public Analysis analyze(UUID user,String month){
  var summary=advisor.budgetSummary(user,month);var budget=advisor.budget(user,month);var facts=new ArrayList<Fact>();
  facts.add(new Fact("TOTAL","이번 달 예산에 포함된 순지출은 "+money(summary.spentWon())+"입니다.",Map.of("spentWon",summary.spentWon(),"totalWon",summary.totalWon())));
  facts.add(new Fact("AVAILABLE","고정비를 고려한 남은 예산은 "+money(summary.availableWon())+"입니다.",Map.of("availableWon",summary.availableWon())));
  for(var c:summary.categories())if(c.remainingWon()!=null&&c.remainingWon()<0)facts.add(new Fact("OVER_"+safeCategory(c.category()),safeLabel(c.category())+" 예산을 "+money(-c.remainingWon())+" 초과했습니다.",Map.of("category",safeCategory(c.category()),"overWon",-c.remainingWon())));
  var comparison=comparison(user,month);facts.add(new Fact("PERIOD_COMPARE","조회 월 "+comparison.throughDay()+"일까지 기록된 지출은 "+money(comparison.knownCurrentWon())+", 이전 월 "+comparison.previousThroughDay()+"일까지는 "+money(comparison.knownPreviousWon())+"입니다. 누락된 거래가 없는 공식 합계가 아닙니다.",Map.of("knownCurrentWon",comparison.knownCurrentWon(),"knownPreviousWon",comparison.knownPreviousWon(),"currentThroughDay",comparison.throughDay(),"previousThroughDay",comparison.previousThroughDay())));
  // Propose only unused total allocation, never cut unknown essential spending or change a total automatically.
  var proposed=new TreeMap<>(budget.categories());long unallocated=budget.totalWon()-proposed.values().stream().mapToLong(Long::longValue).sum();boolean changed=false;
  for(var c:summary.categories())if(Set.of("SHOPPING","LEISURE").contains(c.category())&&c.remainingWon()!=null&&c.remainingWon()<0&&unallocated>0){long add=Math.min(unallocated,-c.remainingWon());proposed.put(c.category(),Math.addExact(proposed.get(c.category()),add));unallocated-=add;changed|=add>0;}
  var proposal=changed?new AdvisorService.BudgetInput(budget.totalWon(),proposed,budget.fixedRemainingWon(),budget.version(),budget.fixedCostMode()):null;
  if(changed)facts.add(new Fact("ALLOCATION","전체 예산과 필수 지출 한도를 유지하면서 미배정액을 쇼핑·여가 초과분에 배분하는 안이 있습니다. 확인 전에는 저장하지 않습니다.",Map.of("totalUnchanged",true)));
  return new Analysis(month,summary,choose(user,"SPENDING_ANALYSIS",facts),proposal,changed?"USER_CONFIRMATION_REQUIRED":"NO_SAFE_CHANGE",comparison);
 }
 public Map<String,Object> classify(UUID user,UUID id){
  var entry=advisor.entry(user,id);String current=(String)entry.get("category");boolean corrected=((Number)entry.get("classification_version")).longValue()>1;
  var merchants=db.queryForList("SELECT merchant_name FROM financial_transactions WHERE user_id=? AND ledger_id=? AND kind='APPROVAL' AND status='APPLIED'",user,id);var local=MerchantCategoryClassifier.classify(merchants.size()==1?(String)merchants.get(0).get("merchant_name"):null);String suggested=entry.get("purchase_facts")!=null?"SHOPPING":local.category();
  var facts=List.of(new Fact("KEEP_USER","사용자가 수정한 분류를 우선합니다.",Map.of("category",safeCategory(current))),new Fact("SHOPPING","확인한 쇼핑몰 구매는 쇼핑 분류 후보입니다.",Map.of("category","SHOPPING")),new Fact("UNKNOWN","정보가 부족한 거래는 기타로 남겨 확인합니다.",Map.of("category","OTHER")));
  // Human corrections are final until the human edits them again. AI never writes classification.
  var suggestionFact=new Fact("LOCAL_CATEGORY","가맹점의 분류 단서를 이용한 "+safeLabel(suggested)+" 후보입니다. 확인 후 저장해야 하며 카드 혜택 업종을 뜻하지 않습니다.",Map.of("category",safeCategory(suggested),"features",local.features(),"ambiguous",local.ambiguous()));
  var assistance=corrected?new Assistance("USER_CORRECTION","KEPT",List.of("사용자가 수정한 분류를 유지합니다."),facts):choose(user,"CLASSIFICATION",List.of(suggestionFact));
  return Map.of("category",corrected?current:suggested,"status","SUGGESTION_ONLY","classificationVersion",entry.get("classification_version"),"confidence","NOT_CALIBRATED","assistance",assistance);
 }
 private static String money(long n){return String.format(Locale.KOREA,"%,d원",n);}
 private static String safeCategory(String c){return Set.of("SHOPPING","FOOD","TRANSPORT","HOUSING","HEALTH","LEISURE","OTHER").contains(c)?c:"OTHER";}
 private static String safeLabel(String c){return Map.of("SHOPPING","쇼핑","FOOD","식비","TRANSPORT","교통","HOUSING","주거","HEALTH","의료","LEISURE","여가","OTHER","기타").getOrDefault(c,"기타");}
}
