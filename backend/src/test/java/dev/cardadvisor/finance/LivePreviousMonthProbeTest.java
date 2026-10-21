package dev.cardadvisor.finance;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.time.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named="RUN_LIVE_PREVIOUS_MONTH",matches="true")
class LivePreviousMonthProbeTest {
 @Autowired JdbcTemplate db;@Autowired FinancialSyncService service;
 long count(UUID user,String table){return db.queryForObject("SELECT count(*) FROM "+table+" WHERE user_id=?",Long.class,user);}
 @Test void loadAndRepeatWithoutDuplicating(){
  var connections=db.queryForList("SELECT * FROM financial_connections WHERE environment='DEMO' AND organization='0301' AND state='ACTIVE'");Assertions.assertEquals(1,connections.size());var c=connections.get(0);UUID user=(UUID)c.get("user_id"),connection=(UUID)c.get("id");
  var pending=db.queryForList("SELECT id,used_date FROM financial_transactions WHERE user_id=? AND connection_id=? AND status='HELD' AND issue='CANCELLATION_MATCH_REQUIRED' ORDER BY created_at LIMIT 1",user,connection);Assertions.assertEquals(1,pending.size());
  UUID cancellation=(UUID)pending.get(0).get("id");YearMonth month=YearMonth.from(((java.sql.Date)pending.get(0).get("used_date")).toLocalDate()).minusMonths(1);
  String requestedMonth=System.getenv("LIVE_CODEF_MONTH");if(requestedMonth!=null){YearMonth explicit=YearMonth.parse(requestedMonth);Assertions.assertTrue(explicit.isBefore(YearMonth.from(((java.sql.Date)pending.get(0).get("used_date")).toLocalDate())));month=explicit;}
  var old=new HashSet<>(db.queryForList("SELECT id FROM financial_transactions WHERE user_id=? AND connection_id=?",UUID.class,user,connection));long oldRefunds=count(user,"cancellations");
  var in=new FinancialSyncService.Input(month.atDay(1),month.atEndOfMonth(),((Number)c.get("version")).longValue());
  var first=service.sync(user,connection,UUID.randomUUID(),in);System.out.println("SAFE_PREVIOUS_FIRST="+first.get("status")+",received="+first.get("received")+",applied="+first.get("applied")+",held="+first.get("held")+",error="+first.get("error_code"));Assertions.assertNotEquals("FAILED",first.get("status"));
  long rows=count(user,"financial_transactions"),ledger=count(user,"ledger_entries"),refunds=count(user,"cancellations");var next=service.sync(user,connection,UUID.randomUUID(),in);Assertions.assertNotEquals("FAILED",next.get("status"));Assertions.assertEquals(rows,count(user,"financial_transactions"));Assertions.assertEquals(ledger,count(user,"ledger_entries"));Assertions.assertEquals(refunds,count(user,"cancellations"));Assertions.assertTrue(new HashSet<>(db.queryForList("SELECT id FROM financial_transactions WHERE user_id=? AND connection_id=?",UUID.class,user,connection)).containsAll(old));
  Assertions.assertNull(db.queryForMap("SELECT linked_transaction_id FROM financial_transactions WHERE user_id=? AND id=?",user,cancellation).get("linked_transaction_id"));
  System.out.println("SAFE_PREVIOUS_REPEAT=stableRowsAndLedgerAndRefunds,oldIdsPreserved=true,originalCancelUnlinked=true,totalRows="+rows+",ledgerRows="+ledger+",newRefundEntries="+(refunds-oldRefunds));
 }
}
