package dev.cardadvisor.finance;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="RUN_FINANCE_PG_TESTS",matches="true")
class FinancialReconciliationPostgresTest extends FinancialConnectionsPostgresTest{
 @Test void unknownRegistrationOnlyRestoresReauthenticationAndReplays()throws Exception{
  var partial=new Behavior();partial.partial=true;provider.behaviors.add(partial);var response=expect(202,request(a,"POST","/finance/connections",connect("0301"),UUID.randomUUID()));UUID id=id(response);long version=response.at("/connection/version").asLong();UUID key=UUID.randomUUID();
  assertEquals(404,request(b,"POST","/finance/connections/"+id+"/reconcile",Map.of("expectedVersion",version),key).status());
  var out=expect(200,request(a,"POST","/finance/connections/"+id+"/reconcile",Map.of("expectedVersion",version),key));assertEquals("AUTH_REQUIRED",out.get("state").asText());assertFalse(out.get("credentialsVerified").asBoolean());int calls=provider.calls.size();
  expect(200,request(a,"POST","/finance/connections/"+id+"/reconcile",Map.of("expectedVersion",version),key));assertEquals(calls,provider.calls.size());assertEquals(409,request(a,"POST","/finance/connections/"+id+"/reconcile",Map.of("expectedVersion",version+1),key).status());
 }
 @Test void confirmedAbsenceSettlesUncertainDisconnect()throws Exception{
  var response=create(a,"0301");UUID id=id(response);var timeout=new Behavior();timeout.status=500;provider.behaviors.add(timeout);
  var unknown=expect(202,request(a,"POST","/finance/connections/"+id+"/disconnect",Map.of("expectedVersion",response.at("/connection/version").asLong()),UUID.randomUUID()));var absent=new Behavior();absent.failed=true;provider.behaviors.add(absent);
  var out=expect(200,request(a,"POST","/finance/connections/"+id+"/reconcile",Map.of("expectedVersion",unknown.at("/connection/version").asLong()),UUID.randomUUID()));assertEquals("DISCONNECTED",out.get("state").asText());assertNull(db.queryForObject("SELECT cid_ciphertext FROM financial_connections WHERE id=?",byte[].class,id));
 }
 @Test void missingConnectedIdNeverSearchesOtherUsersIdentifiers()throws Exception{
  var timeout=new Behavior();timeout.status=500;provider.behaviors.add(timeout);var response=expect(202,request(a,"POST","/finance/connections",connect("0301"),UUID.randomUUID()));int calls=provider.calls.size();var out=expect(200,request(a,"POST","/finance/connections/"+id(response)+"/reconcile",Map.of("expectedVersion",response.at("/connection/version").asLong()),UUID.randomUUID()));assertEquals("PROVIDER_SUPPORT_REQUIRED",out.get("decision").asText());assertEquals(calls,provider.calls.size());
 }
}
