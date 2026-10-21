package dev.cardadvisor.finance;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.*;
import java.util.*;

/** CODEF personal approval contract, checked 2026-10-19. No raw payload retention. */
public final class CodefApprovalNormalizer {
 public static final String VERSION="CODEF_KB_HANA_20261020_MASK2";
 public record Approval(String identity,String cardMask,LocalDate date,String time,String kind,
   Long amountWon,Long cancelledWon,String currency,String sourceAmount,String paymentType,
   Integer installments,String issue,String fingerprintMaterial,String merchantName,String cardLabel) {
  @Override public String toString(){return "Approval[redacted]";}
 }
 private static String text(JsonNode n,String key){JsonNode v=n.get(key);return v!=null&&v.isTextual()?v.asText().strip():"";}
 private static String displayLabel(JsonNode n,String key){String s=text(n,key);if(s.isBlank()||s.length()>120||s.chars().anyMatch(Character::isISOControl)||s.matches(".*[0-9]{12,}.*"))return null;return s;}
 private static BigDecimal decimal(String s){try{if(s.length()>64||!s.matches("-?[0-9]+(?:\\.[0-9]+)?"))return null;return new BigDecimal(s);}catch(Exception e){return null;}}
 private static Long won(BigDecimal d){try{return d==null?null:d.longValueExact();}catch(Exception e){return null;}}
 private static String mask(String s){
  s=s.replace("-","").replace(" ","");
  if(!s.matches("[0-9]{4}[0-9*Xx]{4,11}[0-9]{4}"))return null;
  return s.substring(0,4)+"********"+s.substring(s.length()-4);
 }
 public List<Approval> normalize(String organization,JsonNode data,LocalDate from,LocalDate to){return normalize(organization,data,from,to,false);}
 List<Approval> legacyNormalize(String organization,JsonNode data,LocalDate from,LocalDate to){return normalize(organization,data,from,to,true);}
 private List<Approval> normalize(String organization,JsonNode data,LocalDate from,LocalDate to,boolean legacy){
  if(!Set.of("0301","0313").contains(organization))throw new IllegalArgumentException("UNSUPPORTED_APPROVAL_INSTITUTION");
  if(data==null||!(data.isArray()||data.isObject())||data.isObject()&&!data.has("resUsedDate")||data.size()>5000)throw new IllegalArgumentException("INVALID_APPROVAL_RESPONSE");
  List<JsonNode> rows=new ArrayList<>();if(data.isArray())data.forEach(rows::add);else rows.add(data);
  var result=new ArrayList<Approval>();
  for(JsonNode n:rows){
   if(!n.isObject())throw new IllegalArgumentException("INVALID_APPROVAL_RESPONSE");
   String dateText=text(n,"resUsedDate"),time=text(n,"resUsedTime"),approval=text(n,"resApprovalNo"),card=mask(text(n,"resCardNo"));
   // KB actual response can expose only the last three digits. Preserve masking; invent no digits.
   if(!legacy&&organization.equals("0301")&&text(n,"resCardNo").matches("[*]{13}[0-9]{3}"))card=text(n,"resCardNo");
   LocalDate date=null;try{if(dateText.matches("[0-9]{8}"))date=LocalDate.parse(dateText,DateTimeFormatter.BASIC_ISO_DATE);}catch(Exception ignored){}
   boolean timeValid=false;try{LocalTime.parse(time,DateTimeFormatter.ofPattern("HHmmss").withResolverStyle(ResolverStyle.STRICT));timeValid=true;}catch(Exception ignored){}
   String issue=null;
   if(date==null||!timeValid||card==null||!approval.matches("[A-Za-z0-9]{1,40}"))issue="MISSING_IDENTITY";
   else if(date.isBefore(from)||date.isAfter(to))issue="OUTSIDE_REQUEST_WINDOW";
   String currency=text(n,"resAccountCurrency"),payment=text(n,"resPaymentType"),cancel=text(n,"resCancelYN");
   BigDecimal raw=decimal(text(n,"resUsedAmount"));Long amount="KRW".equals(currency)?won(raw):null;
   if(!"KRW".equals(currency))issue=first(issue,"CURRENCY_REQUIRES_REVIEW");
   if(amount==null||amount==0||amount==Long.MIN_VALUE||amount!=null&&Math.abs(amount)>100_000_000L)issue=first(issue,"UNKNOWN_OR_INVALID_AMOUNT");
   Integer months=null;
   if(payment.equals("1"))months=1;
   else if(payment.equals("2")){try{String s=text(n,"resInstallmentMonth");if(!s.matches("[0-9]{1,3}"))throw new IllegalArgumentException();months=Integer.valueOf(s);if(months<2||months>120)months=null;}catch(Exception ignored){}if(months==null)issue=first(issue,"UNKNOWN_INSTALLMENTS");}
   else issue=first(issue,"UNKNOWN_PAYMENT_TYPE"); // KB type 3 may be advance-discount installments.
   String kind="APPROVAL";Long refund=null;
   if(cancel.equals("3")){kind="REJECTED";issue="REJECTED_APPROVAL";}
   else if(cancel.equals("0")){
    String r=text(n,"resCancelAmount");if(!r.isEmpty()&&!r.equals("0"))issue=first(issue,"CONTRADICTORY_CANCELLATION");
    if(amount!=null&&amount>0)refund=0L;else issue=first(issue,"CONTRADICTORY_CANCELLATION");
   }else if(cancel.equals("1")||cancel.equals("2")){
    if(organization.equals("0301")&&amount!=null&&amount<0&&text(n,"resCancelAmount").isEmpty()){
     kind="CANCEL_EVENT";refund=-amount;amount=null; // KB acquired cancellation slip, official Type 2.
    }else{
     refund="KRW".equals(currency)?won(decimal(text(n,"resCancelAmount"))):null;
     if(amount==null||amount<=0||refund==null||refund<=0||refund>amount)issue=first(issue,"UNKNOWN_OR_INVALID_REFUND");
     // The amount is authoritative when available; KB/Hana do not promise partial flags.
    }
   }else issue=first(issue,"UNKNOWN_CANCEL_STATUS");
   String identity=card==null||date==null||!timeValid||!approval.matches("[A-Za-z0-9]{1,40}")?null:String.join("|",card,dateText,time,approval,kind);
   // Only allowlisted normalized values, never merchant contact, PAN, session or credentials.
   String material=String.join("|",Objects.toString(identity,"missing"),dateText.matches("[0-9]{8}")?dateText:"",time.matches("[0-9]{6}")?time:"",Objects.toString(amount,"?"),Objects.toString(refund,"?"),raw==null||raw.precision()>20?"?":raw.toPlainString(),currency.matches("[A-Z]{3}")?currency:"",payment.matches("[123]")?payment:"",Objects.toString(months,"?"),cancel.matches("[0-3]")?cancel:"",Objects.toString(issue,""));
   result.add(new Approval(identity,card,date,timeValid?time:null,kind,amount,refund,currency.matches("[A-Z]{3}")?currency:null,raw==null||raw.precision()>20?null:raw.toPlainString(),payment.matches("[123]")?payment:null,months,issue,material,displayLabel(n,"resMemberStoreName"),displayLabel(n,"resCardName")));
  }
  return result;
 }
 private static String first(String existing,String next){return existing==null?next:existing;}
}
