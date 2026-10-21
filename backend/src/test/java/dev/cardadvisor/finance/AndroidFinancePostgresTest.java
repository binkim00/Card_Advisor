package dev.cardadvisor.finance;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
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

/** Test classpath only. Actual Android UI -> real Spring API -> PostgreSQL; loopback fake CODEF. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"codef.config-file=","codef.storage-key-file="})
@Import({FinancialConnectionsPostgresTest.LocalProviderConfiguration.class,AndroidFinancePostgresTest.FaultConfiguration.class})
@EnabledIfEnvironmentVariable(named="RUN_ANDROID_FINANCE_TEST",matches="true")
class AndroidFinancePostgresTest {
 @TestConfiguration static class FaultConfiguration {
  @Bean FilterRegistrationBean<OncePerRequestFilter> firstReadFault(JdbcTemplate db){
   var fired=new AtomicBoolean();
   var filter=new OncePerRequestFilter(){
    protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws java.io.IOException,ServletException{
     if(req.getMethod().equals("POST")&&req.getRequestURI().equals("/v1/fixture/expire")){
      String header=req.getHeader("Authorization");if(header==null||!header.startsWith("Bearer ")){res.setStatus(401);return;}
      var users=db.queryForList("SELECT s.user_id FROM sessions s JOIN app_users u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>now() AND u.email LIKE 'finance-ui-%@example.test'",dev.cardadvisor.auth.Passwords.sha(header.substring(7)));
      if(users.size()!=1){res.setStatus(403);return;}
      db.update("UPDATE sessions SET expires_at=now()-interval '1 second' WHERE user_id=?",users.get(0).get("user_id"));res.setStatus(204);return;
     }
     if(req.getMethod().equals("GET")&&req.getRequestURI().equals("/v1/finance/connections")&&fired.compareAndSet(false,true)){
      res.setStatus(500);res.setContentType("application/json");res.getWriter().write("{\"error\":{\"code\":\"SYNTHETIC_READ_FAULT\"}}");return;
     }
     chain.doFilter(req,res);
    }
   };
   return new FilterRegistrationBean<>(filter);
  }
 }
 @LocalServerPort int port;
 @Autowired JdbcTemplate db;@Autowired FinancialConnectionsPostgresTest.Stub provider;
 private final String adb=System.getenv("LOCALAPPDATA")+"/Android/Sdk/platform-tools/adb.exe";
 private final String device=System.getenv().getOrDefault("ANDROID_TEST_DEVICE","emulator-5554");
 String command(String...args)throws Exception{
  var command=new ArrayList<String>(List.of(adb,"-s",device));command.addAll(List.of(args));
  var process=new ProcessBuilder(command).redirectErrorStream(true).start();
  var output=CompletableFuture.supplyAsync(()->{try{return new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);}catch(Exception e){return "";}});
  if(!process.waitFor(240,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("Android verification timed out");}
  String result=output.get(5,TimeUnit.SECONDS);assertEquals(0,process.exitValue(),"Android command failed; raw output withheld");return result;
 }
 @Test void appApiPostgresLifecycle()throws Exception{
  String run=UUID.randomUUID().toString(),email="finance-ui-"+run+"@example.test";
  var report=new LinkedHashMap<String,Object>();report.put("runId",run);report.put("syntheticOnly",true);report.put("device",device);report.put("passed",false);
  for(int i=1;i<=9;i++){var b=new FinancialConnectionsPostgresTest.Behavior();if(i==5||i==7||i==9)b.status=500;provider.behaviors.add(b);}
  try{
   report.put("model",command("shell","getprop","ro.product.model").trim());
   report.put("androidVersion",command("shell","getprop","ro.build.version.release").trim());
   report.put("physicalDevice",!command("shell","getprop","ro.kernel.qemu").trim().equals("1"));
   command("reverse","tcp:18080","tcp:"+port);command("shell","am","force-stop","dev.cardadvisor.android");
   String result=command("shell","am","instrument","-w","-r","-e","class","dev.cardadvisor.android.FinanceFlowTest#connectedLifecycleAndUnknown","-e","runId",run,"dev.cardadvisor.android.test/androidx.test.runner.AndroidJUnitRunner");
   // Persist only test source line numbers (never messages, UI semantics, HTTP headers or input).
   var lines=result.lines().filter(x->x.matches(".*FinanceFlowTest\\.kt:[0-9]+.*")).map(x->x.replaceAll(".*FinanceFlowTest\\.kt:([0-9]+).*","FinanceFlowTest.kt:$1")).distinct().toList();
   report.put("failureLocations",lines);
   // Class names and static status markers only. Never persist runner messages or UI trees.
   report.put("failureTypes",java.util.regex.Pattern.compile("(?:java|androidx|android|org\\.junit)\\.[A-Za-z0-9_.$]+(?:Exception|Error)\\b").matcher(result).results().map(java.util.regex.MatchResult::group).distinct().toList());
   report.put("runnerCodes",result.lines().filter(x->x.matches("INSTRUMENTATION_(?:STATUS_)?CODE: -?[0-9]+\\s*")).toList());
   report.put("processCrashed",result.contains("Process crashed"));
   report.put("noTestsFound",result.contains("No tests found"));
   report.put("runnerFrames",result.lines().filter(x->x.matches("\\s*at (?:androidx|java|android|org\\.junit)\\.[A-Za-z0-9_.$]+\\([A-Za-z0-9_.]+:[0-9]+\\)\\s*")).limit(16).map(String::trim).toList());
   assertTrue(result.contains("OK (1 test)"),"Android flow failed; see sanitized report locations");
   UUID user=db.queryForObject("SELECT id FROM app_users WHERE email=?",UUID.class,email);
   var rows=db.queryForList("SELECT organization,state,version,cid_nonce,cid_ciphertext FROM financial_connections WHERE user_id=? ORDER BY organization",user);
   assertEquals(4,rows.size());
   for(var row:rows){String org=(String)row.get("organization");assertEquals(org.equals("9999")?"DISCONNECTED":"UNKNOWN",row.get("state"));
    if(org.equals("9996")||org.equals("9997")){assertEquals(12,((byte[])row.get("cid_nonce")).length);assertTrue(((byte[])row.get("cid_ciphertext")).length>16);}else assertNull(row.get("cid_ciphertext"));
   }
   assertEquals(9,provider.calls.size(),"No automatic/repeated mutations allowed");assertEquals(9L,db.queryForObject("SELECT count(*) FROM financial_connection_operations WHERE user_id=?",Long.class,user));
   assertEquals(3L,db.queryForObject("SELECT count(*) FROM financial_connection_operations WHERE user_id=? AND status='UNKNOWN'",Long.class,user));
   for(String value:db.queryForList("SELECT row_to_json(c)::text FROM financial_connections c WHERE user_id=? UNION ALL SELECT row_to_json(o)::text FROM financial_connection_operations o WHERE user_id=?",String.class,user,user)){
    assertFalse(value.contains("SYNTHETIC_PASSWORD_ONLY"));assertFalse(value.contains("synthetic-login"));assertFalse(value.contains("synthetic-cid"));
   }
   report.put("passed",true);report.put("providerCalls",9);report.put("operations",9);report.put("unknownOperations",3);report.put("encryptedUnknownConnections",2);report.put("versionConflictNoProviderCall",true);report.put("readFailureRecovered",true);report.put("expiredSessionExitRecovered",true);
  }finally{
   db.update("DELETE FROM app_users WHERE email=?",email);
   report.put("testAccountRemoved",db.queryForObject("SELECT count(*) FROM app_users WHERE email=?",Long.class,email)==0);
   command("reverse","tcp:18080","tcp:18080");
   Path dir=Path.of("../.run");Files.createDirectories(dir);String evidence=new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report);
   Files.writeString(dir.resolve("android-finance-verification.json"),evidence);
   Path archive=dir.resolve("android-finance").resolve(run);Files.createDirectories(archive);Files.writeString(archive.resolve("verification.json"),evidence);
  }
 }

 @Test void failedRecordControls()throws Exception{
  String run=UUID.randomUUID().toString(),email="failed-ui-"+run+"@example.test";
  var report=new LinkedHashMap<String,Object>();report.put("runId",run);report.put("device",device);report.put("syntheticOnly",true);report.put("passed",false);
  for(int i=1;i<=4;i++){var b=new FinancialConnectionsPostgresTest.Behavior();b.failed=i==1||i==4;provider.behaviors.add(b);}
  try{
   command("reverse","tcp:18080","tcp:"+port);command("shell","am","force-stop","dev.cardadvisor.android");
   String result=command("shell","am","instrument","-w","-r","-e","class","dev.cardadvisor.android.FailedConnectionFlowTest","-e","runId",run,"dev.cardadvisor.android.test/androidx.test.runner.AndroidJUnitRunner");
   report.put("failureLocations",java.util.regex.Pattern.compile("FailedConnectionFlowTest\\.kt:[0-9]+").matcher(result).results().map(java.util.regex.MatchResult::group).distinct().toList());
   report.put("failureTypes",java.util.regex.Pattern.compile("(?:java|androidx|android)\\.[A-Za-z0-9_.$]+(?:Exception|Error)\\b").matcher(result).results().map(java.util.regex.MatchResult::group).distinct().toList());
   assertTrue(result.contains("OK (1 test)"),"Failed-record UI verification failed; raw output withheld");
   UUID user=db.queryForObject("SELECT id FROM app_users WHERE email=?",UUID.class,email);
   assertEquals(4,provider.calls.size());assertEquals(4L,db.queryForObject("SELECT count(*) FROM financial_connection_operations WHERE user_id=?",Long.class,user));
   assertEquals(1L,db.queryForObject("SELECT count(*) FROM financial_connections WHERE user_id=? AND archived_at IS NOT NULL AND state='FAILED' AND cid_ciphertext IS NULL",Long.class,user));
   assertEquals(1L,db.queryForObject("SELECT count(*) FROM financial_connections WHERE user_id=? AND archived_at IS NULL AND state='DISCONNECTED' AND cid_ciphertext IS NULL",Long.class,user));
   report.put("passed",true);report.put("providerCalls",4);report.put("operationsPreserved",4);report.put("failedRowsArchived",1);report.put("externalArchiveCalls",0);
  }finally{
   db.update("DELETE FROM app_users WHERE email=?",email);report.put("testAccountRemoved",db.queryForObject("SELECT count(*) FROM app_users WHERE email=?",Long.class,email)==0);
   command("reverse","tcp:18080","tcp:18080");Files.writeString(Path.of("../.run/failed-connection-ui.json"),new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report));
  }
 }
}
