package dev.cardadvisor.finance;
import dev.cardadvisor.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static dev.cardadvisor.finance.FinancialConnectionService.*;

@RestController
@RequestMapping("/v1/finance")
public class FinancialConnectionController {
 private final FinancialConnectionService service;private final FinancialReconciliationService checks;
 public FinancialConnectionController(FinancialConnectionService service,FinancialReconciliationService checks){this.service=service;this.checks=checks;}
 private UUID user(HttpServletRequest request){return (UUID)request.getAttribute("userId");}
 private UUID key(String key){try{return UUID.fromString(key);}catch(Exception e){throw new ApiError(422,"IDEMPOTENCY_KEY_REQUIRED");}}
 private ResponseEntity<?> response(Map<String,Object> body){String outcome=(String)((Map<?,?>)body.get("operation")).get("status");return ResponseEntity.status(Set.of("PENDING","UNKNOWN","AUTH_REQUIRED").contains(outcome)?202:200).body(body);}
 @PostMapping("/connections/{id}/reconcile") public Object reconcile(HttpServletRequest r,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) String k,@RequestBody FinancialReconciliationService.Input input){return checks.check(user(r),id,key(k),input);}
 @GetMapping("/connections") public Object list(HttpServletRequest r){return service.list(user(r));}
 @GetMapping("/connections/{id}") public Object get(HttpServletRequest r,@PathVariable UUID id){return service.get(user(r),id);}
 @GetMapping("/operations/{id}") public Object operation(HttpServletRequest r,@PathVariable UUID id){return service.getOperation(user(r),id);}
 @PostMapping("/connections") public Object create(HttpServletRequest r,@RequestHeader(value="Idempotency-Key",required=false) String k,@RequestBody ConnectInput input){try{return response(service.create(user(r),key(k),input));}finally{if(input.password()!=null)Arrays.fill(input.password(),'\0');if(input.cardOwnership()!=null)input.cardOwnership().close();}}
 @PostMapping("/connections/{id}/reauthorize") public Object reauthorize(HttpServletRequest r,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) String k,@RequestBody ReauthorizeInput input){try{return response(service.reauthorize(user(r),id,key(k),input));}finally{if(input.password()!=null)Arrays.fill(input.password(),'\0');if(input.cardOwnership()!=null)input.cardOwnership().close();}}
 @PostMapping("/connections/{id}/retry") public Object retry(HttpServletRequest r,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) String k,@RequestBody ReauthorizeInput input){try{return response(service.retry(user(r),id,key(k),input));}finally{if(input.password()!=null)Arrays.fill(input.password(),'\0');if(input.cardOwnership()!=null)input.cardOwnership().close();}}
 @PostMapping("/connections/{id}/archive") public Object archive(HttpServletRequest r,@PathVariable UUID id,@RequestBody DisconnectInput input){return service.archiveFailed(user(r),id,input);}
 @PostMapping("/connections/{id}/disconnect") public Object disconnect(HttpServletRequest r,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) String k,@RequestBody DisconnectInput input){return response(service.disconnect(user(r),id,key(k),input));}
}
