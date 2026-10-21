package dev.cardadvisor.account;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"codef.config-file=","codef.storage-key-file="})
@EnabledIfEnvironmentVariable(named="RUN_ANDROID_SESSION_TEST",matches="true")
class AndroidSessionPostgresTest {
 @LocalServerPort int port;@Autowired JdbcTemplate db;
 final String adb=System.getenv("LOCALAPPDATA")+"/Android/Sdk/platform-tools/adb.exe",device=System.getenv().getOrDefault("ANDROID_TEST_DEVICE","emulator-5554");
 String command(String...args)throws Exception{var cmd=new ArrayList<String>(List.of(adb,"-s",device));cmd.addAll(List.of(args));var p=new ProcessBuilder(cmd).redirectErrorStream(true).start();var output=CompletableFuture.supplyAsync(()->{try{return new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);}catch(Exception e){return "";}});if(!p.waitFor(60,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("Session phase timeout; raw output withheld");}assertEquals(0,p.exitValue());return output.get(3,TimeUnit.SECONDS);}
 @Test void processRestartRefreshLogoutAndRevocation()throws Exception{
  String run=UUID.randomUUID().toString(),email="persist-"+run+"@example.test";var completed=new ArrayList<Integer>();var report=new LinkedHashMap<String,Object>();report.put("device",device);report.put("passed",false);
  try{command("reverse","tcp:18080","tcp:"+port);
   for(int phase=1;phase<=(device.startsWith("emulator-")?5:4);phase++){
    command("shell","am","force-stop","dev.cardadvisor.android");String out=command("shell","am","instrument","-w","-r","-e","class","dev.cardadvisor.android.SessionPersistenceTest#phase","-e","runId",run,"-e","phase",Integer.toString(phase),"dev.cardadvisor.android.test/androidx.test.runner.AndroidJUnitRunner");
    report.put("steps",out.lines().filter(x->x.matches("INSTRUMENTATION_STATUS: sessionStep=[a-z_]+\\s*")).toList());report.put("failureLocations",out.lines().filter(x->x.matches(".*SessionPersistenceTest\\.kt:[0-9]+.*")).map(x->x.replaceAll(".*SessionPersistenceTest\\.kt:([0-9]+).*","SessionPersistenceTest.kt:$1")).distinct().toList());assertTrue(out.contains("OK (1 test)"),"Session phase failed; raw output withheld");completed.add(phase);
    UUID user=db.queryForObject("SELECT id FROM app_users WHERE email=?",UUID.class,email);
    if(phase==1||phase==3)db.update("UPDATE sessions SET expires_at=now()-interval '1 second' WHERE user_id=?",user);
    if(phase==2)assertEquals(1L,db.queryForObject("SELECT count(*) FROM refresh_sessions WHERE user_id=? AND used_at IS NOT NULL",Long.class,user));
    if(phase==3)db.update("UPDATE refresh_sessions SET revoked_at=now() WHERE user_id=?",user);
   }report.put("passed",true);report.put("singleRefreshForConcurrentRequests",true);
  }finally{db.update("DELETE FROM app_users WHERE email=?",email);report.put("testAccountRemoved",db.queryForObject("SELECT count(*) FROM app_users WHERE email=?",Long.class,email)==0);report.put("phases",completed);command("reverse","tcp:18080","tcp:18080");Files.createDirectories(Path.of("../.run"));Files.writeString(Path.of("../.run/session-"+device+".json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(report));}
 }
}
