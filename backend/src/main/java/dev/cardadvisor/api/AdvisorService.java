package dev.cardadvisor.api;
import dev.cardadvisor.domain.RecommendationEngine;import dev.cardadvisor.domain.RecommendationEngine.*;
import dev.cardadvisor.domain.PerformanceCalculator;
import dev.cardadvisor.domain.BenefitCalculator;
import dev.cardadvisor.domain.ActualCardRules;
import com.fasterxml.jackson.databind.*;import java.util.*;import java.time.*;
import org.springframework.stereotype.Service;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.annotation.Transactional;
@Service @Transactional
public class AdvisorService {
 private final FixedCostService fixedCosts;private final JdbcTemplate db;private final ObjectMapper mapper;private final RecommendationEngine engine=new RecommendationEngine();
 private final dev.cardadvisor.account.CardCatalogService catalog;
 public AdvisorService(JdbcTemplate db,ObjectMapper mapper,FixedCostService fixedCosts,dev.cardadvisor.account.CardCatalogService catalog){this.catalog=catalog;this.db=db;this.mapper=mapper;this.fixedCosts=fixedCosts;}
 private String json(Object o){try{return mapper.writeValueAsString(o);}catch(Exception e){throw new IllegalArgumentException("INVALID_JSON");}}
 private <T>T decode(Object raw,Class<T> type){try{return mapper.readValue(raw.toString(),type);}catch(Exception e){throw new IllegalStateException("STORED_DATA_INVALID",e);}}
 private Map<String,Object> one(String sql,Object...args){var rows=db.queryForList(sql,args);if(rows.isEmpty())throw new ApiError(404,"RESOURCE_NOT_FOUND");return rows.get(0);}
 private long lock(UUID u){return ((Number)one("SELECT revision FROM app_users WHERE id=? FOR UPDATE",u).get("revision")).longValue();}
 private void changed(UUID u){db.update("UPDATE app_users SET revision=revision+1 WHERE id=?",u);}
 public Map<String,Object> deleteUser(UUID u){lock(u);
  long pending=db.queryForObject("SELECT count(*) FROM financial_connections WHERE user_id=? AND (state NOT IN ('DISCONNECTED','FAILED') OR cid_ciphertext IS NOT NULL)",Long.class,u);
  if(pending>0)throw new ApiError(409,"DISCONNECT_FINANCIAL_CONNECTIONS_FIRST");
  db.update("INSERT INTO deleted_user_tombstones(user_id) VALUES (?) ON CONFLICT DO NOTHING",u);
  db.update("DELETE FROM app_users WHERE id=?",u);return Map.of("localDataDeleted",true,"externalConnections","NONE_REMAINING");}
 private List<Card> profiles(UUID u){return db.queryForList("SELECT payload FROM cards WHERE user_id=? ORDER BY payload->>'name',id",u).stream().map(r->decode(r.get("payload"),Card.class)).toList();}
 private Card profile(UUID u,UUID id){return decode(one("SELECT payload FROM cards WHERE user_id=? AND id=?",u,id).get("payload"),Card.class);}
 private String currentMonth(){return YearMonth.now(ZoneId.of("Asia/Seoul")).toString();}
 public record MonthStateInput(Long untrackedPerformanceWon,Boolean currentQualified,Long remainingCapWon,Long version,
   String untrackedSource,LocalDate untrackedAsOf,String qualificationSource,LocalDate qualificationAsOf,String qualificationPeriod){
  public MonthStateInput(Long baseline,Boolean qualified,Long cap,long version){this(baseline,qualified,cap,version,null,null,null,null,null);}
 }
 private String evidenceSource(Object value,String source,LocalDate asOf,LocalDate earliest,LocalDate latest){
  if(value==null){if(!"UNKNOWN".equals(source)||asOf!=null)throw new ApiError(422,"UNKNOWN_REQUIRES_EMPTY_VALUE_AND_DATE");return source;}
  if(!Set.of("USER_INPUT","USER_ESTIMATE").contains(source==null?"":source))throw new ApiError(422,"MANUAL_SOURCE_REQUIRED");
  if(asOf==null||asOf.isBefore(earliest)||asOf.isAfter(latest)||asOf.isAfter(LocalDate.now(ZoneId.of("Asia/Seoul"))))throw new ApiError(422,"INVALID_EVIDENCE_DATE");
  return source;
 }
 public record CardPerformance(String id,String name,String performanceMonth,String qualificationPeriod,String nextBenefitMonth,
   Boolean currentQualified,Long untrackedPerformanceWon,Long ledgerPerformanceWon,Long performanceWon,Long targetWon,
   Long gapWon,Boolean nextMonthThresholdMet,Long remainingCapWon,long stateVersion,String performanceSource,String currentQualificationSource,String cancellationPolicy,String untrackedSource,String untrackedAsOf,String qualificationAsOf,Long baseRemainingCapWon,Long ledgerBenefitUsedWon,String benefitSource,String benefitRuleVersion){}
 public MonthStateInput saveMonthState(UUID u,UUID id,String m,MonthStateInput in){lock(u);Card cardProfile=profile(u,id);
  LocalDate from=month(m);String period=YearMonth.from(from).minusMonths(1).toString();
  if(in.version()==null||in.version()<0)throw new ApiError(422,"VERSION_REQUIRED");
  boolean legacy=in.untrackedSource()==null&&in.qualificationSource()==null&&in.untrackedAsOf()==null&&in.qualificationAsOf()==null&&in.qualificationPeriod()==null;
  String baselineSource,qualificationSource;
  if(legacy){baselineSource=in.untrackedPerformanceWon()==null?"UNKNOWN":"SYNTHETIC";qualificationSource=in.currentQualified()==null?"UNKNOWN":"SYNTHETIC";}
  else {
   if(!period.equals(in.qualificationPeriod()))throw new ApiError(422,"QUALIFICATION_PERIOD_MUST_BE_PREVIOUS_MONTH");
   baselineSource=evidenceSource(in.untrackedPerformanceWon(),in.untrackedSource(),in.untrackedAsOf(),from,from.plusMonths(1).minusDays(1));
   qualificationSource=evidenceSource(in.currentQualified(),in.qualificationSource(),in.qualificationAsOf(),from.minusMonths(1),from.plusMonths(1).minusDays(1));
  }
  if((in.untrackedPerformanceWon()!=null&&(in.untrackedPerformanceWon()<0||in.untrackedPerformanceWon()>100_000_000))||
     (in.remainingCapWon()!=null&&(in.remainingCapWon()<0||in.remainingCapWon()>100_000_000)))throw new ApiError(422,"INVALID_MONTH_STATE");
  var rows=db.queryForList("SELECT version FROM card_month_states WHERE user_id=? AND card_id=? AND month=?",u,id,month(m));
  long version=rows.isEmpty()?0:((Number)rows.get(0).get("version")).longValue();
  if(in.version()!=version)throw new ApiError(409,"VERSION_CONFLICT");
  db.update("INSERT INTO card_month_states(user_id,card_id,month,untracked_performance_won,current_qualified,remaining_cap_won,version,source,untracked_source,untracked_as_of,qualification_source,qualification_as_of,qualification_period) VALUES (?,?,?,?,?,?,?,'SYNTHETIC',?,?,?,?,?) ON CONFLICT(user_id,card_id,month) DO UPDATE SET untracked_performance_won=EXCLUDED.untracked_performance_won,current_qualified=EXCLUDED.current_qualified,remaining_cap_won=EXCLUDED.remaining_cap_won,version=EXCLUDED.version,untracked_source=EXCLUDED.untracked_source,untracked_as_of=EXCLUDED.untracked_as_of,qualification_source=EXCLUDED.qualification_source,qualification_as_of=EXCLUDED.qualification_as_of,qualification_period=EXCLUDED.qualification_period,updated_at=now()",u,id,from,in.untrackedPerformanceWon(),in.currentQualified(),in.remainingCapWon(),version+1,baselineSource,in.untrackedAsOf(),qualificationSource,in.qualificationAsOf(),from.minusMonths(1));
  if(ActualCardRules.actual(cardProfile.source()))db.update("UPDATE card_month_states SET source='USER_INPUT' WHERE user_id=? AND card_id=? AND month=?",u,id,from);
  changed(u);return new MonthStateInput(in.untrackedPerformanceWon(),in.currentQualified(),in.remainingCapWon(),version+1,baselineSource,in.untrackedAsOf(),qualificationSource,in.qualificationAsOf(),period);
 }
 public List<CardPerformance> performance(UUID u,String m){lock(u);LocalDate from=month(m);var out=new ArrayList<CardPerformance>();
  for(Card c:profiles(u)){
   UUID id=UUID.fromString(c.id());var rows=db.queryForList("SELECT * FROM card_month_states WHERE user_id=? AND card_id=? AND month=?",u,id,from);
   var state=rows.isEmpty()?Map.<String,Object>of():rows.get(0);
   if(ActualCardRules.actual(c.source())){out.add(catalog.performance(u,c,m,state));continue;}
   Long baseline=state.get("untracked_performance_won")==null?null:((Number)state.get("untracked_performance_won")).longValue();
   Long cap=state.get("remaining_cap_won")==null?null:((Number)state.get("remaining_cap_won")).longValue();
   var ledgerRows=db.queryForList("SELECT e.card_charge_won,e.performance_eligible,e.benefit_rate_bp,e.benefit_rule_version,CASE WHEN EXISTS(SELECT 1 FROM cancellations c WHERE c.user_id=e.user_id AND c.transaction_id=e.id AND c.card_refund_won IS NULL) THEN NULL ELSE COALESCE((SELECT sum(c.card_refund_won) FROM cancellations c WHERE c.user_id=e.user_id AND c.transaction_id=e.id),0) END AS refunded FROM ledger_entries e WHERE user_id=? AND card_id=? AND purchased_at>=? AND purchased_at<?",u,id,from,from.plusMonths(1));
   var movements=ledgerRows.stream().map(r->new PerformanceCalculator.Movement(((Number)r.get("card_charge_won")).longValue(),r.get("refunded")==null?null:((Number)r.get("refunded")).longValue(),(Boolean)r.get("performance_eligible"))).toList();
   var benefitMovements=ledgerRows.stream().map(r->new BenefitCalculator.Movement(((Number)r.get("card_charge_won")).longValue(),r.get("refunded")==null?null:((Number)r.get("refunded")).longValue(),r.get("benefit_rate_bp")==null?null:((Number)r.get("benefit_rate_bp")).intValue(),(String)r.get("benefit_rule_version"))).toList();
   var benefit=new BenefitCalculator().calculate(cap,(Boolean)state.get("current_qualified"),benefitMovements);
   var totals=new PerformanceCalculator().calculate(baseline,movements);Long gap=totals.totalWon()==null||c.targetWon()==null?null:Math.max(0,c.targetWon()-totals.totalWon());
   out.add(new CardPerformance(c.id(),c.name(),m,YearMonth.parse(m).minusMonths(1).toString(),YearMonth.parse(m).plusMonths(1).toString(),
    (Boolean)state.get("current_qualified"),baseline,totals.ledgerWon(),totals.totalWon(),c.targetWon(),gap,gap==null?null:gap==0,benefit.remainingCapWon(),
    state.get("version")==null?0:((Number)state.get("version")).longValue(),totals.totalWon()==null?"UNKNOWN":"SYNTHETIC_ESTIMATE",state.getOrDefault("qualification_source","UNKNOWN").toString(),"ORIGINAL_PURCHASE_MONTH",
    state.getOrDefault("untracked_source","UNKNOWN").toString(),state.get("untracked_as_of")==null?null:state.get("untracked_as_of").toString(),state.get("qualification_as_of")==null?null:state.get("qualification_as_of").toString(),cap,benefit.ledgerBenefitUsedWon(),benefit.remainingCapWon()==null||benefit.ledgerBenefitUsedWon()==null?"UNKNOWN":"SYNTHETIC_ESTIMATE",BenefitCalculator.RULE));
  }return out;
 }
 public Map<String,Object> dashboard(UUID u,String m){
  long revision=lock(u);var result=new LinkedHashMap<String,Object>();result.put("ledger",entries(u,m));result.put("performance",performance(u,m));
  result.put("budget",db.queryForObject("SELECT count(*) FROM budgets WHERE user_id=? AND month=?",Integer.class,u,month(m))==0?null:budgetSummary(u,m));result.put("revision",revision);result.put("userId",u.toString());return result;
 }
 public List<Card> cards(UUID u){var states=performance(u,currentMonth());var profiles=profiles(u);return profiles.stream().map(c->{var s=states.stream().filter(p->p.id().equals(c.id())).findFirst().orElseThrow();return new Card(c.id(),c.name(),c.rateBp(),s.currentQualified(),s.performanceWon(),c.targetWon(),s.remainingCapWon(),c.performanceEligible(),c.source());}).toList();}
 public Map<String,Object> demo(UUID u){lock(u);
  for(int i=0;i<2;i++){String key=i==0?"A":"B";String id=UUID.nameUUIDFromBytes((u+":"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
   if(db.queryForObject("SELECT count(*) FROM cards WHERE user_id=? AND id=?",Integer.class,u,UUID.fromString(id))==0)
    saveCard(u,new Card(id,"[가상] "+key+" 카드",i==0?300:100,true,i==0?300000L:270000L,300000L,10000L,true,"SYNTHETIC"));}
  String m=YearMonth.now(ZoneId.of("Asia/Seoul")).toString();
  if(db.queryForObject("SELECT count(*) FROM budgets WHERE user_id=? AND month=?",Integer.class,u,month(m))==0)
   saveBudget(u,m,new BudgetInput(120000L,Map.of("SHOPPING",70000L),30000L,0L));
  return Map.of("mode","SYNTHETIC","cards",cards(u),"budget",budget(u,m));
 }
 public Card saveCard(UUID u,Card c){lock(u);UUID id;try{id=UUID.fromString(c.id());}catch(Exception e){throw new ApiError(422,"INVALID_CARD_ID");}
  if(!"SYNTHETIC".equals(c.source()))throw new ApiError(422,"REAL_CARD_RULES_NOT_VERIFIED");
  if(!c.name().startsWith("[가상]"))throw new ApiError(422,"SYNTHETIC_LABEL_REQUIRED");
  var existing=db.queryForList("SELECT user_id FROM cards WHERE id=?",id);if(!existing.isEmpty()&&!u.equals(existing.get(0).get("user_id")))throw new ApiError(404,"RESOURCE_NOT_FOUND");
  if(!existing.isEmpty())throw new ApiError(409,"CARD_ALREADY_EXISTS_USE_MONTH_STATE");
  db.update("INSERT INTO cards(id,user_id,payload) VALUES (?,?,?::jsonb)",id,u,json(c));
  saveMonthState(u,id,currentMonth(),new MonthStateInput(c.performanceWon(),c.qualified(),c.remainingCapWon(),0));changed(u);return c;
 }
 private LocalDate month(String value){try{return YearMonth.parse(value).atDay(1);}catch(Exception e){throw new ApiError(422,"INVALID_MONTH");}}
 public record BudgetInput(Long totalWon,Map<String,Long> categories,Long fixedRemainingWon,Long version,String fixedCostMode) {
  public BudgetInput(Long total,Map<String,Long> categories,Long fixed,Long version){this(total,categories,fixed,version,null);}
 }
 private String fixedMode(BudgetInput b){return b==null||b.fixedCostMode()==null?"MANUAL":b.fixedCostMode();}
 private long fixedRemaining(UUID u,String m,BudgetInput b){return "SCHEDULED".equals(fixedMode(b))?fixedCosts.remaining(u,m):b.fixedRemainingWon();}
 public BudgetInput saveBudget(UUID u,String m,BudgetInput b){
  lock(u);LocalDate period=month(m);
  if(b.totalWon()==null||b.fixedRemainingWon()==null||b.version()==null||b.version()<0||b.categories()==null||
     b.totalWon()<0||b.totalWon()>100_000_000L||b.fixedRemainingWon()<0||b.fixedRemainingWon()>100_000_000L)
   throw new ApiError(422,"INVALID_BUDGET");
  var rows=db.queryForList("SELECT payload,version FROM budgets WHERE user_id=? AND month=?",u,period);
  BudgetInput old=rows.isEmpty()?null:decode(rows.get(0).get("payload"),BudgetInput.class);
  String mode=b.fixedCostMode()==null?fixedMode(old):b.fixedCostMode();
  if(!Set.of("MANUAL","SCHEDULED").contains(mode))throw new ApiError(422,"INVALID_FIXED_MODE");
  long sum=0;
  for(var entry:b.categories().entrySet()){
   String key=entry.getKey();Long amount=entry.getValue();
   if(key==null||(!EDIT_CATEGORIES.contains(key)&&(old==null||!old.categories().containsKey(key)))||
      amount==null||amount<0||amount>100_000_000L)throw new ApiError(422,"INVALID_BUDGET");
   sum=Math.addExact(sum,amount);
  }
  if(sum>b.totalWon())throw new ApiError(422,"INVALID_BUDGET");
  long current=rows.isEmpty()?0:((Number)rows.get(0).get("version")).longValue();
  if(current!=b.version())throw new ApiError(409,"VERSION_CONFLICT");
  if(old!=null&&old.totalWon().equals(b.totalWon())&&old.fixedRemainingWon().equals(b.fixedRemainingWon())&&old.categories().equals(b.categories())&&fixedMode(old).equals(mode))return old;
  var out=new BudgetInput(b.totalWon(),b.categories(),b.fixedRemainingWon(),current+1,mode);
  db.update("INSERT INTO budgets(user_id,month,payload,version) VALUES (?,?,?::jsonb,?) ON CONFLICT(user_id,month) DO UPDATE SET payload=EXCLUDED.payload,version=EXCLUDED.version",u,period,json(out),out.version());
  changed(u);return out;
 }
 public BudgetInput budget(UUID u,String m){return decode(one("SELECT payload FROM budgets WHERE user_id=? AND month=?",u,month(m)).get("payload"),BudgetInput.class);}
 public record EntryInput(UUID clientId,long amountWon,LocalDate purchasedAt,String category,boolean included,UUID cardId,Long cardChargeWon,ActualCardRules.Facts purchaseFacts) {public EntryInput(UUID client,long amount,LocalDate date,String category,boolean included,UUID card,Long charge){this(client,amount,date,category,included,card,charge,null);}}
 public Map<String,Object> addEntry(UUID u,EntryInput e){lock(u);if(e.clientId()==null||e.purchasedAt()==null||e.amountWon()<=0||e.amountWon()>10_000_000||e.category()==null||e.category().isBlank())throw new ApiError(422,"INVALID_TRANSACTION");
  if(e.cardId()==null||e.cardChargeWon()==null||e.cardChargeWon()<0||e.cardChargeWon()>e.amountWon())throw new ApiError(422,"CARD_AND_APPROVAL_REQUIRED");
  Card card=profile(u,e.cardId());
  if("SYNTHETIC".equals(card.source())&&e.purchaseFacts()!=null)throw new ApiError(422,"TEST_CARD_EXCLUDED_FROM_ACTUAL_PURCHASE");
  if(ActualCardRules.actual(card.source())&&!ActualCardRules.supported(e.purchaseFacts()))throw new ApiError(422,"ACTUAL_PURCHASE_FACTS_REQUIRED");
  var old=db.queryForList("SELECT * FROM ledger_entries WHERE user_id=? AND client_id=?",u,e.clientId());
  if(!old.isEmpty()){var r=old.get(0);if(ActualCardRules.actual(card.source())&&!mapper.valueToTree(e.purchaseFacts()).equals(mapper.valueToTree(decode(r.get("purchase_facts"),ActualCardRules.Facts.class))))throw new ApiError(409,"IDEMPOTENCY_CONFLICT");if(((Number)r.get("amount_won")).longValue()!=e.amountWon()||!r.get("original_category").equals(e.category())||!r.get("purchased_at").toString().equals(e.purchasedAt().toString())||!r.get("original_included").equals(e.included())||!Objects.equals(r.get("card_id"),e.cardId())||!Objects.equals(r.get("card_charge_won"),e.cardChargeWon()))throw new ApiError(409,"IDEMPOTENCY_CONFLICT");return r;}
  UUID id=UUID.randomUUID();db.update("INSERT INTO ledger_entries(id,user_id,client_id,amount_won,purchased_at,category,included,source,card_id,card_charge_won,performance_eligible,performance_rule_version,benefit_rate_bp,benefit_rule_version,original_category,original_included) VALUES (?,?,?,?,?,?,?,'MANUAL',?,?,?,'SYNTHETIC_V1',?,'SYNTHETIC_BENEFIT_V1',?,?)",id,u,e.clientId(),e.amountWon(),e.purchasedAt(),e.category(),e.included(),e.cardId(),e.cardChargeWon(),card.performanceEligible(),card.rateBp(),e.category(),e.included());
  if(ActualCardRules.actual(card.source()))db.update("UPDATE ledger_entries SET purchase_facts=?::jsonb,performance_eligible=?,performance_rule_version=?,benefit_rate_bp=?,benefit_rule_version=? WHERE user_id=? AND id=?",json(e.purchaseFacts()),ActualCardRules.eligible(e.purchaseFacts()),card.source(),ActualCardRules.eligible(e.purchaseFacts())?card.rateBp():0,card.source(),u,id);
  changed(u);return one("SELECT * FROM ledger_entries WHERE id=? AND user_id=?",id,u);
 }
 public List<Map<String,Object>> entries(UUID u,String m){LocalDate from=month(m);var result=db.queryForList("SELECT e.*,p.payload->>'name' AS card_name,t.id AS financial_transaction_id,t.merchant_name,CASE WHEN t.id IS NOT NULL THEN COALESCE(mapped.payload->>'name',t.card_label) ELSE p.payload->>'name' END AS display_card_name,CASE WHEN t.card_mask ~ '^\\*{13}[0-9]{3}$' THEN right(t.card_mask,3) WHEN t.card_mask ~ '^[0-9]{4}\\*{8}[0-9]{4}$' THEN right(t.card_mask,4) ELSE NULL END AS card_last_digits,t.status AS financial_status,t.source AS financial_source,e.amount_won-COALESCE((SELECT sum(c.amount_won) FROM cancellations c WHERE c.user_id=e.user_id AND c.transaction_id=e.id),0) AS net_won,CASE WHEN EXISTS(SELECT 1 FROM cancellations c WHERE c.user_id=e.user_id AND c.transaction_id=e.id AND c.card_refund_won IS NULL) THEN NULL ELSE e.card_charge_won-COALESCE((SELECT sum(c.card_refund_won) FROM cancellations c WHERE c.user_id=e.user_id AND c.transaction_id=e.id),0) END AS card_net_won FROM ledger_entries e LEFT JOIN cards p ON p.user_id=e.user_id AND p.id=e.card_id LEFT JOIN financial_transactions t ON t.user_id=e.user_id AND t.ledger_id=e.id AND t.kind='APPROVAL' LEFT JOIN financial_card_mappings fm ON fm.user_id=t.user_id AND fm.connection_id=t.connection_id AND fm.source_mask=t.card_mask AND fm.source_label=COALESCE(t.card_label,'') LEFT JOIN cards mapped ON mapped.user_id=fm.user_id AND mapped.id=fm.card_id WHERE e.user_id=? AND e.purchased_at>=? AND e.purchased_at<? ORDER BY e.purchased_at DESC,e.id",u,from,from.plusMonths(1));
  for(var row:result){row.put("allocations_won",originalAllocation(row));row.put("net_allocations_won",netAllocation(row));row.put("allocation_policy","PROPORTIONAL_REFUND_V1");row.remove("budget_allocations");}return result;}

 public record ClassificationInput(String category,Boolean included,Long version,Map<String,Long> allocationsWon){
  public ClassificationInput(String category,Boolean included,Long version){this(category,included,version,null);}
 }
 private Map<String,Long> originalAllocation(Map<String,Object> row){
  if(row.get("allocations_won") instanceof Map<?,?> value){var out=new TreeMap<String,Long>();value.forEach((k,v)->out.put(k.toString(),((Number)v).longValue()));return out;}
  if(row.get("budget_allocations")==null)return Map.of((String)row.get("category"),((Number)row.get("amount_won")).longValue());
  try{return mapper.readValue(row.get("budget_allocations").toString(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Long>>(){});}catch(Exception e){throw new IllegalStateException("STORED_ALLOCATION_INVALID");}
 }
 public Map<String,Long> netAllocation(Map<String,Object> row){return dev.cardadvisor.domain.BudgetAllocation.net(originalAllocation(row),((Number)row.get("amount_won")).longValue(),((Number)row.get("net_won")).longValue());}
 public List<Map<String,Object>> classificationHistory(UUID u,UUID id){entry(u,id);return db.queryForList("SELECT version,category,included,allocations::text AS allocations,changed_at FROM ledger_classification_history WHERE user_id=? AND transaction_id=? ORDER BY version DESC",u,id);}

 private static final Set<String> EDIT_CATEGORIES=dev.cardadvisor.domain.Categories.ALL;
 public Map<String,Object> entry(UUID u,UUID id){
  lock(u);var raw=one("SELECT purchased_at FROM ledger_entries WHERE user_id=? AND id=?",u,id);
  return entries(u,raw.get("purchased_at").toString().substring(0,7)).stream().filter(e->id.equals(e.get("id"))).findFirst().orElseThrow();
 }
 public Map<String,Object> classify(UUID u,UUID id,ClassificationInput in){
  lock(u);var old=one("SELECT * FROM ledger_entries WHERE user_id=? AND id=?",u,id);
  if(in.version()==null||in.version()<1||in.included()==null||in.category()==null||
     (!EDIT_CATEGORIES.contains(in.category())&&!in.category().equals(old.get("category"))))throw new ApiError(422,"INVALID_CLASSIFICATION");
  if(in.version()!=((Number)old.get("classification_version")).longValue())throw new ApiError(409,"VERSION_CONFLICT");
  long total=((Number)old.get("amount_won")).longValue();
  Map<String,Long> allocations=in.allocationsWon()==null?Map.of(in.category(),total):in.allocationsWon();
  if(allocations.size()>EDIT_CATEGORIES.size()||!EDIT_CATEGORIES.containsAll(allocations.keySet())||!allocations.containsKey(in.category()))throw new ApiError(422,"INVALID_ALLOCATION");
  try{dev.cardadvisor.domain.BudgetAllocation.net(allocations,total,total);}catch(RuntimeException e){throw new ApiError(422,"ALLOCATION_SUM_MISMATCH");}
  if(in.category().equals(old.get("category"))&&in.included().equals(old.get("included"))&&allocations.equals(originalAllocation(old)))return entry(u,id);
  db.update("INSERT INTO ledger_classification_history(user_id,transaction_id,version,category,included,allocations) VALUES (?,?,?,?,?,?::jsonb) ON CONFLICT DO NOTHING",u,id,in.version(),old.get("category"),old.get("included"),json(originalAllocation(old)));
  db.update("UPDATE ledger_entries SET category=?,included=?,budget_allocations=?::jsonb,classification_version=classification_version+1,classification_updated_at=now() WHERE user_id=? AND id=?",in.category(),in.included(),json(allocations),u,id);
  db.update("INSERT INTO ledger_classification_history(user_id,transaction_id,version,category,included,allocations) VALUES (?,?,?,?,?,?::jsonb)",u,id,in.version()+1,in.category(),in.included(),json(allocations));
  changed(u);return entry(u,id);
 }
 public record CategoryBalance(String category,Long limitWon,long spentWon,Long remainingWon){}
 public record BudgetSummary(String month,long totalWon,long spentWon,long remainingWon,long fixedRemainingWon,long availableWon,List<CategoryBalance> categories,long pendingFinancialTransactions){}
 private long pendingFinancial(UUID user){return db.queryForObject("SELECT count(*) FROM financial_transactions WHERE user_id=? AND status='HELD' AND issue<>'REJECTED_APPROVAL'",Long.class,user);}
 public BudgetSummary budgetSummary(UUID u,String m){
  lock(u);var budget=budget(u,m);var spent=new TreeMap<String,Long>();long totalSpent=0;
  for(var e:entries(u,m)){if(!Boolean.TRUE.equals(e.get("included")))continue;
   long net=((Number)e.get("net_won")).longValue();totalSpent=Math.addExact(totalSpent,net);
   netAllocation(e).forEach((category,part)->spent.merge(category,part,Math::addExact));
  }
  var keys=new TreeSet<String>(budget.categories().keySet());keys.addAll(spent.keySet());var categories=new ArrayList<CategoryBalance>();
  for(String key:keys){Long limit=budget.categories().get(key);long used=spent.getOrDefault(key,0L);categories.add(new CategoryBalance(key,limit,used,limit==null?null:Math.subtractExact(limit,used)));}
  long remaining=Math.subtractExact(budget.totalWon(),totalSpent);
  long fixed=fixedRemaining(u,m,budget);
  return new BudgetSummary(m,budget.totalWon(),totalSpent,remaining,fixed,Math.subtractExact(remaining,fixed),categories,pendingFinancial(u));
 }
 public record LinkCardInput(UUID cardId,long cardChargeWon){}
 public Map<String,Object> linkCard(UUID u,UUID id,LinkCardInput in){lock(u);var e=one("SELECT * FROM ledger_entries WHERE user_id=? AND id=?",u,id);
  requireManualManagement(u,id,e);
  if(in.cardId()==null||in.cardChargeWon()<0||in.cardChargeWon()>((Number)e.get("amount_won")).longValue())throw new ApiError(422,"INVALID_CARD_APPROVAL");
  Card card=profile(u,in.cardId());
  if(ActualCardRules.actual(card.source())||e.get("purchase_facts")!=null)throw new ApiError(409,"ACTUAL_CARD_RELINK_REQUIRES_FACTS_REVIEW");
  // Ambiguous historic refunds must be resolved before changing their original tender breakdown.
  long count=db.queryForObject("SELECT count(*) FROM cancellations WHERE user_id=? AND transaction_id=?",Long.class,u,id);
  if(count>0&&(e.get("card_charge_won")==null||((Number)e.get("card_charge_won")).longValue()!=in.cardChargeWon()))throw new ApiError(409,"REFUND_ALLOCATION_REQUIRES_REVIEW");
  if(Objects.equals(e.get("card_id"),in.cardId())&&Objects.equals(e.get("card_charge_won"),in.cardChargeWon()))return e;
  db.update("UPDATE ledger_entries SET card_id=?,card_charge_won=?,performance_eligible=?,performance_rule_version='SYNTHETIC_V1',benefit_rate_bp=?,benefit_rule_version='SYNTHETIC_BENEFIT_V1' WHERE user_id=? AND id=?",in.cardId(),in.cardChargeWon(),card.performanceEligible(),card.rateBp(),u,id);
  changed(u);return one("SELECT * FROM ledger_entries WHERE user_id=? AND id=?",u,id);
 }
 public record CancelInput(UUID clientId,long amountWon,Long cardRefundWon,LocalDate receivedAt){public CancelInput(UUID id,long amount,Long refund){this(id,amount,refund,null);}}
 public Map<String,Object> cancel(UUID u,UUID id,CancelInput c){lock(u);var entry=one("SELECT * FROM ledger_entries WHERE user_id=? AND id=?",u,id);
  requireManualManagement(u,id,entry);
  if(c.clientId()==null||c.amountWon()<=0)throw new ApiError(422,"INVALID_CANCEL");
  Long cardRefund=c.cardRefundWon();
  if(cardRefund==null&&entry.get("card_charge_won")!=null&&((Number)entry.get("card_charge_won")).longValue()==((Number)entry.get("amount_won")).longValue())cardRefund=c.amountWon();
  var old=db.queryForList("SELECT * FROM cancellations WHERE user_id=? AND client_id=?",u,c.clientId());if(!old.isEmpty()){var r=old.get(0);if(entry.get("purchase_facts")!=null&&c.receivedAt()!=null&&!c.receivedAt().toString().equals(r.get("received_at").toString()))throw new ApiError(409,"IDEMPOTENCY_CONFLICT");if(!r.get("transaction_id").equals(id)||((Number)r.get("amount_won")).longValue()!=c.amountWon()||!Objects.equals(r.get("card_refund_won"),cardRefund))throw new ApiError(409,"IDEMPOTENCY_CONFLICT");return r;}
  Long cancelled=db.queryForObject("SELECT COALESCE(sum(amount_won),0) FROM cancellations WHERE user_id=? AND transaction_id=?",Long.class,u,id);
  if(c.amountWon()>((Number)entry.get("amount_won")).longValue()-cancelled)throw new ApiError(422,"CANCEL_EXCEEDS_BALANCE");
  if(entry.get("card_id")!=null){
   if(cardRefund==null)throw new ApiError(422,"CARD_REFUND_REQUIRED_FOR_MIXED_TENDER");
   long previousCard=db.queryForObject("SELECT COALESCE(sum(card_refund_won),0) FROM cancellations WHERE user_id=? AND transaction_id=?",Long.class,u,id);
   long charge=((Number)entry.get("card_charge_won")).longValue(),amount=((Number)entry.get("amount_won")).longValue();
   if(cardRefund<0||cardRefund>c.amountWon()||cardRefund>charge-previousCard||c.amountWon()-cardRefund>(amount-charge)-(cancelled-previousCard))throw new ApiError(422,"INVALID_REFUND_ALLOCATION");
  }else if(cardRefund!=null)throw new ApiError(422,"LINK_CARD_BEFORE_CARD_REFUND");
  LocalDate receipt=c.receivedAt()==null?LocalDate.now(ZoneId.of("Asia/Seoul")):c.receivedAt();
  if(entry.get("purchase_facts")!=null&&(c.receivedAt()==null||receipt.isBefore(LocalDate.parse(entry.get("purchased_at").toString()))||receipt.isAfter(LocalDate.now(ZoneId.of("Asia/Seoul")))))throw new ApiError(422,"ACTUAL_REFUND_RECEIPT_DATE_REQUIRED");
  UUID cid=UUID.randomUUID();db.update("INSERT INTO cancellations(id,user_id,transaction_id,client_id,amount_won,card_refund_won) VALUES (?,?,?,?,?,?)",cid,u,id,c.clientId(),c.amountWon(),cardRefund);db.update("UPDATE cancellations SET received_at=? WHERE user_id=? AND id=?",receipt,u,cid);changed(u);return Map.of("id",cid,"cancelledWon",c.amountWon());
 }
 private void requireManualManagement(UUID user,UUID id,Map<String,Object> entry){
  if(entry.get("source").equals("CODEF")||db.queryForObject("SELECT count(*) FROM financial_transactions WHERE user_id=? AND ledger_id=?",Long.class,user,id)>0)throw new ApiError(409,"PROVIDER_TRANSACTION_MANAGED");
 }
 public static final String RULE_SET_VERSION="CALCULATION_20261022_REFUND_POLICY";
 public Map<String,Object> checkout(UUID u,Checkout c){lock(u);UUID id=UUID.randomUUID();db.update("INSERT INTO checkouts(id,user_id,payload,version,expires_at) VALUES (?,?,?::jsonb,1,now()+interval '2 minutes')",id,u,json(c));return Map.of("id",id,"snapshotVersion",1,"context",c);}
 public record UpdateCheckout(long expectedVersion,Checkout context){}
 public Map<String,Object> updateCheckout(UUID u,UUID id,UpdateCheckout input){lock(u);var old=one("SELECT version FROM checkouts WHERE user_id=? AND id=?",u,id);long v=((Number)old.get("version")).longValue();if(v!=input.expectedVersion())throw new ApiError(409,"VERSION_CONFLICT");
  db.update("UPDATE checkouts SET payload=?::jsonb,version=version+1,expires_at=now()+interval '2 minutes' WHERE user_id=? AND id=?",json(input.context()),u,id);return Map.of("id",id,"snapshotVersion",v+1);}
 public record RecommendInput(UUID checkoutId,long snapshotVersion,Mode mode,long maxOpportunityCostWon){}
 public Map<String,Object> recommend(UUID u,RecommendInput in){long revision=lock(u);var r=one("SELECT *,expires_at>now() AS valid FROM checkouts WHERE user_id=? AND id=?",u,in.checkoutId());
  if(pendingFinancial(u)>0)throw new ApiError(409,"FINANCIAL_REVIEW_REQUIRED");
  if(performance(u,currentMonth()).stream().anyMatch(p->"FINANCIAL_RULE_REVIEW_REQUIRED".equals(p.benefitSource())))throw new ApiError(409,"FINANCIAL_CARD_CALCULATION_REVIEW_REQUIRED");
  if(!Boolean.TRUE.equals(r.get("valid"))||((Number)r.get("version")).longValue()!=in.snapshotVersion())throw new ApiError(409,"STALE_CHECKOUT");
  Checkout c=decode(r.get("payload"),Checkout.class);String m=YearMonth.now(ZoneId.of("Asia/Seoul")).toString();BudgetInput b=budget(u,m);
  var ledger=entries(u,m);long spent=ledger.stream().filter(e->Boolean.TRUE.equals(e.get("included"))).mapToLong(e->((Number)e.get("net_won")).longValue()).sum();
  long categorySpent=ledger.stream().filter(e->Boolean.TRUE.equals(e.get("included"))).mapToLong(e->netAllocation(e).getOrDefault(c.category(),0L)).sum();
  var categoryBudgets=new TreeMap<String,CategoryBudget>();for(String category:c.allocationsWon().keySet()){long total=ledger.stream().filter(e->Boolean.TRUE.equals(e.get("included"))).mapToLong(e->netAllocation(e).getOrDefault(category,0L)).sum();categoryBudgets.put(category,new CategoryBudget(b.categories().get(category),total));}
  Result result=engine.evaluate(cards(u),c,new Budget(b.totalWon(),spent,b.categories().get(c.category()),categorySpent,fixedRemaining(u,m,b),categoryBudgets),Objects.requireNonNull(in.mode()),in.maxOpportunityCostWon());
  UUID id=UUID.randomUUID();db.update("INSERT INTO recommendations(id,user_id,checkout_id,user_revision,checkout_version,payload,expires_at,calculation_month,rule_set_version) VALUES (?,?,?,?,?,?::jsonb,?,?,?)",id,u,in.checkoutId(),revision,in.snapshotVersion(),json(result),r.get("expires_at"),month(m),RULE_SET_VERSION);
  return Map.of("id",id,"result",result,"basis",Map.of("userRevision",revision,"snapshotVersion",in.snapshotVersion(),"budgetVersion",b.version(),"ruleSetVersion",RULE_SET_VERSION),"expiresAt",r.get("expires_at"));
 }
 public Map<String,Object> recommendation(UUID u,UUID id){var r=one("SELECT r.*,c.version AS current_version,u.revision AS current_revision,r.expires_at>now() AS live FROM recommendations r JOIN checkouts c ON c.id=r.checkout_id AND c.user_id=r.user_id JOIN app_users u ON u.id=r.user_id WHERE r.user_id=? AND r.id=?",u,id);
  String validity=!Boolean.TRUE.equals(r.get("live"))?"EXPIRED":!RULE_SET_VERSION.equals(r.get("rule_set_version"))||!r.get("user_revision").equals(r.get("current_revision"))||!r.get("checkout_version").equals(r.get("current_version"))||r.get("calculation_month")==null||!r.get("calculation_month").toString().equals(month(currentMonth()).toString())?"INVALIDATED":"CURRENT";
  return Map.of("id",id,"validity",validity,"result",decode(r.get("payload"),Result.class));}
}
