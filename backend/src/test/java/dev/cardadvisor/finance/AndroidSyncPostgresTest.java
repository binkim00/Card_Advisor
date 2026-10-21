package dev.cardadvisor.finance;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.cardadvisor.finance.CodefApprovalNormalizerTest.*;

/** Synthetic provider/fault controls exist only on the test classpath. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"codef.config-file=","codef.storage-key-file="})
@Import({FinancialConnectionsPostgresTest.LocalProviderConfiguration.class,AndroidSyncPostgresTest.Faults.class})
@EnabledIfEnvironmentVariable(named="RUN_ANDROID_SYNC_TEST",matches="true")
class AndroidSyncPostgresTest {
 @TestConfiguration static class Faults {
  @Bean FilterRegistrationBean<OncePerRequestFilter> syncFaults(JdbcTemplate db,FinancialConnectionsPostgresTest.Stub provider){
   var first=new AtomicBoolean(true);var malformed=new AtomicBoolean();var lost=new AtomicBoolean();
   return new FilterRegistrationBean<>(new OncePerRequestFilter(){
    protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws java.io.IOException,ServletException{
     String path=req.getRequestURI();
     if(path.startsWith("/v1/fixture/sync/")){
      String header=req.getHeader("Authorization");if(header==null||!header.startsWith("Bearer ")||!req.getMethod().equals("POST")){res.setStatus(401);return;}
      var users=db.queryForList("SELECT s.user_id FROM sessions s JOIN app_users u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>now() AND u.email LIKE 'sync-ui-%@example.test'",dev.cardadvisor.auth.Passwords.sha(header.substring(7)));
      if(users.size()!=1){res.setStatus(403);return;}var u=users.get(0).get("user_id");String mode=path.substring(path.lastIndexOf('/')+1);
      switch(mode){
       case "mappingconflict"->db.update("UPDATE financial_card_mappings SET version=version+1 WHERE user_id=?",u);
       case "conflict"->db.update("UPDATE financial_transactions SET version=version+1 WHERE user_id=? AND amount_won=50000",u);
       case "malformed"->malformed.set(true);
       case "running"->db.update("INSERT INTO financial_sync_runs(id,user_id,connection_id,idempotency_key,from_date,to_date,connection_version,status,error_code) SELECT ?,user_id,id,?,?,?,version,'RUNNING','TEST_ONLY' FROM financial_connections WHERE user_id=?",UUID.randomUUID(),UUID.randomUUID(),DATE,DATE,u);
       case "finish"->db.update("DELETE FROM financial_sync_runs WHERE user_id=? AND error_code='TEST_ONLY'",u);
       case "unknown"->db.update("UPDATE financial_connections SET state='UNKNOWN',version=version+1 WHERE user_id=?",u);
       case "active"->db.update("UPDATE financial_connections SET state='ACTIVE',version=version+1 WHERE user_id=?",u);
       case "expire"->{db.update("UPDATE sessions SET expires_at=now()-interval '1 second' WHERE user_id=?",u);db.update("UPDATE refresh_sessions SET revoked_at=now() WHERE user_id=?",u);}
       case "initial","partial","failed","lost"->{
        var b=new FinancialConnectionsPostgresTest.Behavior();boolean partial=mode.equals("partial")||mode.equals("lost");
        b.approvalData=JSON.createArrayNode().add(row("A1","50000","0","")).add(row("A2","70000","0","")).add(row("A3","90000",partial?"2":"0",partial?"20000":"").put("resPaymentType","2").put("resInstallmentMonth","3")).add(row("C1","-20000","1","")).add(row("U1","30000","2",""));
        for(var n:b.approvalData)((com.fasterxml.jackson.databind.node.ObjectNode)n).put("resMemberStoreName","테스트 가맹점").put("resCardName","가상 테스트 카드");
        if("true".equals(System.getenv("TEST_KB_THREE_DIGIT_MASK")))for(var n:b.approvalData)((com.fasterxml.jackson.databind.node.ObjectNode)n).put("resCardNo","*************123");
        if(mode.equals("failed"))b.status=500;if(mode.equals("lost"))lost.set(true);provider.behaviors.add(b);
       }
       default->{res.setStatus(404);return;}
      }
      res.setStatus(204);return;
     }
     if(path.matches("/v1/finance/connections/[^/]+/transactions")&&req.getMethod().equals("GET")){
      if(first.compareAndSet(true,false)){res.setStatus(500);res.setContentType("application/json");res.getWriter().write("{\"error\":{\"code\":\"SYNTHETIC_READ_FAULT\"}}");return;}
      if(malformed.compareAndSet(true,false)){res.setContentType("application/json");res.getWriter().write("[{\"id\":\"bad\"}]");return;}
     }
     if(path.matches("/v1/finance/connections/[^/]+/sync")&&req.getMethod().equals("POST")&&lost.compareAndSet(true,false)){
      // Let the real service commit, discard its response, return a transport-layer failure.
      var wrapped=new org.springframework.web.util.ContentCachingResponseWrapper(res);chain.doFilter(req,wrapped);
      res.reset();res.setStatus(500);res.setContentType("application/json");res.getWriter().write("{\"error\":{\"code\":\"SYNTHETIC_RESPONSE_LOST\"}}");return;
     }
     chain.doFilter(req,res);
    }
   });
  }
 }
 @LocalServerPort int port;@Autowired JdbcTemplate db;@Autowired FinancialConnectionsPostgresTest.Stub provider;
 private final String adb=System.getenv("LOCALAPPDATA")+"/Android/Sdk/platform-tools/adb.exe";
 private final String device=System.getenv().getOrDefault("ANDROID_TEST_DEVICE","emulator-5554");
 String command(String...args)throws Exception{
  var cmd=new ArrayList<String>(List.of(adb,"-s",device));cmd.addAll(List.of(args));var p=new ProcessBuilder(cmd).redirectErrorStream(true).start();
  var output=CompletableFuture.supplyAsync(()->{try{return new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);}catch(Exception e){return "";}});
  if(!p.waitFor(300,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("Android sync test timed out");}
  var result=output.get(5,TimeUnit.SECONDS);assertEquals(0,p.exitValue(),"Android command failed; raw output withheld");return result;
 }
 @Test void appSyncReconciliation()throws Exception{
  String run=UUID.randomUUID().toString(),email="sync-ui-"+run+"@example.test";
  var report=new LinkedHashMap<String,Object>();report.put("runId",run);report.put("syntheticOnly",true);report.put("device",device);report.put("passed",false);
  try{
   report.put("model",command("shell","getprop","ro.product.model").trim());report.put("androidVersion",command("shell","getprop","ro.build.version.release").trim());report.put("physicalDevice",!command("shell","getprop","ro.kernel.qemu").trim().equals("1"));
   command("reverse","tcp:18080","tcp:"+port);command("shell","am","force-stop","dev.cardadvisor.android");
   String result=command("shell","am","instrument","-w","-r","-e","class","dev.cardadvisor.android.FinanceSyncFlowTest#syncReconcileAndRecover","-e","runId",run,"dev.cardadvisor.android.test/androidx.test.runner.AndroidJUnitRunner");
   report.put("failureLocations",result.lines().filter(x->x.matches(".*FinanceSyncFlowTest\\.kt:[0-9]+.*")).map(x->x.replaceAll(".*FinanceSyncFlowTest\\.kt:([0-9]+).*","FinanceSyncFlowTest.kt:$1")).distinct().toList());
   report.put("steps",result.lines().filter(x->x.matches("INSTRUMENTATION_STATUS: syncStep=[a-z_]+\\s*")).map(x->x.substring(x.indexOf('=')+1).trim()).toList());
   report.put("failureTypes",java.util.regex.Pattern.compile("(?:java|androidx|android|org\\.junit)\\.[A-Za-z0-9_.$]+(?:Exception|Error)\\b").matcher(result).results().map(java.util.regex.MatchResult::group).distinct().toList());
   report.put("runnerCodes",result.lines().filter(x->x.matches("INSTRUMENTATION_(?:STATUS_)?CODE: -?[0-9]+\\s*")).toList());report.put("processCrashed",result.contains("Process crashed"));
   assertTrue(result.contains("OK (1 test)"),"Android sync flow failed; see sanitized locations");
   UUID u=db.queryForObject("SELECT id FROM app_users WHERE email=?",UUID.class,email);
   assertEquals(4L,db.queryForObject("SELECT count(*) FROM ledger_entries WHERE user_id=?",Long.class,u));
   assertEquals(2L,db.queryForObject("SELECT count(*) FROM cancellations WHERE user_id=?",Long.class,u));
   assertEquals(40000L,db.queryForObject("SELECT sum(amount_won) FROM cancellations WHERE user_id=?",Long.class,u));
   assertEquals(170000L,db.queryForObject("SELECT sum(e.amount_won-COALESCE((SELECT sum(c.amount_won) FROM cancellations c WHERE c.user_id=e.user_id AND c.transaction_id=e.id),0)) FROM ledger_entries e WHERE e.user_id=? AND e.included",Long.class,u));
   assertEquals(5L,db.queryForObject("SELECT count(*) FROM financial_transactions WHERE user_id=?",Long.class,u));
   assertEquals(0L,db.queryForObject("SELECT count(*) FROM financial_transactions WHERE user_id=? AND status='HELD'",Long.class,u));
   assertEquals(1L,db.queryForObject("SELECT count(*) FROM financial_transactions WHERE user_id=? AND status='IGNORED'",Long.class,u));
   assertEquals(3,db.queryForObject("SELECT installments FROM financial_transactions WHERE user_id=? AND amount_won=90000",Integer.class,u));
   assertEquals(1L,db.queryForObject("SELECT count(*) FROM ledger_entries WHERE user_id=? AND amount_won=50000 AND category='FOOD' AND included",Long.class,u));
   assertEquals(5L,db.queryForObject("SELECT count(*) FROM financial_sync_runs WHERE user_id=?",Long.class,u));
   assertEquals(1L,db.queryForObject("SELECT count(*) FROM financial_sync_runs WHERE user_id=? AND status='FAILED'",Long.class,u));
   assertEquals(6,provider.calls.size()); // one connection + five explicit reads, including lost response
   for(String value:db.queryForList("SELECT row_to_json(t)::text FROM financial_transactions t WHERE user_id=?",String.class,u)){assertFalse(value.contains("1234567812345678"));assertFalse(value.contains("synthetic-cid"));assertFalse(value.contains("SYNTHETIC_PASSWORD_ONLY"));}
   report.put("passed",true);report.put("providerCalls",6);report.put("syncRuns",5);report.put("ledgerEntries",4);report.put("cancellations",2);report.put("refundWon",40000);report.put("budgetSpentWon",170000);report.put("pending",0);
   report.put("verified",List.of("dateValidation","read500Recovery","versionConflictDraft","manualMatch","separatePurchase","explicitCancelMatch","unknownNotZero","installmentTotal","repeatNoDuplicate","responseLostRecovery","malformedReadBlocked","runningBlocked","unknownConnectionBlocked","freshLogin","expiredSession"));
  }finally{
   report.put("finalStoredStates",db.queryForList("SELECT t.kind,t.status,t.issue,t.amount_won,t.cancelled_won,t.version FROM financial_transactions t JOIN app_users u ON u.id=t.user_id WHERE u.email=? ORDER BY t.kind,t.amount_won",email));
   db.update("DELETE FROM app_users WHERE email=?",email);report.put("testAccountRemoved",db.queryForObject("SELECT count(*) FROM app_users WHERE email=?",Long.class,email)==0);command("reverse","tcp:18080","tcp:18080");
   Path dir=Path.of("../.run"),archive=dir.resolve("android-sync").resolve(run);Files.createDirectories(archive);String evidence=JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report);Files.writeString(dir.resolve("android-sync-verification.json"),evidence);Files.writeString(archive.resolve("verification.json"),evidence);
  }
 }
}
