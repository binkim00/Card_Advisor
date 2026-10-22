package dev.cardadvisor.account;
import com.fasterxml.jackson.databind.*;import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;import org.springframework.boot.web.servlet.FilterRegistrationBean;import org.springframework.boot.test.web.server.LocalServerPort;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.concurrent.*;import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"codef.config-file=","codef.storage-key-file=","openai.config-file=","account.mail-config-file="})
@EnabledIfEnvironmentVariable(named="RUN_DEVICE_FREE_ANDROID_TEST",matches="true")
class AndroidDeviceFreePostgresTest{
 @org.springframework.boot.test.context.TestConfiguration static class FaultSwitch{
  @org.springframework.context.annotation.Bean FilterRegistrationBean<jakarta.servlet.Filter> switchFilter(){
   var offline=new java.util.concurrent.atomic.AtomicBoolean();var registration=new FilterRegistrationBean<jakarta.servlet.Filter>();registration.setOrder(Integer.MIN_VALUE);
   registration.setFilter((request,response,chain)->{var req=(jakarta.servlet.http.HttpServletRequest)request;var res=(jakarta.servlet.http.HttpServletResponse)response;String p=req.getRequestURI();
    if(p.equals("/v1/test/network")){offline.set(new ObjectMapper().readTree(req.getInputStream()).path("offline").asBoolean());res.setContentType("application/json");res.getWriter().write("{}");return;}
    if(offline.get()&&p.equals("/v1/dashboard")){res.setStatus(503);res.setContentType("application/json");res.getWriter().write("{\"error\":{\"code\":\"SYNTHETIC_OFFLINE\"}}");return;}chain.doFilter(request,response);
   });return registration;
  }
 }
 @LocalServerPort int port;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;
 String adb(String...args)throws Exception{var cmd=new ArrayList<String>(List.of(System.getenv("LOCALAPPDATA")+"/Android/Sdk/platform-tools/adb.exe","-s","emulator-5554"));cmd.addAll(List.of(args));var p=new ProcessBuilder(cmd).redirectErrorStream(true).start();var output=CompletableFuture.supplyAsync(()->{try{return new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);}catch(Exception e){return "";}});if(!p.waitFor(180,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("Synthetic Android test timeout");}assertEquals(0,p.exitValue());return output.get(3,TimeUnit.SECONDS);}
 @Test void offlineAndBudgetUserFlows()throws Exception{
  String run=UUID.randomUUID().toString();var report=new LinkedHashMap<String,Object>();report.put("passed",false);report.put("physicalDevice",false);report.put("externalProviderCalls",0);
  try{adb("reverse","tcp:18080","tcp:"+port);String out=adb("shell","am","instrument","-w","-r","-e","class","dev.cardadvisor.android.DeviceFreeBudgetFlowTest,dev.cardadvisor.android.OfflineDashboardTest","-e","runId",run,"dev.cardadvisor.android.test/androidx.test.runner.AndroidJUnitRunner");
   report.put("failureLocations",out.lines().filter(x->x.matches(".*(DeviceFreeBudgetFlowTest|OfflineDashboardTest)\\.kt:[0-9]+.*")).map(x->x.replaceAll(".*((?:DeviceFreeBudgetFlowTest|OfflineDashboardTest)\\.kt:[0-9]+).*","$1")).distinct().toList());
   // Error headers contain no response bodies or screen hierarchy; include only known exception names.
   report.put("failureHints",List.of("Failed to perform scroll", "Expected exactly", "No node found", "Condition still not satisfied").stream().filter(out::contains).toList());
   report.put("failureTypes",List.of("AssertionError","ComposeTimeoutException","ApiException","JSONException").stream().filter(out::contains).toList());
   assertTrue(out.contains("OK (2 tests)"),"Device-free Android tests failed; raw output withheld");
   UUID user=db.queryForObject("SELECT id FROM app_users WHERE email=?",UUID.class,"budget-flow-"+run+"@example.test");assertEquals(1,db.queryForObject("SELECT count(*) FROM fixed_cost_links WHERE user_id=? AND allocated_won=500",Integer.class,user));assertEquals(2,db.queryForObject("SELECT count(*) FROM ledger_classification_history WHERE user_id=?",Integer.class,user));assertEquals(250L,db.queryForObject("SELECT sum(amount_won) FROM cancellations WHERE user_id=?",Long.class,user));report.put("passed",true);
  }finally{for(String prefix:List.of("budget-flow-","offline-"))db.update("DELETE FROM app_users WHERE email=?",prefix+run+"@example.test");adb("reverse","--remove","tcp:18080");report.put("syntheticAccountsRemoved",true);Files.writeString(Path.of("../.run/device-free-android.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(report));}
 }
}
