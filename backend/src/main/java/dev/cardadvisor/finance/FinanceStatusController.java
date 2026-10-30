package dev.cardadvisor.finance;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/finance/codef")
public class FinanceStatusController {
 private final CodefClient client;private final ConnectedIdCipher cipher;private final org.springframework.core.env.Environment environment;
 public FinanceStatusController(CodefClient client,ConnectedIdCipher cipher,org.springframework.core.env.Environment environment){this.client=client;this.cipher=cipher;this.environment=environment;}
 @PostMapping("/authentication-check") public Map<String,Object> authenticationCheck(){return client.verifyAuthentication();}
 @GetMapping("/status") public Map<String,Object> status(){
  var result=new LinkedHashMap<String,Object>(client.readiness());result.put("connectionApiImplemented",true);
  result.put("privatePreviewEnabled",environment.matchesProfiles("synthetic-preview")&&Boolean.TRUE.equals(result.get("syntheticProvider")));result.put("realInstitutionAccessEnabled",Boolean.TRUE.equals(result.get("enabled"))&&!Boolean.TRUE.equals(result.get("syntheticProvider")));
  result.put("storageConfigured",cipher.configured());result.put("userConnectionApiReady",cipher.configured()&&Boolean.TRUE.equals(result.get("enabled")));return result;
 }
}
