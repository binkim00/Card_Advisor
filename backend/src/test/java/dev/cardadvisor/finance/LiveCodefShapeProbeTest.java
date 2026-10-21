package dev.cardadvisor.finance;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
/** Operator-invoked read-only probe. Prints aggregate structural enums, never values. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named="RUN_LIVE_CODEF_SHAPE_PROBE",matches="true")
class LiveCodefShapeProbeTest {
 @Autowired JdbcTemplate db;@Autowired CodefClient provider;@Autowired ConnectedIdCipher cipher;
 @Test void inspectOnlyShapes(){
  var list=db.queryForList("SELECT c.*,s.from_date,s.to_date FROM financial_connections c JOIN LATERAL (SELECT from_date,to_date FROM financial_sync_runs WHERE user_id=c.user_id AND connection_id=c.id ORDER BY created_at DESC LIMIT 1)s ON true WHERE c.environment='DEMO' AND c.organization='0301' AND c.state='ACTIVE'");
  Assertions.assertEquals(1,list.size());var c=list.get(0);Assertions.assertEquals("DEMO",provider.environment());
  var user=(UUID)c.get("user_id");var id=(UUID)c.get("id");String cid=cipher.decrypt(new ConnectedIdCipher.Sealed((String)c.get("key_id"),(byte[])c.get("cid_nonce"),(byte[])c.get("cid_ciphertext")),user,id,"DEMO",(String)c.get("provider_scope"),"0301");
  var data=provider.approvals(cid,"0301","",((java.sql.Date)c.get("from_date")).toLocalDate(),((java.sql.Date)c.get("to_date")).toLocalDate(),Map.of("inquiryType","1","orderBy","1","memberStoreInfoType","0")).data();
  var shapes=new TreeMap<String,Integer>();
  for(var row:data){var value=row.path("resCardNo");String s=value.asText("");String shape=value.isMissingNode()?"MISSING":!value.isTextual()?"NON_TEXT":s.isBlank()?"BLANK":s.replaceAll("[0-9]","D").replaceAll("[A-Za-z]","L").replaceAll("[^DL* -]","?");if(shape.length()>32)shape="OVERSIZE";shapes.merge(shape,1,Integer::sum);}
  System.out.println("SAFE_CARD_FIELD_SHAPES="+shapes);
 }
}
