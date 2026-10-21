package dev.cardadvisor.finance;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.cardadvisor.finance.CodefApprovalNormalizerTest.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"codef.config-file=","codef.storage-key-file=","account.mail-config-file="})
@Import(FinancialConnectionsPostgresTest.LocalProviderConfiguration.class)
@EnabledIfEnvironmentVariable(named="RUN_ANDROID_LEDGER_DEVICE_TEST",matches="true")
class AndroidLedgerDevicePostgresTest {
 @LocalServerPort int port;@Autowired JdbcTemplate db;@Autowired FinancialConnectionsPostgresTest.Stub provider;
 final String device=System.getenv().getOrDefault("ANDROID_TEST_DEVICE","R3CX701TE5J");final String adb=System.getenv("LOCALAPPDATA")+"/Android/Sdk/platform-tools/adb.exe";
 String command(String...args)throws Exception{var cmd=new ArrayList<String>(List.of(adb,"-s",device));cmd.addAll(List.of(args));var p=new ProcessBuilder(cmd).redirectErrorStream(true).start();var out=CompletableFuture.supplyAsync(()->{try{return new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);}catch(Exception e){return "";}});if(!p.waitFor(240,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("Device test timed out; output withheld");}assertEquals(0,p.exitValue());return out.get(3,TimeUnit.SECONDS);}
 Map<String,String> personalSnapshot(){var result=new LinkedHashMap<String,String>();for(String table:List.of("app_users","cards","card_month_states","budgets","ledger_entries","cancellations","financial_transactions","financial_card_mappings","financial_performance_reviews","financial_performance_refund_dates","financial_benefit_reviews","financial_benefit_months")){
  String owner=table.equals("app_users")?"t.id":"t.user_id";result.put(table,db.queryForObject("SELECT md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY to_jsonb(t)::text),'')) FROM "+table+" t WHERE NOT EXISTS(SELECT 1 FROM app_users u WHERE u.id="+owner+" AND u.email LIKE 'device-ledger-%@example.test')",String.class));
 }return result;}
 @Test void physicalLedgerManualAndFinancialRefunds()throws Exception{
  String run=UUID.randomUUID().toString(),email="device-ledger-"+run+"@example.test";var report=new LinkedHashMap<String,Object>();report.put("passed",false);report.put("device",device);report.put("syntheticProviderOnly",true);
  command("shell","am","force-stop","dev.cardadvisor.android");var before=personalSnapshot();
  try{
   report.put("model",command("shell","getprop","ro.product.model").trim());report.put("physicalDevice",!command("shell","getprop","ro.kernel.qemu").trim().equals("1"));
   provider.behaviors.add(new FinancialConnectionsPostgresTest.Behavior());
   for(String refund:List.of("0","20000","90000")){var b=new FinancialConnectionsPostgresTest.Behavior();String cancel=refund.equals("0")?"0":refund.equals("90000")?"1":"2";b.approvalData=row("DEVICE1","90000",cancel,refund).put("resCardNo","*************123").put("resMemberStoreName","테스트 온라인스토어").put("resCardName","테스트 조회 카드").put("resUsedDate",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));provider.behaviors.add(b);}
   command("reverse","tcp:18080","tcp:"+port);String out=command("shell","am","instrument","-w","-r","-e","class","dev.cardadvisor.android.LedgerDeviceFlowTest","-e","runId",run,"dev.cardadvisor.android.test/androidx.test.runner.AndroidJUnitRunner");
   report.put("steps",out.lines().filter(x->x.matches("INSTRUMENTATION_STATUS: ledgerStep=[a-z_]+\\s*")).map(x->x.substring(x.indexOf('=')+1).trim()).toList());report.put("failureLocations",out.lines().filter(x->x.matches(".*LedgerDeviceFlowTest\\.kt:[0-9]+.*")).map(x->x.replaceAll(".*LedgerDeviceFlowTest\\.kt:([0-9]+).*","LedgerDeviceFlowTest.kt:$1")).distinct().toList());report.put("failureTypes",java.util.regex.Pattern.compile("(?:java|androidx|android|org\\.junit)\\.[A-Za-z0-9_.$]+(?:Exception|Error)\\b").matcher(out).results().map(java.util.regex.MatchResult::group).distinct().toList());assertTrue(out.contains("OK (1 test)"),"Device ledger flow failed; see sanitized evidence");
   UUID user=db.queryForObject("SELECT id FROM app_users WHERE email=?",UUID.class,email);assertEquals(2L,db.queryForObject("SELECT count(*) FROM ledger_entries WHERE user_id=?",Long.class,user));assertEquals(1L,db.queryForObject("SELECT count(*) FROM financial_transactions WHERE user_id=?",Long.class,user));assertEquals(4L,db.queryForObject("SELECT count(*) FROM cancellations WHERE user_id=?",Long.class,user));assertEquals(140000L,db.queryForObject("SELECT sum(amount_won) FROM cancellations WHERE user_id=?",Long.class,user));assertEquals(0L,db.queryForObject("SELECT sum(e.amount_won-COALESCE((SELECT sum(c.amount_won) FROM cancellations c WHERE c.user_id=e.user_id AND c.transaction_id=e.id),0)) FROM ledger_entries e WHERE e.user_id=?",Long.class,user));assertEquals(1L,db.queryForObject("SELECT count(*) FROM financial_card_mappings WHERE user_id=? AND card_id IS NOT NULL AND version=1",Long.class,user));assertEquals(4,provider.calls.size());assertEquals(1L,db.queryForObject("SELECT count(*) FROM financial_performance_reviews WHERE user_id=? AND decision='INCLUDE'",Long.class,user));assertEquals(2L,db.queryForObject("SELECT count(*) FROM financial_performance_refund_dates WHERE user_id=? AND receipt_date IS NOT NULL",Long.class,user));report.put("performanceDecisionsRefundsAndConflict",true);report.put("mappingSavedAndReopened",true);report.put("ledgerRows",2);report.put("financialRows",1);report.put("cancellations",4);report.put("netSpendingWon",0);report.put("passed",true);
  }finally{
   db.update("DELETE FROM app_users WHERE email=?",email);report.put("syntheticAccountRemoved",db.queryForObject("SELECT count(*) FROM app_users WHERE email=?",Long.class,email)==0);boolean unchanged=before.equals(personalSnapshot());report.put("existingDataUnchanged",unchanged);command("shell","am","force-stop","dev.cardadvisor.android");command("reverse","tcp:18080","tcp:18080");Files.createDirectories(Path.of("../.run"));Files.writeString(Path.of("../.run/ledger-device-"+device+".json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));assertTrue(unchanged,"Existing user data changed; values withheld");
  }
 }
}
