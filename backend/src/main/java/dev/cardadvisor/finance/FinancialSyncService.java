package dev.cardadvisor.finance;

import dev.cardadvisor.api.ApiError;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class FinancialSyncService {
 private final JdbcTemplate db;private final CodefClient provider;private final ConnectedIdCipher cipher;private final TransactionTemplate tx;
 private final CodefApprovalNormalizer normalizer=new CodefApprovalNormalizer();
 public FinancialSyncService(JdbcTemplate db,CodefClient provider,ConnectedIdCipher cipher,PlatformTransactionManager manager){this.db=db;this.provider=provider;this.cipher=cipher;tx=new TransactionTemplate(manager);}
 public record Input(LocalDate from,LocalDate to,Long expectedVersion){}
 public record Resolution(Long version,String action,UUID ledgerId,UUID approvalId){}
 private record Work(UUID run,boolean execute,Map<String,Object> connection){}
 private void lock(UUID user){if(db.queryForList("SELECT id FROM app_users WHERE id=? FOR UPDATE",user).isEmpty())throw new ApiError(401,"SESSION_EXPIRED");}
 private Map<String,Object> one(String table,UUID user,UUID id){ // table is always a server constant
  var rows=db.queryForList("SELECT * FROM "+table+" WHERE user_id=? AND id=?",user,id);if(rows.isEmpty())throw new ApiError(404,"FINANCIAL_RESOURCE_NOT_FOUND");return rows.get(0);
 }
 private void changed(UUID user){db.update("UPDATE app_users SET revision=revision+1 WHERE id=?",user);}
 private long number(Object value){return ((Number)value).longValue();}
 public Map<String,Object> run(UUID user,UUID id){return one("financial_sync_runs",user,id);}
 public List<Map<String,Object>> runs(UUID user,UUID connection){one("financial_connections",user,connection);return db.queryForList("SELECT * FROM financial_sync_runs WHERE user_id=? AND connection_id=? ORDER BY created_at DESC,id DESC LIMIT 20",user,connection);}
 public List<Map<String,Object>> transactions(UUID user,UUID connection){one("financial_connections",user,connection);return db.queryForList("SELECT t.*,c.payload->>'name' AS mapped_card_name FROM financial_transactions t LEFT JOIN financial_card_mappings m ON m.user_id=t.user_id AND m.connection_id=t.connection_id AND m.source_mask=t.card_mask AND m.source_label=COALESCE(t.card_label,'') LEFT JOIN cards c ON c.user_id=m.user_id AND c.id=m.card_id WHERE t.user_id=? AND t.connection_id=? ORDER BY t.used_date,t.id",user,connection).stream().map(this::publicRow).toList();}
 private Map<String,Object> publicRow(Map<String,Object> row){var out=new LinkedHashMap<>(row);out.remove("identity_hash");out.remove("fingerprint");return out;}
 public Map<String,Object> sync(UUID user,UUID connection,UUID key,Input input){
  if(key==null||input==null||input.from()==null||input.to()==null||input.expectedVersion()==null||input.from().isAfter(input.to())||input.to().isAfter(LocalDate.now(ZoneId.of("Asia/Seoul")))||input.to().isAfter(input.from().plusDays(30)))throw new ApiError(422,"INVALID_SYNC_WINDOW");
  Work work=tx.execute(status->{
   lock(user);var c=one("financial_connections",user,connection);
   var old=db.queryForList("SELECT * FROM financial_sync_runs WHERE user_id=? AND idempotency_key=?",user,key);
   if(!old.isEmpty()){
    var r=old.get(0);if(!r.get("connection_id").equals(connection)||!r.get("from_date").toString().equals(input.from().toString())||!r.get("to_date").toString().equals(input.to().toString())||number(r.get("connection_version"))!=input.expectedVersion())throw new ApiError(409,"IDEMPOTENCY_CONFLICT");
    return new Work((UUID)r.get("id"),false,c);
   }
   if(!Set.of("0301","0313").contains(c.get("organization")))throw new ApiError(422,"UNSUPPORTED_APPROVAL_INSTITUTION");
   int months=c.get("organization").equals("0301")?12:18;
   if(input.from().isBefore(LocalDate.now(ZoneId.of("Asia/Seoul")).minusMonths(months)))throw new ApiError(422,"SYNC_WINDOW_OUTSIDE_DOCUMENTED_HISTORY");
   if(!c.get("state").equals("ACTIVE")||c.get("cid_ciphertext")==null)throw new ApiError(409,"CONNECTION_NOT_ACTIVE");
   if(number(c.get("version"))!=input.expectedVersion())throw new ApiError(409,"VERSION_CONFLICT");
   provider.requireAvailable();cipher.requireConfigured();
   if(!provider.environment().equals(c.get("environment"))||!provider.scope().equals(c.get("provider_scope")))throw new ApiError(409,"PROVIDER_CONFIGURATION_CHANGED");
   db.update("UPDATE financial_sync_runs SET status='FAILED',error_code='SYNC_INTERRUPTED',finished_at=now() WHERE user_id=? AND connection_id=? AND status='RUNNING' AND created_at<now()-interval '10 minutes'",user,connection);
   if(db.queryForObject("SELECT count(*) FROM financial_sync_runs WHERE user_id=? AND connection_id=? AND status='RUNNING'",Long.class,user,connection)>0)throw new ApiError(409,"SYNC_IN_PROGRESS");
   if(c.get("transaction_key_id")==null){db.update("UPDATE financial_connections SET transaction_key_id=? WHERE user_id=? AND id=?",cipher.activeKeyId(),user,connection);c.put("transaction_key_id",cipher.activeKeyId());}
   UUID run=UUID.randomUUID();db.update("INSERT INTO financial_sync_runs(id,user_id,connection_id,idempotency_key,from_date,to_date,connection_version,status) VALUES (?,?,?,?,?,?,?,'RUNNING')",run,user,connection,key,input.from(),input.to(),input.expectedVersion());
   return new Work(run,true,c);
  });
  if(!work.execute())return run(user,work.run());
  try{
   var c=work.connection();String org=(String)c.get("organization");
   String cid=cipher.decrypt(new ConnectedIdCipher.Sealed((String)c.get("key_id"),(byte[])c.get("cid_nonce"),(byte[])c.get("cid_ciphertext")),user,connection,(String)c.get("environment"),(String)c.get("provider_scope"),org);
   // No undocumented pagination. An explicit <=31-day, all-card read with minimum merchant detail.
   var data=provider.approvals(cid,org,"",input.from(),input.to(),Map.of("inquiryType","1","orderBy","1","memberStoreInfoType","0")).data();
   var rows=normalizer.normalize(org,data,input.from(),input.to());
   var legacy=normalizer.legacyNormalize(org,data,input.from(),input.to());
   var legacyAliases=new HashMap<String,Set<String>>();
   for(int i=0;i<rows.size();i++)if(rows.get(i).identity()!=null&&legacy.get(i).identity()==null)legacyAliases.computeIfAbsent(legacy.get(i).fingerprintMaterial(),x->new HashSet<>()).add(rows.get(i).identity());
   var identities=new HashMap<String,String>();
   for(var row:rows)if(row.identity()!=null){String previous=identities.putIfAbsent(row.identity(),row.fingerprintMaterial());if(previous!=null&&!previous.equals(row.fingerprintMaterial()))throw new ApiError(422,"AMBIGUOUS_APPROVAL_IDENTITY");}
   tx.executeWithoutResult(status->{
    lock(user);var current=one("financial_connections",user,connection);
    if(!current.get("state").equals("ACTIVE")||number(current.get("version"))!=input.expectedVersion()||!one("financial_sync_runs",user,work.run()).get("status").equals("RUNNING"))throw new ApiError(409,"CONNECTION_CHANGED_DURING_SYNC");
    // A legacy alias collision matters only when there is an old row to repair.
    // New approvals keep their complete distinct identities, even if the old lossy shape matches.
    for(var alias:legacyAliases.entrySet())if(alias.getValue().size()>1){
     byte[] legacyHash=hash((String)c.get("transaction_key_id"),user+"|"+connection+"|unidentified|"+alias.getKey());
     if(db.queryForObject("SELECT count(*) FROM financial_transactions WHERE user_id=? AND connection_id=? AND identity_hash=?",Long.class,user,connection,legacyHash)>0)throw new ApiError(422,"AMBIGUOUS_LEGACY_APPROVAL_IDENTITY");
    }
    var ids=new LinkedHashSet<UUID>();
    for(int i=0;i<rows.size();i++)ids.add(store(user,connection,work.run(),(String)c.get("transaction_key_id"),rows.get(i),legacy.get(i)));
    int applied=0,held=0;
    for(UUID id:ids){apply(user,id,false);String state=(String)one("financial_transactions",user,id).get("status");if(state.equals("APPLIED"))applied++;else if(!state.equals("IGNORED"))held++;}
    db.update("UPDATE financial_sync_runs SET status=?,received=?,applied=?,held=?,finished_at=now() WHERE user_id=? AND id=?",held==0?"IMPORTED":"PARTIAL",rows.size(),applied,held,user,work.run());
   });
  }catch(CodefFailure e){fail(user,work.run(),"CODEF_"+e.kind.name());}
  catch(ApiError e){fail(user,work.run(),e.getMessage());}
  catch(IllegalArgumentException e){fail(user,work.run(),"INVALID_APPROVAL_RESPONSE");}
  catch(Exception e){fail(user,work.run(),"SYNC_RESULT_UNCONFIRMED");}
  return run(user,work.run());
 }
 private void fail(UUID user,UUID run,String code){tx.executeWithoutResult(s->{lock(user);db.update("UPDATE financial_sync_runs SET status='FAILED',error_code=?,finished_at=now() WHERE user_id=? AND id=? AND status='RUNNING'",code,user,run);});}
 private byte[] hash(String key,String value){return cipher.fingerprint(key,value.getBytes(StandardCharsets.UTF_8));}
 private UUID store(UUID user,UUID connection,UUID run,String key,CodefApprovalNormalizer.Approval a,CodefApprovalNormalizer.Approval legacy){
  byte[] identity=hash(key,user+"|"+connection+"|"+(a.identity()==null?"unidentified|"+a.fingerprintMaterial():a.identity())),fp=hash(key,a.fingerprintMaterial());
  var old=db.queryForList("SELECT * FROM financial_transactions WHERE user_id=? AND connection_id=? AND identity_hash=?",user,connection,identity);
  if(a.identity()!=null&&legacy.identity()==null&&"MISSING_IDENTITY".equals(legacy.issue())){
   byte[] legacyHash=hash(key,user+"|"+connection+"|unidentified|"+legacy.fingerprintMaterial());
   var candidates=db.queryForList("SELECT * FROM financial_transactions WHERE user_id=? AND connection_id=? AND identity_hash=?",user,connection,legacyHash);
   if(!candidates.isEmpty()){
    var previous=candidates.get(0);
    if(!old.isEmpty()||!"HELD".equals(previous.get("status"))||!"MISSING_IDENTITY".equals(previous.get("issue"))||previous.get("ledger_id")!=null||previous.get("linked_transaction_id")!=null||!"CODEF_KB_HANA_20261019".equals(previous.get("rule_version")))throw new ApiError(422,"LEGACY_APPROVAL_REQUIRES_REVIEW");
    db.update("UPDATE financial_transactions SET identity_hash=?,card_mask=?,used_time=?,rule_version=? WHERE user_id=? AND id=?",identity,a.cardMask(),a.time(),CodefApprovalNormalizer.VERSION,user,previous.get("id"));
    old=candidates;
   }
  }
  if(!old.isEmpty()){
   var r=old.get(0);UUID id=(UUID)r.get("id");
   if(!Arrays.equals(fp,(byte[])r.get("fingerprint"))){
    db.update("UPDATE financial_transactions SET fingerprint=?,amount_won=?,cancelled_won=?,currency=?,source_amount=?,payment_type=?,installments=?,issue=?,status=CASE WHEN status='IGNORED' THEN status ELSE 'RECEIVED' END,version=version+1,last_run_id=?,updated_at=now() WHERE user_id=? AND id=?",fp,a.amountWon(),a.cancelledWon(),a.currency(),a.sourceAmount(),a.paymentType(),a.installments(),a.issue(),run,user,id);
    changed(user); // A changed provider observation invalidates recommendations even when held.
   }
   displayMetadata(user,id,a);
   return id;
  }
  UUID id=UUID.randomUUID();db.update("INSERT INTO financial_transactions(id,user_id,connection_id,identity_hash,fingerprint,card_mask,used_date,used_time,kind,amount_won,cancelled_won,currency,source_amount,payment_type,installments,status,issue,rule_version,source,last_run_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'RECEIVED',?,?,?,?)",id,user,connection,identity,fp,a.cardMask(),a.date(),a.time(),a.kind(),a.amountWon(),a.cancelledWon(),a.currency(),a.sourceAmount(),a.paymentType(),a.installments(),a.issue(),CodefApprovalNormalizer.VERSION,Boolean.TRUE.equals(provider.readiness().get("syntheticProvider"))?"SYNTHETIC":"PROVIDER_REPORTED",run);
  changed(user);
  displayMetadata(user,id,a);
  return id;
 }
 private void displayMetadata(UUID user,UUID id,CodefApprovalNormalizer.Approval a){db.update("UPDATE financial_transactions SET merchant_name=COALESCE(?,merchant_name),card_label=COALESCE(?,card_label) WHERE user_id=? AND id=?",a.merchantName(),a.cardLabel(),user,id);}
 private void hold(UUID user,UUID id,String issue){db.update("UPDATE financial_transactions SET status='HELD',issue=? WHERE user_id=? AND id=?",issue,user,id);}
 private void apply(UUID user,UUID id,boolean forceImport){
  var r=one("financial_transactions",user,id);if(r.get("status").equals("IGNORED"))return;
  // These issues are reconciliation decisions, not missing/invalid provider fields.
  if(r.get("issue")!=null&&!Set.of("MANUAL_DUPLICATE_REVIEW","CANCELLATION_MATCH_REQUIRED","REFUND_REGRESSION","REFUND_CONFLICT","APPROVAL_AMOUNT_CHANGED").contains(r.get("issue"))){hold(user,id,(String)r.get("issue"));return;}
  if(r.get("kind").equals("REJECTED")){hold(user,id,"REJECTED_APPROVAL");return;}
  if(r.get("kind").equals("CANCEL_EVENT")){
   if(r.get("linked_transaction_id")==null){hold(user,id,"CANCELLATION_MATCH_REQUIRED");return;}
   var original=one("financial_transactions",user,(UUID)r.get("linked_transaction_id"));
   if(original.get("ledger_id")==null){hold(user,id,"CANCELLATION_MATCH_REQUIRED");return;}
   // Never combine cumulative Type 1 and separate Type 2 refunds without reconciliation.
   if(original.get("cancelled_won")!=null&&number(original.get("cancelled_won"))>0){hold(user,id,"REFUND_CONFLICT");return;}
   refund(user,id,(UUID)original.get("ledger_id"),number(r.get("cancelled_won")));
   return;
  }
  if(r.get("amount_won")==null||r.get("cancelled_won")==null){hold(user,id,"UNKNOWN_OR_INVALID_AMOUNT");return;}
  UUID ledger=(UUID)r.get("ledger_id");long amount=number(r.get("amount_won"));
  if(ledger==null){
   long candidates=db.queryForObject("SELECT count(*) FROM ledger_entries WHERE user_id=? AND source='MANUAL' AND purchased_at=? AND amount_won=?",Long.class,user,r.get("used_date"),amount);
   if(candidates>0&&!forceImport){hold(user,id,"MANUAL_DUPLICATE_REVIEW");return;}
   ledger=UUID.randomUUID();db.update("INSERT INTO ledger_entries(id,user_id,client_id,amount_won,purchased_at,category,included,source,original_category,original_included) VALUES (?,?,?,?,?,'OTHER',true,'CODEF','OTHER',true)",ledger,user,UUID.randomUUID(),amount,r.get("used_date"));
   db.update("UPDATE financial_transactions SET ledger_id=? WHERE user_id=? AND id=?",ledger,user,id);changed(user);
  }else if(number(one("ledger_entries",user,ledger).get("amount_won"))!=amount){hold(user,id,"APPROVAL_AMOUNT_CHANGED");return;}
  long requested=number(r.get("cancelled_won"));
  long separate=db.queryForObject("SELECT count(*) FROM financial_transactions t WHERE t.user_id=? AND t.linked_transaction_id=? AND EXISTS(SELECT 1 FROM financial_refund_applications a WHERE a.user_id=t.user_id AND a.financial_transaction_id=t.id)",Long.class,user,id);
  if(separate>0&&requested>0){hold(user,id,"REFUND_CONFLICT");return;}
  if(separate==0)refund(user,id,ledger,requested);
  else db.update("UPDATE financial_transactions SET status='APPLIED',issue=NULL WHERE user_id=? AND id=?",user,id);
 }
 private void refund(UUID user,UUID id,UUID ledger,long cumulative){
  var r=one("financial_transactions",user,id);var entry=one("ledger_entries",user,ledger);
  long own=db.queryForObject("SELECT COALESCE(max(cumulative_won),0) FROM financial_refund_applications WHERE user_id=? AND financial_transaction_id=?",Long.class,user,id);
  long total=db.queryForObject("SELECT COALESCE(sum(amount_won),0) FROM cancellations WHERE user_id=? AND transaction_id=?",Long.class,user,ledger);
  // User-confirmed manual matches already have an explicit, known refund total.
  if(Boolean.TRUE.equals(r.get("manual_link"))&&own==0)own=total;
  if(cumulative<own){hold(user,id,"REFUND_REGRESSION");return;}
  long delta=cumulative-own;
  if(delta>number(entry.get("amount_won"))-total){hold(user,id,"REFUND_CONFLICT");return;}
  if(delta>0){
   UUID cancellation=UUID.randomUUID();Long cardRefund=entry.get("card_id")==null?null:delta;
   db.update("INSERT INTO cancellations(id,user_id,transaction_id,client_id,amount_won,card_refund_won) VALUES (?,?,?,?,?,?)",cancellation,user,ledger,UUID.randomUUID(),delta,cardRefund);
   db.update("INSERT INTO financial_refund_applications(user_id,financial_transaction_id,cancellation_id,cumulative_won) VALUES (?,?,?,?)",user,id,cancellation,cumulative);changed(user);
  }
  db.update("UPDATE financial_transactions SET status='APPLIED',issue=NULL WHERE user_id=? AND id=?",user,id);
 }
 public Map<String,Object> resolve(UUID user,UUID id,Resolution in){return tx.execute(status->{
  lock(user);var r=one("financial_transactions",user,id);
  if(in==null||in.version()==null||in.version()!=number(r.get("version")))throw new ApiError(409,"VERSION_CONFLICT");
  if(!r.get("status").equals("HELD"))throw new ApiError(409,"TRANSACTION_NOT_HELD");
  if("IGNORE".equals(in.action())){
   if(r.get("ledger_id")!=null||r.get("linked_transaction_id")!=null)throw new ApiError(409,"APPLIED_EVIDENCE_CANNOT_BE_IGNORED");
   db.update("UPDATE financial_transactions SET status='IGNORED' WHERE user_id=? AND id=?",user,id);
  }
  else if("LINK_CANCELLATION".equals(in.action())&&r.get("kind").equals("CANCEL_EVENT")&&"CANCELLATION_MATCH_REQUIRED".equals(r.get("issue"))){
   var original=one("financial_transactions",user,in.approvalId());
   if(!original.get("connection_id").equals(r.get("connection_id"))||!Objects.equals(original.get("card_mask"),r.get("card_mask"))||!original.get("kind").equals("APPROVAL")||!original.get("status").equals("APPLIED")||original.get("ledger_id")==null)throw new ApiError(422,"INVALID_CANCELLATION_MATCH");
   if(original.get("used_date")==null||r.get("used_date")==null||((java.sql.Date)original.get("used_date")).after((java.sql.Date)r.get("used_date"))||original.get("amount_won")==null||r.get("cancelled_won")==null||original.get("cancelled_won")==null||number(original.get("cancelled_won"))!=0)throw new ApiError(422,"INVALID_CANCELLATION_MATCH");
   long refunded=db.queryForObject("SELECT COALESCE(sum(amount_won),0) FROM cancellations WHERE user_id=? AND transaction_id=?",Long.class,user,original.get("ledger_id"));
   if(number(r.get("cancelled_won"))<=0||number(r.get("cancelled_won"))>number(original.get("amount_won"))-refunded)throw new ApiError(422,"INVALID_CANCELLATION_MATCH");
   db.update("UPDATE financial_transactions SET linked_transaction_id=? WHERE user_id=? AND id=?",in.approvalId(),user,id);apply(user,id,false);
  }else if("MANUAL_DUPLICATE_REVIEW".equals(r.get("issue"))&&"LINK_MANUAL".equals(in.action())){
   var entry=one("ledger_entries",user,in.ledgerId());
   if(!entry.get("source").equals("MANUAL")||!entry.get("purchased_at").equals(r.get("used_date"))||number(entry.get("amount_won"))!=number(r.get("amount_won"))||entry.get("card_charge_won")!=null&&number(entry.get("card_charge_won"))!=number(entry.get("amount_won")))throw new ApiError(422,"INVALID_MANUAL_MATCH");
   if(db.queryForObject("SELECT count(*) FROM financial_transactions WHERE user_id=? AND ledger_id=?",Long.class,user,in.ledgerId())>0)throw new ApiError(409,"LEDGER_ALREADY_LINKED");
   db.update("UPDATE financial_transactions SET ledger_id=?,manual_link=true WHERE user_id=? AND id=?",in.ledgerId(),user,id);
   // Real provider evidence must never inherit virtual-card benefit rules.
   db.update("UPDATE ledger_entries SET performance_eligible=NULL,performance_rule_version=NULL,benefit_rate_bp=NULL,benefit_rule_version=NULL WHERE user_id=? AND id=?",user,in.ledgerId());
   apply(user,id,false);
  }else if("MANUAL_DUPLICATE_REVIEW".equals(r.get("issue"))&&"IMPORT_SEPARATELY".equals(in.action()))apply(user,id,true);
  else throw new ApiError(422,"INVALID_RECONCILIATION_ACTION");
  db.update("UPDATE financial_transactions SET version=version+1,updated_at=now() WHERE user_id=? AND id=?",user,id);changed(user);
  return publicRow(one("financial_transactions",user,id));
 });}
}
