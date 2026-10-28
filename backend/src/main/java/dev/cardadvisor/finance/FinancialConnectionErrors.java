package dev.cardadvisor.finance;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;
@RestControllerAdvice(assignableTypes={FinancialConnectionController.class,FinanceStatusController.class,FinancialSyncController.class,HeldCardController.class})
public class FinancialConnectionErrors {
 @ExceptionHandler(CodefFailure.class) public ResponseEntity<?> provider(CodefFailure error){return ResponseEntity.status(503).body(Map.of("error",error.httpStatus==null?Map.of("code",error.getMessage()):Map.of("code",error.getMessage(),"providerHttpStatus",error.httpStatus)));}
}
