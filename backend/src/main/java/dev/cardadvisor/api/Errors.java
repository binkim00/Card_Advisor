package dev.cardadvisor.api;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
@RestControllerAdvice
public class Errors {
 @ExceptionHandler(ApiError.class) ResponseEntity<?> api(ApiError e){return response(e.status,e.getMessage());}
 @ExceptionHandler({IllegalArgumentException.class,ArithmeticException.class,HttpMessageNotReadableException.class})
 ResponseEntity<?> invalid(Exception e){return response(422,e instanceof IllegalArgumentException?e.getMessage():"INVALID_REQUEST");}
 @ExceptionHandler(DataIntegrityViolationException.class)
 ResponseEntity<?> conflict(Exception e){return response(409,"CONFLICT");}
 private ResponseEntity<?> response(int status,String code){return ResponseEntity.status(status).body(Map.of("error",Map.of("code",code==null?"INVALID_REQUEST":code),"requestId",UUID.randomUUID()));}
}
