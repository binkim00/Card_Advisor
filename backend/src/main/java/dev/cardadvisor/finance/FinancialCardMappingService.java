package dev.cardadvisor.finance;
import dev.cardadvisor.api.ApiError;
import dev.cardadvisor.domain.ActualCardRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** User-confirmed associations only. Never write transaction/ledger calculation inputs. */
@Service @Transactional
public class FinancialCardMappingService {
 private final JdbcTemplate db;
 public FinancialCardMappingService(JdbcTemplate db){this.db=db;}
 public record Input(String sourceKey,UUID cardId,Long expectedVersion,Long expectedConnectionVersion,Boolean confirmed){}
 private void lock(UUID user){if(db.queryForList("SELECT id FROM app_users WHERE id=? FOR UPDATE",user).isEmpty())throw new ApiError(401,"SESSION_EXPIRED");}
 private Map<String,Object> connection(UUID user,UUID id){var rows=db.queryForList("SELECT * FROM financial_connections WHERE user_id=? AND id=? AND archived_at IS NULL",user,id);if(rows.isEmpty())throw new ApiError(404,"FINANCIAL_CONNECTION_NOT_FOUND");return rows.get(0);}
 private String key(String mask,String label){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((mask+"\n"+label).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("GROUP_KEY_FAILED");}}
 private boolean issuerMatches(String org,String source){return org.equals("0301")?ActualCardRules.KB.equals(source):org.equals("0313")&&(ActualCardRules.LOG.equals(source)||ActualCardRules.GO.equals(source));}
 private List<Map<String,Object>> groups(UUID user,UUID id){
  var rows=db.queryForList("SELECT card_mask AS mask,COALESCE(card_label,'') AS label,count(*) AS transaction_count FROM financial_transactions WHERE user_id=? AND connection_id=? AND card_mask IS NOT NULL GROUP BY card_mask,COALESCE(card_label,'') ORDER BY card_mask,COALESCE(card_label,'')",user,id);
  for(var row:rows){row.put("sourceKey",key((String)row.get("mask"),(String)row.get("label")));var mappings=db.queryForList("SELECT m.version,m.card_id,c.payload->>'name' AS mapped_name FROM financial_card_mappings m LEFT JOIN cards c ON c.user_id=m.user_id AND c.id=m.card_id WHERE m.user_id=? AND m.connection_id=? AND m.source_key=?",user,id,row.get("sourceKey"));var mapping=mappings.isEmpty()?null:mappings.get(0);row.put("version",mapping==null?0L:mapping.get("version"));row.put("cardId",mapping==null?null:mapping.get("card_id"));row.put("mappedName",mapping==null?null:mapping.get("mapped_name"));row.put("calculationStatus",mapping==null||mapping.get("card_id")==null?"NOT_LINKED":"RULE_REVIEW_REQUIRED");}
  return rows;
 }
 public Map<String,Object> list(UUID user,UUID id){lock(user);var c=connection(user,id);String org=(String)c.get("organization");var targets=new ArrayList<Map<String,Object>>();for(var row:db.queryForList("SELECT id,payload->>'name' AS name,payload->>'source' AS source FROM cards WHERE user_id=? ORDER BY id",user))if(issuerMatches(org,(String)row.get("source")))targets.add(row);
  return Map.of("groups",groups(user,id),"targets",targets,"organization",org,"connectionVersion",c.get("version"),"policy","ASSOCIATION_ONLY_NO_AUTOMATIC_CALCULATION");
 }
 public Map<String,Object> save(UUID user,UUID id,Input in){
  lock(user);var c=connection(user,id);
  if(in==null||in.expectedVersion()==null||in.expectedVersion()<0||in.expectedConnectionVersion()==null||!Boolean.TRUE.equals(in.confirmed())||in.sourceKey()==null||!in.sourceKey().matches("[a-f0-9]{64}"))throw new ApiError(422,"MAPPING_CONFIRMATION_REQUIRED");
  if(!c.get("state").equals("ACTIVE")||!Objects.equals(((Number)c.get("version")).longValue(),in.expectedConnectionVersion()))throw new ApiError(409,"CONNECTION_CHANGED");
  var g=groups(user,id).stream().filter(x->in.sourceKey().equals(x.get("sourceKey"))).findFirst().orElseThrow(()->new ApiError(404,"FINANCIAL_CARD_GROUP_NOT_FOUND"));
  long version=((Number)g.get("version")).longValue();if(version!=in.expectedVersion())throw new ApiError(409,"MAPPING_VERSION_CONFLICT");
  if(in.cardId()!=null){var cards=db.queryForList("SELECT payload->>'source' AS source FROM cards WHERE user_id=? AND id=?",user,in.cardId());if(cards.isEmpty())throw new ApiError(404,"CARD_NOT_FOUND");if(!issuerMatches((String)c.get("organization"),(String)cards.get(0).get("source")))throw new ApiError(422,"CARD_ISSUER_OR_RULE_MISMATCH");}
  if(Objects.equals(g.get("cardId"),in.cardId()))return list(user,id);
  db.update("INSERT INTO financial_card_mappings(user_id,connection_id,source_key,source_mask,source_label,card_id,version,confirmed_at) VALUES(?,?,?,?,?,?,1,now()) ON CONFLICT(user_id,connection_id,source_key) DO UPDATE SET card_id=EXCLUDED.card_id,version=financial_card_mappings.version+1,confirmed_at=now()",user,id,in.sourceKey(),g.get("mask"),g.get("label"),in.cardId());
  db.update("UPDATE app_users SET revision=revision+1 WHERE id=?",user);return list(user,id);
 }
}
