package dev.cardadvisor.account;
import dev.cardadvisor.api.*;
import dev.cardadvisor.domain.*;
import dev.cardadvisor.domain.RecommendationEngine.Card;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.*;

@Service @Transactional
public class CardCatalogService {
 private final JdbcTemplate db;private final ObjectMapper mapper;private final dev.cardadvisor.finance.FinancialPerformanceService financial;private final dev.cardadvisor.finance.FinancialBenefitService benefits;
 public CardCatalogService(JdbcTemplate db,ObjectMapper mapper,dev.cardadvisor.finance.FinancialPerformanceService financial,dev.cardadvisor.finance.FinancialBenefitService benefits){this.db=db;this.mapper=mapper;this.financial=financial;this.benefits=benefits;}
 public record Register(String productId,String expectedRuleVersion){}
 public Object register(UUID u,Register in){
  var product=ActualCardRules.PRODUCTS.stream().filter(p->p.id().equals(in.productId())).findFirst().orElseThrow(()->new ApiError(422,"UNKNOWN_PRODUCT"));
  if(!product.ruleVersion().equals(in.expectedRuleVersion()))throw new ApiError(409,"PRODUCT_VERSION_CHANGED");
  db.queryForObject("SELECT revision FROM app_users WHERE id=? FOR UPDATE",Long.class,u);
  UUID id=UUID.nameUUIDFromBytes((u+":"+product.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  var card=new Card(id.toString(),product.name(),product.rateBp(),product.noPerformanceRequirement()?true:null,product.noPerformanceRequirement()?0L:null,product.targetWon(),null,true,product.ruleVersion());
  var existing=db.queryForList("SELECT payload->>'source' AS source FROM cards WHERE user_id=? AND id=?",u,id);
  if(!existing.isEmpty()){if(!product.ruleVersion().equals(existing.get(0).get("source")))throw new ApiError(409,"PRODUCT_VERSION_CHANGED");return card;}
  try{db.update("INSERT INTO cards(id,user_id,payload) VALUES (?,?,?::jsonb)",id,u,mapper.writeValueAsString(card));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("CARD_SERIALIZATION_FAILED");}
  db.update("UPDATE app_users SET revision=revision+1 WHERE id=?",u);return card;
 }
 public AdvisorService.CardPerformance performance(UUID u,Card c,String month,Map<String,Object> state){
  LocalDate from=YearMonth.parse(month).atDay(1);boolean noRequirement=ActualCardRules.LOG.equals(c.source());
  Long baseline=noRequirement?Long.valueOf(0L):number(state.get("untracked_performance_won"));
  Boolean qualified=noRequirement?Boolean.TRUE:(Boolean)state.get("current_qualified");
  boolean financialReview=db.queryForObject("SELECT count(*) FROM financial_card_mappings m JOIN financial_transactions t ON t.user_id=m.user_id AND t.connection_id=m.connection_id AND t.card_mask=m.source_mask AND COALESCE(t.card_label,'')=m.source_label WHERE m.user_id=? AND m.card_id=? AND t.used_date>=? AND t.used_date<? AND t.status<>'IGNORED'",Long.class,u,UUID.fromString(c.id()),from,from.plusMonths(1))>0;
  var contribution=financial.contribution(u,UUID.fromString(c.id()),from);
  long approved=contribution.amountWon()==null?0:contribution.amountWon();boolean unknown=contribution.amountWon()==null;long benefit=0;
  for(var entry:db.queryForList("SELECT e.*,COALESCE((SELECT sum(card_refund_won) FROM cancellations c WHERE c.user_id=e.user_id AND c.transaction_id=e.id),0) AS refunded FROM ledger_entries e WHERE e.user_id=? AND e.card_id=? AND e.purchased_at>=? AND e.purchased_at<? AND NOT EXISTS(SELECT 1 FROM financial_transactions ft WHERE ft.user_id=e.user_id AND ft.ledger_id=e.id)",u,UUID.fromString(c.id()),from,from.plusMonths(1))){
   if(entry.get("performance_eligible")==null)unknown=true;
   else if(Boolean.TRUE.equals(entry.get("performance_eligible")))approved=Math.addExact(approved,((Number)entry.get("card_charge_won")).longValue());
   // Only the supported shopping scope is issued by this version. Other purchases remain blocked.
   if(noRequirement&&((Number)entry.get("benefit_rate_bp")).intValue()==30)benefit=Math.addExact(benefit,ActualCardRules.roundHana(((Number)entry.get("card_charge_won")).longValue()-((Number)entry.get("refunded")).longValue()));
  }
  long refunds=0;
  for(var r:db.queryForList("SELECT c.card_refund_won,e.performance_eligible FROM cancellations c JOIN ledger_entries e ON e.user_id=c.user_id AND e.id=c.transaction_id WHERE e.user_id=? AND e.card_id=? AND c.received_at>=? AND c.received_at<? AND NOT EXISTS(SELECT 1 FROM financial_transactions ft WHERE ft.user_id=e.user_id AND ft.ledger_id=e.id)",u,UUID.fromString(c.id()),from,from.plusMonths(1))){
   if(Boolean.FALSE.equals(r.get("performance_eligible")))continue;
   if(r.get("card_refund_won")==null||r.get("performance_eligible")==null)unknown=true;else refunds=Math.addExact(refunds,((Number)r.get("card_refund_won")).longValue());
  }
  // KB explicitly deducts cancellations in their receipt month. The other performance policy needs issuer confirmation.
  if(ActualCardRules.GO.equals(c.source())&&refunds>0)unknown=true;
  Long ledger=noRequirement?Long.valueOf(0L):unknown?null:Math.subtractExact(approved,refunds);
  Long total=baseline==null||ledger==null?null:Math.addExact(baseline,ledger);
  if(total!=null&&total<0)total=null;
  Long gap=total==null?null:Math.max(0,c.targetWon()-total);
  var financialBenefits=financialReview?benefits.summary(u,UUID.fromString(c.id()),month):null;
  Long financialUsed=financialBenefits==null?null:number(financialBenefits.get("estimatedUsedWon"));
  Long financialCap=financialBenefits==null?null:number(financialBenefits.get("estimatedRemainingCapWon"));
  return new AdvisorService.CardPerformance(c.id(),c.name(),month,YearMonth.parse(month).minusMonths(1).toString(),YearMonth.parse(month).plusMonths(1).toString(),
   qualified,baseline,ledger,total,c.targetWon(),gap,gap==null?null:gap==0,financialCap,state.get("version")==null?0:((Number)state.get("version")).longValue(),
   total==null?"UNKNOWN":contribution.hasEvidence()?"USER_REVIEWED_TERMS_ESTIMATE":"TERMS_ESTIMATE",noRequirement?"PRODUCT_NO_THRESHOLD":state.getOrDefault("qualification_source","UNKNOWN").toString(),
   ActualCardRules.KB.equals(c.source())?"CANCELLATION_RECEIPT_MONTH":"REFUND_REQUIRES_ISSUER_REVIEW",noRequirement?"PRODUCT_NO_THRESHOLD":state.getOrDefault("untracked_source","UNKNOWN").toString(),
   date(state.get("untracked_as_of")),date(state.get("qualification_as_of")),null,financialReview?financialUsed:Long.valueOf(benefit),financialReview?(financialUsed==null?"FINANCIAL_RULE_REVIEW_REQUIRED":"USER_CONFIRMED_TERMS_ESTIMATE"):"TERMS_ESTIMATE",c.source());
 }
 private Long number(Object o){return o==null?null:((Number)o).longValue();}
 private String date(Object o){return o==null?null:o.toString();}
}
