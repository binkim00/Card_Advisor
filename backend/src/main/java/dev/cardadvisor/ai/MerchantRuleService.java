package dev.cardadvisor.ai;
import dev.cardadvisor.api.*;import dev.cardadvisor.auth.Passwords;import dev.cardadvisor.domain.Categories;
import java.util.*;import java.text.Normalizer;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.jdbc.core.JdbcTemplate;
/** Exact merchant-name memory is a user-owned suggestion, never an automatic ledger edit. */
@Service @Transactional public class MerchantRuleService {
 private final JdbcTemplate db;public MerchantRuleService(JdbcTemplate db){this.db=db;}
 public record Rule(boolean available,String category,long version){}
 public record Input(String category,Long expectedVersion,Boolean confirmed){}
 private String key(UUID user,UUID transaction){
  if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM ledger_entries WHERE user_id=? AND id=?)",Boolean.class,user,transaction)))throw new ApiError(404,"NOT_FOUND");
  var rows=db.queryForList("SELECT merchant_name FROM financial_transactions WHERE user_id=? AND ledger_id=? AND kind='APPROVAL' AND status='APPLIED'",user,transaction);
  if(rows.size()!=1||!(rows.get(0).get("merchant_name") instanceof String name)||name.isBlank()||name.length()>512)return null;
  String normalized=Normalizer.normalize(name,Normalizer.Form.NFKC).strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
  return Passwords.sha(user+"|"+normalized);
 }
 private Rule read(UUID user,String key){if(key==null)return new Rule(false,null,0);var rows=db.queryForList("SELECT category,version FROM merchant_category_rules WHERE user_id=? AND merchant_key=?",user,key);return rows.isEmpty()?new Rule(true,null,0):new Rule(true,(String)rows.get(0).get("category"),((Number)rows.get(0).get("version")).longValue());}
 public Rule get(UUID user,UUID transaction){return read(user,key(user,transaction));}
 public Rule save(UUID user,UUID transaction,Input in){
  db.queryForObject("SELECT revision FROM app_users WHERE id=? FOR UPDATE",Long.class,user);
  String key=key(user,transaction);if(key==null)throw new ApiError(422,"MERCHANT_UNAVAILABLE");
  if(!Boolean.TRUE.equals(in.confirmed())||in.expectedVersion()==null||in.expectedVersion()<0||(in.category()!=null&&!Categories.ALL.contains(in.category())))throw new ApiError(422,"INVALID_MERCHANT_RULE");
  Rule old=read(user,key);if(old.version()!=in.expectedVersion())throw new ApiError(409,"VERSION_CONFLICT");
  if(Objects.equals(old.category(),in.category()))return old;
  db.update("INSERT INTO merchant_category_rules(user_id,merchant_key,category,version) VALUES(?,?,?,1) ON CONFLICT(user_id,merchant_key) DO UPDATE SET category=excluded.category,version=merchant_category_rules.version+1,updated_at=now()",user,key,in.category());
  return read(user,key);
 }
}
