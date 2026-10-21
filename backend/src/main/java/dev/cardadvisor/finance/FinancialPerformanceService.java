package dev.cardadvisor.finance;

import dev.cardadvisor.api.ApiError;
import dev.cardadvisor.domain.ActualCardRules;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** User-confirmed performance evidence. Never changes ledger, budget or current qualification. */
@Service @Transactional
public class FinancialPerformanceService {
 private final JdbcTemplate db;
 public FinancialPerformanceService(JdbcTemplate db){this.db=db;}
 public record RefundDate(UUID cancellationId,LocalDate receiptDate){}
 public record Input(Long expectedVersion,Long expectedTransactionVersion,Long expectedMappingVersion,String decision,Boolean confirmed,List<RefundDate> refunds){}
 public record Contribution(Long amountWon,long confirmedSubtotalWon,long includedCount,long excludedCount,long unknownCount,long unknownRefundDateCount,boolean hasEvidence){}
 private long n(Object v){return ((Number)v).longValue();}
 private void lock(UUID u){if(db.queryForList("SELECT id FROM app_users WHERE id=? FOR UPDATE",u).isEmpty())throw new ApiError(401,"SESSION_EXPIRED");}
 Map<String,Object> approval(UUID u,UUID id){
  var rows=db.queryForList("SELECT t.*,m.card_id AS mapped_card_id,m.version AS mapping_version,c.payload->>'source' AS product_rule FROM financial_transactions t LEFT JOIN financial_card_mappings m ON m.user_id=t.user_id AND m.connection_id=t.connection_id AND m.source_mask=t.card_mask AND m.source_label=COALESCE(t.card_label,'') LEFT JOIN cards c ON c.user_id=m.user_id AND c.id=m.card_id WHERE t.user_id=? AND t.id=?",u,id);
  if(rows.isEmpty())throw new ApiError(404,"FINANCIAL_RESOURCE_NOT_FOUND");var row=rows.get(0);
  if(!"APPROVAL".equals(row.get("kind"))||row.get("mapped_card_id")==null)throw new ApiError(422,"PERFORMANCE_MAPPING_REQUIRED");return row;
 }
 List<Map<String,Object>> refunds(UUID u,UUID id){
  return db.queryForList("SELECT c.id AS cancellation_id,c.amount_won,CASE WHEN t.kind='CANCEL_EVENT' THEN t.used_date ELSE original.used_date END AS earliest_date,t.fingerprint,rd.receipt_date FROM financial_refund_applications a JOIN financial_transactions t ON t.user_id=a.user_id AND t.id=a.financial_transaction_id JOIN financial_transactions original ON original.user_id=t.user_id AND original.id=? JOIN cancellations c ON c.user_id=a.user_id AND c.id=a.cancellation_id LEFT JOIN financial_performance_refund_dates rd ON rd.user_id=c.user_id AND rd.transaction_id=original.id AND rd.cancellation_id=c.id WHERE a.user_id=? AND (t.id=original.id OR t.linked_transaction_id=original.id) ORDER BY c.id",id,u);
 }
 String signature(List<Map<String,Object>> refunds){try{var s=new StringBuilder();for(var r:refunds)s.append(r.get("cancellation_id")).append(':').append(r.get("amount_won")).append(':').append(HexFormat.of().formatHex((byte[])r.get("fingerprint"))).append(';');return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("PERFORMANCE_SIGNATURE_FAILED");}}
 private Map<String,Object> saved(UUID u,UUID id){var rows=db.queryForList("SELECT * FROM financial_performance_reviews WHERE user_id=? AND transaction_id=?",u,id);return rows.isEmpty()?null:rows.get(0);}
 private boolean valid(Map<String,Object> t,Map<String,Object> s,List<Map<String,Object>> refunds){return s!=null&&Objects.equals(s.get("card_id"),t.get("mapped_card_id"))&&Objects.equals(s.get("mapping_version"),t.get("mapping_version"))&&Objects.equals(s.get("rule_version"),t.get("product_rule"))&&Arrays.equals((byte[])s.get("transaction_fingerprint"),(byte[])t.get("fingerprint"))&&Objects.equals(s.get("refund_signature"),signature(refunds))&&"APPLIED".equals(t.get("status"));}
 public Map<String,Object> get(UUID u,UUID id){lock(u);var t=approval(u,id);var f=refunds(u,id);var s=saved(u,id);boolean valid=valid(t,s,f);var out=new LinkedHashMap<String,Object>();
  out.put("transactionId",id);out.put("cardId",t.get("mapped_card_id"));out.put("transactionVersion",t.get("version"));out.put("mappingVersion",t.get("mapping_version"));out.put("version",s==null?0:s.get("version"));out.put("decision",valid?s.get("decision"):"UNKNOWN");out.put("reviewCurrent",valid);out.put("ruleVersion",t.get("product_rule"));out.put("source","USER_CONFIRMED_TERMS_ESTIMATE");out.put("purchaseDate",t.get("used_date"));out.put("amountWon",t.get("amount_won"));
  out.put("refunds",f.stream().map(r->{var x=new LinkedHashMap<String,Object>();x.put("cancellationId",r.get("cancellation_id"));x.put("amountWon",r.get("amount_won"));x.put("earliestDate",r.get("earliest_date"));x.put("receiptDate",r.get("receipt_date"));return x;}).toList());return out;
 }
 public Map<String,Object> save(UUID u,UUID id,Input in){lock(u);var t=approval(u,id);var old=saved(u,id);var f=refunds(u,id);
  if(in==null||in.expectedVersion()==null||in.expectedTransactionVersion()==null||in.expectedMappingVersion()==null||in.decision()==null||!Set.of("INCLUDE","EXCLUDE","UNKNOWN").contains(in.decision())||!Boolean.TRUE.equals(in.confirmed())||in.refunds()==null)throw new ApiError(422,"PERFORMANCE_CONFIRMATION_REQUIRED");
  if(in.expectedVersion()!=(old==null?0:n(old.get("version")))||in.expectedTransactionVersion()!=n(t.get("version"))||in.expectedMappingVersion()!=n(t.get("mapping_version")))throw new ApiError(409,"PERFORMANCE_VERSION_CONFLICT");
  if(!"APPLIED".equals(t.get("status"))||t.get("ledger_id")==null)throw new ApiError(409,"PERFORMANCE_TRANSACTION_NOT_FINAL");
  if(!ActualCardRules.actual((String)t.get("product_rule")))throw new ApiError(422,"PERFORMANCE_RULE_UNSUPPORTED");
  if(in.decision().equals("INCLUDE")&&(!"KRW".equals(t.get("currency"))||t.get("amount_won")==null||(t.get("payment_type")==null||!Set.of("1","2").contains(t.get("payment_type")))||t.get("used_date")==null))throw new ApiError(422,"PERFORMANCE_FACTS_UNCONFIRMED");
  var dates=new HashMap<UUID,LocalDate>();for(var d:in.refunds()){if(d==null||d.cancellationId()==null||dates.containsKey(d.cancellationId()))throw new ApiError(422,"INVALID_REFUND_DATES");dates.put(d.cancellationId(),d.receiptDate());}
  if(!dates.keySet().equals(new HashSet<>(f.stream().map(r->(UUID)r.get("cancellation_id")).toList())))throw new ApiError(409,"PERFORMANCE_REFUNDS_CHANGED");
  for(var refund:f){var date=dates.get((UUID)refund.get("cancellation_id"));if(date!=null&&(refund.get("earliest_date")==null||date.isBefore(((java.sql.Date)refund.get("earliest_date")).toLocalDate())||date.isAfter(LocalDate.now(ZoneId.of("Asia/Seoul")))))throw new ApiError(422,"INVALID_REFUND_RECEIPT_DATE");}
  boolean same=valid(t,old,f)&&Objects.equals(old.get("decision"),in.decision())&&f.stream().allMatch(r->Objects.equals(r.get("receipt_date")==null?null:((java.sql.Date)r.get("receipt_date")).toLocalDate(),dates.get((UUID)r.get("cancellation_id"))));if(same)return get(u,id);
  long version=old==null?1:n(old.get("version"))+1;
  db.update("INSERT INTO financial_performance_reviews(user_id,transaction_id,card_id,mapping_version,transaction_fingerprint,refund_signature,rule_version,decision,version) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(user_id,transaction_id) DO UPDATE SET card_id=EXCLUDED.card_id,mapping_version=EXCLUDED.mapping_version,transaction_fingerprint=EXCLUDED.transaction_fingerprint,refund_signature=EXCLUDED.refund_signature,rule_version=EXCLUDED.rule_version,decision=EXCLUDED.decision,version=EXCLUDED.version,updated_at=now()",u,id,t.get("mapped_card_id"),t.get("mapping_version"),t.get("fingerprint"),signature(f),t.get("product_rule"),in.decision(),version);
  db.update("DELETE FROM financial_performance_refund_dates WHERE user_id=? AND transaction_id=?",u,id);for(var d:in.refunds())db.update("INSERT INTO financial_performance_refund_dates(user_id,transaction_id,cancellation_id,receipt_date) VALUES(?,?,?,?)",u,id,d.cancellationId(),d.receiptDate());
  db.update("UPDATE app_users SET revision=revision+1 WHERE id=?",u);return get(u,id);
 }
 public Contribution contribution(UUID u,UUID card,LocalDate month){
  long known=0,inc=0,exc=0,unknown=0,unknownDates=0;boolean has=false;
  var ids=db.queryForList("SELECT t.id FROM financial_transactions t JOIN financial_card_mappings m ON m.user_id=t.user_id AND m.connection_id=t.connection_id AND m.source_mask=t.card_mask AND m.source_label=COALESCE(t.card_label,'') WHERE t.user_id=? AND m.card_id=? AND t.kind='APPROVAL' AND t.status<>'IGNORED' AND (t.used_date IS NULL OR t.used_date<?)",UUID.class,u,card,month.plusMonths(1));
  for(UUID id:ids){var t=approval(u,id);var f=refunds(u,id);LocalDate used=t.get("used_date")==null?null:((java.sql.Date)t.get("used_date")).toLocalDate();boolean purchase=used==null||YearMonth.from(used).equals(YearMonth.from(month));
   boolean refundRelevant=f.stream().anyMatch(r->r.get("receipt_date")==null||YearMonth.from(((java.sql.Date)r.get("receipt_date")).toLocalDate()).equals(YearMonth.from(month)));
   if(!purchase&&!refundRelevant)continue;has=true;var s=saved(u,id);String decision=valid(t,s,f)?(String)s.get("decision"):"UNKNOWN";
   if(decision.equals("EXCLUDE")){exc++;continue;}if(decision.equals("UNKNOWN")){unknown++;continue;}
   inc++;if(purchase){if(used==null||t.get("amount_won")==null){unknown++;continue;}known=Math.addExact(known,n(t.get("amount_won")));}
   for(var refund:f){if(refund.get("receipt_date")==null){unknownDates++;continue;}LocalDate date=((java.sql.Date)refund.get("receipt_date")).toLocalDate();if(YearMonth.from(date).equals(YearMonth.from(month))){if(!ActualCardRules.KB.equals(t.get("product_rule"))){unknown++;continue;}known=Math.subtractExact(known,n(refund.get("amount_won")));}}
  }
  long unmatched=db.queryForObject("SELECT count(*) FROM financial_transactions t JOIN financial_card_mappings m ON m.user_id=t.user_id AND m.connection_id=t.connection_id AND m.source_mask=t.card_mask AND m.source_label=COALESCE(t.card_label,'') WHERE t.user_id=? AND m.card_id=? AND t.kind='CANCEL_EVENT' AND t.status NOT IN('APPLIED','IGNORED') AND (t.used_date IS NULL OR t.used_date<?)",Long.class,u,card,month.plusMonths(1));unknown+=unmatched;has|=unmatched>0;
  return new Contribution(unknown==0&&unknownDates==0?known:null,known,inc,exc,unknown,unknownDates,has);
 }
 public Object summary(UUID u,UUID card,String month){lock(u);if(db.queryForObject("SELECT count(*) FROM cards WHERE user_id=? AND id=?",Long.class,u,card)==0)throw new ApiError(404,"CARD_NOT_FOUND");LocalDate date;try{date=YearMonth.parse(month).atDay(1);}catch(Exception e){throw new ApiError(422,"INVALID_MONTH");}return contribution(u,card,date);}
}
