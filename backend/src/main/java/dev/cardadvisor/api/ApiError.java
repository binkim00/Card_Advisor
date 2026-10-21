package dev.cardadvisor.api;
public class ApiError extends RuntimeException {
 public final int status;
 public ApiError(int status,String code){super(code);this.status=status;}
}
