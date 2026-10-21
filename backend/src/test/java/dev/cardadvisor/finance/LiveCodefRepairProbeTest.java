package dev.cardadvisor.finance;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
/** Explicit operator-run real sync. Never prints values or credentials. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named="RUN_LIVE_CODEF_REPAIR",matches="true")
class LiveCodefRepairProbeTest {
 @Autowired JdbcTemplate db;@Autowired FinancialSyncService service;
 long count(UUID user,String table){return db.queryForObject("SELECT count(*) FROM "+table+" WHERE user_id=?",Long.class,user);}
 @Test void repairAndReplay(){
  var list=db.queryForList("SELECT c.*,s.from_date,s.to_date FROM financial_connections c JOIN LATERAL (SELECT from_date,to_date FROM financial_sync_runs WHERE user_id=c.user_id AND connection_id=c.id ORDER BY created_at DESC LIMIT 1)s ON true WHERE c.environment='DEMO' AND c.organization='0301' AND c.state='ACTIVE'");Assertions.assertEquals(1,list.size());var c=list.get(0);var user=(UUID)c.get("user_id");var id=(UUID)c.get("id");
  var oldIds=db.queryForList("SELECT id FROM financial_transactions WHERE user_id=? AND connection_id=?",UUID.class,user,id);Assertions.assertEquals(33,oldIds.size());
  var input=new FinancialSyncService.Input(((java.sql.Date)c.get("from_date")).toLocalDate(),((java.sql.Date)c.get("to_date")).toLocalDate(),((Number)c.get("version")).longValue());
  var first=service.sync(user,id,UUID.randomUUID(),input);System.out.println("SAFE_REPAIR_FIRST="+first.get("status")+",applied="+first.get("applied")+",held="+first.get("held")+",error="+first.get("error_code"));
  Assertions.assertNotEquals("FAILED",first.get("status"));long ledger=count(user,"ledger_entries"),refunds=count(user,"cancellations");
  if("true".equals(System.getenv("LIVE_CODEF_SINGLE_READ"))){System.out.println("SAFE_LABEL_COUNTS="+db.queryForMap("SELECT count(*) FILTER(WHERE merchant_name IS NOT NULL) AS merchant_count,count(*) FILTER(WHERE card_label IS NOT NULL) AS card_count FROM financial_transactions WHERE user_id=? AND connection_id=?",user,id));return;}
  var next=service.sync(user,id,UUID.randomUUID(),input);Assertions.assertNotEquals("FAILED",next.get("status"));Assertions.assertEquals(ledger,count(user,"ledger_entries"));Assertions.assertEquals(refunds,count(user,"cancellations"));Assertions.assertEquals(new HashSet<>(oldIds),new HashSet<>(db.queryForList("SELECT id FROM financial_transactions WHERE user_id=? AND connection_id=?",UUID.class,user,id)));
  Assertions.assertEquals(0L,db.queryForObject("SELECT count(*) FROM financial_transactions WHERE user_id=? AND connection_id=? AND issue='MISSING_IDENTITY'",Long.class,user,id));
  System.out.println("SAFE_REPAIR_RESULT=stableRows33,noDuplicateLedger=true,noDuplicateRefund=true,missingIdentity0");
 }
}
