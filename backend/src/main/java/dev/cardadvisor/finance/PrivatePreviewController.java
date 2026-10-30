package dev.cardadvisor.finance;
import dev.cardadvisor.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @Profile("synthetic-preview") @RequestMapping("/v1/demo/finance-preview")
public class PrivatePreviewController {
 private final FinancialConnectionService connections;
 public PrivatePreviewController(FinancialConnectionService connections){this.connections=connections;}
 @PostMapping public Object prepare(HttpServletRequest request){
  UUID user=(UUID)request.getAttribute("userId");
  for(var c:connections.list(user))if("9999".equals(c.get("organization"))){
   if("ACTIVE".equals(c.get("state")))return Map.of("synthetic",true,"connection",c);
   throw new ApiError(409,"PREVIEW_CONNECTION_REQUIRES_REVIEW");
  }
  return connections.create(user,UUID.nameUUIDFromBytes((user+":private-preview-v1").getBytes(java.nio.charset.StandardCharsets.UTF_8)),
   new FinancialConnectionService.ConnectInput("9999","synthetic-preview-user","synthetic-preview-password".toCharArray(),true,FinancialConnectionService.CONSENT_VERSION,null));
 }
}
