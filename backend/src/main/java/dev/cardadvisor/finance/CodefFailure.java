package dev.cardadvisor.finance;

/** Fixed messages only: never attach provider bodies, headers, request values or causes. */
public final class CodefFailure extends RuntimeException {
 public enum Kind { DISABLED, CONFIGURATION, AUTHENTICATION, ACCESS_DENIED, RATE_LIMITED,
  AUTH_REQUIRED, REJECTED, INVALID_RESPONSE, TRANSPORT, UNKNOWN_OUTCOME, INTERRUPTED }
 private CodefChallenge challenge;
 public CodefChallenge challenge(){return challenge;}
 CodefFailure withChallenge(CodefChallenge value){challenge=value;return this;}
 public final Kind kind;
 public final Integer httpStatus;
 public final String providerCode;
 public static String safeCode(String value){return value!=null&&value.matches("CF-[0-9]{5}")?value:null;}
 public CodefFailure(Kind kind){this(kind,null);}
 public CodefFailure(Kind kind,Integer status){this(kind,status,null);}
 public CodefFailure(Kind kind,Integer status,String code){super("CODEF_"+kind.name());this.kind=kind;this.httpStatus=status!=null&&status>=100&&status<=599?status:null;this.providerCode=safeCode(code);}
}
