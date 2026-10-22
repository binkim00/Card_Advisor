package dev.cardadvisor.finance;
import dev.cardadvisor.api.ApiError;
import dev.cardadvisor.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;

@Service @Transactional
public class FinancialBenefitService {
 private final JdbcTemplate db;private final FinancialPerformanceService performance;
 public FinancialBenefitService(JdbcTemplate db,FinancialPerformanceService p){this.db=db;performance=p;}
 public record Input(Long expectedVersion,Long expectedTransactionVersion,Long expectedMappingVersion,String decision,String area,Boolean conditionsConfirmed){}
 public record MonthInput(Long expectedVersion,Long expectedQualificationVersion,Integer tier,Boolean coverageConfirmed){}
 private long n(Object v){return ((Number)v).longValue();}
 private void lock(UUID u){if(db.queryForList("SELECT id FROM app_users WHERE id=? FOR UPDATE",u).isEmpty())throw new ApiError(401,"SESSION_EXPIRED");}
 private String evidence(UUID u,Map<String,Object> t){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((HexFormat.of().formatHex((byte[])t.get("fingerprint"))+":"+t.get("mapping_version")+":"+t.get("product_rule")+":"+performance.signature(performance.refunds(u,(UUID)t.get("id")))).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("BENEFIT_EVIDENCE_FAILED");}}
 private Map<String,Object> saved(UUID u,UUID id){var all=db.queryForList("SELECT * FROM financial_benefit_reviews WHERE user_id=? AND transaction_id=?",u,id);return all.isEmpty()?null:all.get(0);}
 private boolean valid(UUID u,Map<String,Object> t,Map<String,Object> s){return s!=null&&Objects.equals(s.get("card_id"),t.get("mapped_card_id"))&&Objects.equals(s.get("evidence_hash"),evidence(u,t))&&"APPLIED".equals(t.get("status"));}
 public Map<String,Object> get(UUID u,UUID id){lock(u);var t=performance.approval(u,id);var s=saved(u,id);var out=new LinkedHashMap<String,Object>();boolean valid=valid(u,t,s);
  out.put("transactionId",id);out.put("cardId",t.get("mapped_card_id"));out.put("version",s==null?0:s.get("version"));out.put("transactionVersion",t.get("version"));out.put("mappingVersion",t.get("mapping_version"));out.put("decision",valid?s.get("decision"):"UNKNOWN");out.put("area",valid?s.get("area"):null);out.put("reviewCurrent",valid);out.put("ruleVersion",ActualBenefitCalculator.VERSION);out.put("productRule",t.get("product_rule"));out.put("month",t.get("used_date")==null?null:t.get("used_date").toString().substring(0,7));out.put("areas",ActualCardRules.GO.equals(t.get("product_rule"))?ActualBenefitCalculator.GO_AREAS:ActualBenefitCalculator.AREAS);out.put("source","USER_CONFIRMED_TERMS_ESTIMATE");return out;
 }
 public Object save(UUID u,UUID id,Input in){lock(u);var t=performance.approval(u,id);var s=saved(u,id);
  if(in==null||in.expectedVersion==null||in.expectedTransactionVersion==null||in.expectedMappingVersion==null||!Boolean.TRUE.equals(in.conditionsConfirmed)||in.decision==null||!Set.of("RULE","EXCLUDE","UNKNOWN").contains(in.decision))throw new ApiError(422,"BENEFIT_CONFIRMATION_REQUIRED");
  if(in.expectedVersion!=(s==null?0:n(s.get("version")))||in.expectedTransactionVersion!=n(t.get("version"))||in.expectedMappingVersion!=n(t.get("mapping_version")))throw new ApiError(409,"BENEFIT_VERSION_CONFLICT");
  if(!"APPLIED".equals(t.get("status"))||t.get("ledger_id")==null)throw new ApiError(409,"BENEFIT_TRANSACTION_NOT_FINAL");
  String rule=(String)t.get("product_rule");String area=in.decision.equals("RULE")?in.area:null;
  if(in.decision.equals("RULE")){
   if(!"KRW".equals(t.get("currency"))||!"1".equals(t.get("payment_type"))||t.get("amount_won")==null)throw new ApiError(422,"BENEFIT_FACTS_UNCONFIRMED");
   if(ActualCardRules.KB.equals(rule)){if(area==null||ActualBenefitCalculator.AREAS.stream().noneMatch(a->a.id().equals(area))||area.equals("FOREIGN_DUTYFREE"))throw new ApiError(422,"BENEFIT_AREA_UNSUPPORTED");}
   else if(ActualCardRules.LOG.equals(rule)){if(!"DOMESTIC_GENERAL".equals(area))throw new ApiError(422,"BENEFIT_AREA_UNSUPPORTED");}
   else if(ActualCardRules.GO.equals(rule)){if(area==null||ActualBenefitCalculator.GO_AREAS.stream().noneMatch(a->a.id().equals(area)))throw new ApiError(422,"BENEFIT_AREA_UNSUPPORTED");}
   else throw new ApiError(422,"BENEFIT_PRODUCT_RULE_UNVERIFIED");
  }
  String hash=evidence(u,t);if(valid(u,t,s)&&Objects.equals(s.get("decision"),in.decision)&&Objects.equals(s.get("area"),area))return get(u,id);
  db.update("INSERT INTO financial_benefit_reviews(user_id,transaction_id,card_id,evidence_hash,decision,area,version) VALUES(?,?,?,?,?,?,?) ON CONFLICT(user_id,transaction_id) DO UPDATE SET card_id=EXCLUDED.card_id,evidence_hash=EXCLUDED.evidence_hash,decision=EXCLUDED.decision,area=EXCLUDED.area,version=EXCLUDED.version,updated_at=now()",u,id,t.get("mapped_card_id"),hash,in.decision,area,s==null?1:n(s.get("version"))+1);db.update("UPDATE app_users SET revision=revision+1 WHERE id=?",u);return get(u,id);
 }
 private LocalDate month(String m){try{return YearMonth.parse(m).atDay(1);}catch(Exception e){throw new ApiError(422,"INVALID_MONTH");}}
 private Map<String,Object> card(UUID u,UUID id){var rows=db.queryForList("SELECT payload->>'source' AS rule FROM cards WHERE user_id=? AND id=?",u,id);if(rows.isEmpty())throw new ApiError(404,"CARD_NOT_FOUND");if(!ActualCardRules.actual((String)rows.get(0).get("rule")))throw new ApiError(422,"ACTUAL_CARD_REQUIRED");return rows.get(0);}
 private Map<String,Object> state(UUID u,UUID id,LocalDate m){var rows=db.queryForList("SELECT current_qualified,version FROM card_month_states WHERE user_id=? AND card_id=? AND month=?",u,id,m);return rows.isEmpty()?Map.of("version",0L):rows.get(0);}
 public Object saveMonth(UUID u,UUID id,String m,MonthInput in){lock(u);var c=card(u,id);LocalDate date=month(m);var state=state(u,id,date);var old=db.queryForList("SELECT * FROM financial_benefit_months WHERE user_id=? AND card_id=? AND month=?",u,id,date);long version=old.isEmpty()?0:n(old.get(0).get("version"));
  if(in==null||in.expectedVersion==null||in.expectedQualificationVersion==null||in.coverageConfirmed==null||in.tier==null||in.tier<0||in.tier>3)throw new ApiError(422,"BENEFIT_MONTH_CONFIRMATION_REQUIRED");
  if(in.expectedVersion!=version||in.expectedQualificationVersion!=n(state.get("version")))throw new ApiError(409,"BENEFIT_VERSION_CONFLICT");
  if((ActualCardRules.KB.equals(c.get("rule"))||ActualCardRules.GO.equals(c.get("rule")))&&(state.get("current_qualified")==null||Boolean.TRUE.equals(state.get("current_qualified"))!=(in.tier>0)))throw new ApiError(422,"BENEFIT_QUALIFICATION_CONFLICT");
  var facts=db.queryForList("SELECT * FROM actual_qualification_facts WHERE user_id=? AND card_id=? AND month=? AND qualification_version=?",u,id,date,in.expectedQualificationVersion);
  if(!facts.isEmpty()){var f=facts.get(0);var calculated=ActualQualification.calculate((String)c.get("rule"),YearMonth.from(date),(Long)f.get("previous_performance_won"),f.get("first_use_date")==null?null:((java.sql.Date)f.get("first_use_date")).toLocalDate());if(calculated.tier()!=null&&!calculated.tier().equals(in.tier)||in.tier<calculated.minimumTier())throw new ApiError(422,"BENEFIT_TIER_CONFLICT");}
  if(ActualCardRules.GO.equals(c.get("rule"))&&in.tier>2)throw new ApiError(422,"INVALID_GO_TIER");
  if(!old.isEmpty()&&Objects.equals(old.get(0).get("tier"),in.tier)&&Objects.equals(old.get(0).get("coverage_confirmed"),in.coverageConfirmed)&&n(old.get(0).get("qualification_version"))==in.expectedQualificationVersion)return summary(u,id,m);
  db.update("INSERT INTO financial_benefit_months(user_id,card_id,month,tier,coverage_confirmed,qualification_version,version) VALUES(?,?,?,?,?,?,?) ON CONFLICT(user_id,card_id,month) DO UPDATE SET tier=EXCLUDED.tier,coverage_confirmed=EXCLUDED.coverage_confirmed,qualification_version=EXCLUDED.qualification_version,version=EXCLUDED.version,updated_at=now()",u,id,date,in.tier,in.coverageConfirmed,in.expectedQualificationVersion,version+1);db.update("UPDATE app_users SET revision=revision+1 WHERE id=?",u);return summary(u,id,m);
 }
 public Map<String,Object> summary(UUID u,UUID id,String m){lock(u);var card=card(u,id);String rule=(String)card.get("rule");LocalDate date=month(m);var state=state(u,id,date);var settings=db.queryForList("SELECT * FROM financial_benefit_months WHERE user_id=? AND card_id=? AND month=?",u,id,date);var context=settings.isEmpty()?null:settings.get(0);boolean complete=context!=null&&Boolean.TRUE.equals(context.get("coverage_confirmed"))&&n(context.get("qualification_version"))==n(state.get("version"));
  long known=0,excluded=0,unknown=0;var purchases=new ArrayList<ActualBenefitCalculator.Purchase>();var ids=db.queryForList("SELECT t.id FROM financial_transactions t JOIN financial_card_mappings m ON m.user_id=t.user_id AND m.connection_id=t.connection_id AND m.source_mask=t.card_mask AND m.source_label=COALESCE(t.card_label,'') WHERE t.user_id=? AND m.card_id=? AND t.kind='APPROVAL' AND t.status<>'IGNORED' AND (t.used_date IS NULL OR (t.used_date>=? AND t.used_date<?))",UUID.class,u,id,date,date.plusMonths(1));
  for(var transaction:ids){var t=performance.approval(u,transaction);var s=saved(u,transaction);String decision=valid(u,t,s)?(String)s.get("decision"):"UNKNOWN";if(decision.equals("UNKNOWN")){unknown++;continue;}if(decision.equals("EXCLUDE")){excluded++;continue;}long original=n(t.get("amount_won"));long refund=performance.refunds(u,transaction).stream().mapToLong(f->n(f.get("amount_won"))).sum();long net=Math.subtractExact(original,refund);if(net<0){unknown++;continue;}
   if(ActualCardRules.KB.equals(rule)&&net>0&&ActualBenefitCalculator.AREAS.stream().anyMatch(a->a.id().equals(s.get("area"))&&net<a.minimumWon()&&original>=a.minimumWon())){unknown++;continue;}
   if(ActualCardRules.GO.equals(rule)&&net>0&&ActualBenefitCalculator.GO_AREAS.stream().anyMatch(a->a.id().equals(s.get("area"))&&net<a.minimumWon()&&original>=a.minimumWon())){unknown++;continue;}
   if(ActualCardRules.LOG.equals(rule))known=Math.addExact(known,ActualCardRules.roundHana(net));else purchases.add(new ActualBenefitCalculator.Purchase((String)s.get("area"),original,net));
  }
  long held=db.queryForObject("SELECT count(*) FROM financial_transactions t JOIN financial_card_mappings m ON m.user_id=t.user_id AND m.connection_id=t.connection_id AND m.source_mask=t.card_mask AND m.source_label=COALESCE(t.card_label,'') WHERE t.user_id=? AND m.card_id=? AND t.kind='CANCEL_EVENT' AND t.status NOT IN('APPLIED','IGNORED') AND (t.used_date IS NULL OR t.used_date<?)",Long.class,u,id,date.plusMonths(1));unknown+=held;
  long manual=db.queryForObject("SELECT count(*) FROM ledger_entries e WHERE e.user_id=? AND e.card_id=? AND e.purchased_at>=? AND e.purchased_at<? AND NOT EXISTS(SELECT 1 FROM financial_transactions t WHERE t.user_id=e.user_id AND t.ledger_id=e.id)",Long.class,u,id,date,date.plusMonths(1));unknown+=manual;
  var result=new LinkedHashMap<String,Object>();result.put("cardId",id);result.put("month",m);result.put("source","USER_CONFIRMED_TERMS_ESTIMATE");result.put("ruleVersion",ActualBenefitCalculator.VERSION);result.put("version",context==null?0:context.get("version"));result.put("qualificationVersion",state.get("version"));result.put("coverageConfirmed",complete);result.put("tier",context==null?null:context.get("tier"));result.put("unknownCount",unknown);result.put("excludedCount",excluded);result.put("reviewedCount",ids.size()-unknown+held+manual);result.put("hasEvidence",!ids.isEmpty()||held>0);result.put("officialRemainingCapWon",null);result.put("cancellationPolicy","ORIGINAL_PURCHASE_MONTH_ESTIMATE_NOT_ISSUER_POSTED");
  Long used=null,remaining=null,cap=null;long lower=known,upper=known;List<?> areas=List.of();
  if(ActualCardRules.KB.equals(rule)){Integer tier=context==null?null:((Number)context.get("tier")).intValue();if(tier!=null){var c=ActualBenefitCalculator.kb(tier,purchases);cap=c.capWon();lower=c.lowerWon();upper=c.upperWon();areas=c.areas();if(complete&&unknown==0){used=c.usedWon();remaining=c.remainingWon();}}}
  else if(ActualCardRules.GO.equals(rule)){Integer tier=context==null?null:((Number)context.get("tier")).intValue();if(tier!=null){var c=ActualBenefitCalculator.go(tier,purchases);cap=c.capWon();lower=c.lowerWon();upper=c.upperWon();areas=c.areas();if(complete&&unknown==0){used=c.usedWon();remaining=c.remainingWon();}}}
  else if(ActualCardRules.LOG.equals(rule)){if(complete&&unknown==0)used=known;}
  else if(complete&&unknown==0&&ids.size()==excluded){used=0L;}
  result.put("estimatedUsedWon",used);result.put("estimatedRemainingCapWon",remaining);result.put("estimatedLimitWon",cap);result.put("confirmedLowerWon",lower);result.put("confirmedUpperWon",upper);result.put("areas",areas);result.put("unlimited",ActualCardRules.LOG.equals(rule));return result;
 }
}
