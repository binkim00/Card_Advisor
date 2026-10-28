package dev.cardadvisor.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.cardadvisor.account.CardCatalogService;
import dev.cardadvisor.api.ApiError;
import dev.cardadvisor.domain.ActualCardRules;
import dev.cardadvisor.domain.RecommendationEngine.Card;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class HeldCardService {
 public static final String UNVERIFIED="IMPORTED_UNVERIFIED";
 private final JdbcTemplate db;private final CodefClient provider;private final ConnectedIdCipher cipher;
 private final CardCatalogService catalog;private final TransactionTemplate tx;private final ObjectMapper json=new ObjectMapper();
 public HeldCardService(JdbcTemplate db,CodefClient provider,ConnectedIdCipher cipher,CardCatalogService catalog,PlatformTransactionManager manager){this.db=db;this.provider=provider;this.cipher=cipher;this.catalog=catalog;tx=new TransactionTemplate(manager);}
 public record Lookup(Long expectedConnectionVersion,Boolean confirmed){}
 public record Register(Long expectedVersion,Long expectedConnectionVersion,Boolean confirmed,String productId,String expectedRuleVersion){}
 private void lock(UUID u){if(db.queryForList("SELECT id FROM app_users WHERE id=? FOR UPDATE",u).isEmpty())throw new ApiError(401,"SESSION_EXPIRED");}
 private Map<String,Object> connection(UUID u,UUID c){var rows=db.queryForList("SELECT * FROM financial_connections WHERE user_id=? AND id=? AND archived_at IS NULL",u,c);if(rows.isEmpty())throw new ApiError(404,"FINANCIAL_CONNECTION_NOT_FOUND");return rows.get(0);}
 private void active(Map<String,Object> c,Long version){if(version==null||!Objects.equals(((Number)c.get("version")).longValue(),version)||!"ACTIVE".equals(c.get("state")))throw new ApiError(409,"CONNECTION_CHANGED");}
 private void scope(Map<String,Object> c){provider.requireAvailable();cipher.requireConfigured();if(!provider.environment().equals(c.get("environment"))||!provider.scope().equals(c.get("provider_scope")))throw new ApiError(409,"PROVIDER_CONFIGURATION_CHANGED");}
 private List<ActualCardRules.Product> products(String org){return ActualCardRules.PRODUCTS.stream().filter(p->org.equals("0301")?p.id().startsWith("KB_"):org.equals("0313")&&p.id().startsWith("HANA_")).toList();}
 private Map<String,Object> snapshot(UUID u,UUID c){
  var connection=connection(u,c);
  var rows=db.queryForList("SELECT id,display_name AS name,masked_number AS mask,card_type AS type,current,version,card_id AS \"cardId\",fetched_at AS \"fetchedAt\" FROM financial_held_cards WHERE user_id=? AND connection_id=? AND (current OR card_id IS NOT NULL) ORDER BY display_name,id",u,c);
  return Map.of("connectionVersion",connection.get("version"),"state",connection.get("state"),"cards",rows,"products",products((String)connection.get("organization")));
 }
 public Map<String,Object> list(UUID u,UUID c){return tx.execute(s->{lock(u);return snapshot(u,c);});}
 public Map<String,Object> lookup(UUID u,UUID id,Lookup input){
  if(input==null||!Boolean.TRUE.equals(input.confirmed()))throw new ApiError(422,"HELD_CARD_CONFIRMATION_REQUIRED");
  var before=tx.execute(s->{lock(u);var c=connection(u,id);active(c,input.expectedConnectionVersion());scope(c);return c;});
  String org=(String)before.get("organization");
  String cid=cipher.decrypt(new ConnectedIdCipher.Sealed((String)before.get("key_id"),(byte[])before.get("cid_nonce"),(byte[])before.get("cid_ciphertext")),u,id,(String)before.get("environment"),(String)before.get("provider_scope"),org);
  var normalized=new HeldCardNormalizer().normalize(org,provider.cards(cid,org,"").data());
  return tx.execute(s->{
   lock(u);var latest=connection(u,id);active(latest,input.expectedConnectionVersion());scope(latest);
   var visible=new HashSet<>(db.queryForList("SELECT source_key FROM financial_held_cards WHERE user_id=? AND connection_id=? AND card_id IS NOT NULL",String.class,u,id));
   normalized.forEach(card->visible.add(card.key()));if(visible.size()>200)throw new ApiError(422,"HELD_CARD_LIMIT_EXCEEDED");
   db.update("UPDATE financial_held_cards SET current=false,version=version+1 WHERE user_id=? AND connection_id=?",u,id);
   for(var card:normalized){
    UUID candidate=UUID.nameUUIDFromBytes((u+":"+id+":"+card.key()).getBytes(StandardCharsets.UTF_8));
    db.update("INSERT INTO financial_held_cards(id,user_id,connection_id,source_key,display_name,masked_number,card_type) VALUES(?,?,?,?,?,?,?) ON CONFLICT(user_id,connection_id,source_key) DO UPDATE SET current=true,version=financial_held_cards.version+1,fetched_at=now()",candidate,u,id,card.key(),card.name(),card.mask(),card.type());
   }
   return snapshot(u,id);
  });
 }
 public Map<String,Object> register(UUID u,UUID connection,UUID candidate,Register input){return tx.execute(s->{
  lock(u);var c=connection(u,connection);
  if(input==null||!Boolean.TRUE.equals(input.confirmed())||input.expectedVersion()==null)throw new ApiError(422,"HELD_CARD_CONFIRMATION_REQUIRED");
  active(c,input.expectedConnectionVersion());
  var rows=db.queryForList("SELECT * FROM financial_held_cards WHERE user_id=? AND connection_id=? AND id=?",u,connection,candidate);
  if(rows.isEmpty())throw new ApiError(404,"HELD_CARD_NOT_FOUND");var row=rows.get(0);
  String expectedSource=UNVERIFIED;
  if(input.productId()!=null){var p=products((String)c.get("organization")).stream().filter(x->x.id().equals(input.productId())).findFirst().orElseThrow(()->new ApiError(422,"CARD_ISSUER_OR_RULE_MISMATCH"));if(!p.ruleVersion().equals(input.expectedRuleVersion()))throw new ApiError(409,"PRODUCT_VERSION_CHANGED");expectedSource=p.ruleVersion();}
  else if(input.expectedRuleVersion()!=null)throw new ApiError(422,"INVALID_PRODUCT_SELECTION");
  if(row.get("card_id")!=null){String existing=db.queryForObject("SELECT payload->>'source' FROM cards WHERE user_id=? AND id=?",String.class,u,row.get("card_id"));if(!expectedSource.equals(existing))throw new ApiError(409,"HELD_CARD_ALREADY_REGISTERED");return snapshot(u,connection);}
  if(!Boolean.TRUE.equals(row.get("current"))||!Objects.equals(((Number)row.get("version")).longValue(),input.expectedVersion()))throw new ApiError(409,"HELD_CARD_CHANGED");
  if(db.queryForObject("SELECT count(*) FROM financial_held_cards WHERE user_id=? AND connection_id=? AND masked_number=? AND card_id IS NOT NULL AND id<>?",Long.class,u,connection,row.get("masked_number"),candidate)>0)throw new ApiError(409,"AMBIGUOUS_HELD_CARD_IDENTITY");
  UUID card;
  if(input.productId()!=null){var registered=(Card)catalog.register(u,new CardCatalogService.Register(input.productId(),input.expectedRuleVersion()));card=UUID.fromString(registered.id());}
  else{
   card=UUID.nameUUIDFromBytes((u+":held:"+candidate).getBytes(StandardCharsets.UTF_8));
   var value=new Card(card.toString(),(String)row.get("display_name"),null,null,null,null,null,null,UNVERIFIED);
   String encoded;try{encoded=json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("HELD_CARD_ENCODING_FAILED");}
   db.update("INSERT INTO cards(id,user_id,payload) VALUES(?,?,?::jsonb)",card,u,encoded);
   db.update("UPDATE app_users SET revision=revision+1 WHERE id=?",u);
  }
  db.update("UPDATE financial_held_cards SET card_id=?,version=version+1 WHERE user_id=? AND connection_id=? AND id=?",card,u,connection,candidate);
  return snapshot(u,connection);
 });}
}
