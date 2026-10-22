package dev.cardadvisor.finance;
import java.util.Arrays;
/** Optional KB possession check, documented in CODEF account register/update. Never persisted. */
public record CardOwnershipInput(char[] cardNo,char[] cardPassword,Boolean confirmed) implements AutoCloseable {
 public boolean validFor(String organization){return "0301".equals(organization)&&Boolean.TRUE.equals(confirmed)&&digits(cardNo,13,19)&&digits(cardPassword,2,2);}
 private static boolean digits(char[] value,int min,int max){if(value==null||value.length<min||value.length>max)return false;for(char c:value)if(c<'0'||c>'9')return false;return true;}
 public void close(){if(cardNo!=null)Arrays.fill(cardNo,'\0');if(cardPassword!=null)Arrays.fill(cardPassword,'\0');}
 @Override public String toString(){return "CardOwnershipInput[redacted]";}
}
