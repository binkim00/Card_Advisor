package dev.cardadvisor.finance;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v1/finance/codef")
public class FinanceStatusController {
 private final CodefClient client;private final ConnectedIdCipher cipher;
 public FinanceStatusController(CodefClient client,ConnectedIdCipher cipher){this.client=client;this.cipher=cipher;}
 @PostMapping("/authentication-check") public Map<String,Object> authenticationCheck(){return client.verifyAuthentication();}
 @GetMapping("/status") public Map<String,Object> status(){
  var result=new LinkedHashMap<String,Object>(client.readiness());result.put("connectionApiImplemented",true);
  result.put("storageConfigured",cipher.configured());result.put("userConnectionApiReady",cipher.configured()&&Boolean.TRUE.equals(result.get("enabled")));return result;
 }
}
