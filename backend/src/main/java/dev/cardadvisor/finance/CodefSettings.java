package dev.cardadvisor.finance;

import java.net.URI;
import java.util.Properties;
import static dev.cardadvisor.finance.CodefFailure.Kind.*;

/** Never use a record/generated toString for secrets. No credentials in Android. */
public final class CodefSettings {
 public enum Environment {
  SANDBOX("https://sandbox.codef.io"), DEMO("https://development.codef.io"), PRODUCTION("https://api.codef.io");
  final URI uri; Environment(String uri){this.uri=URI.create(uri);}
 }
 final boolean enabled;
 final Environment environment;
 final String clientId,clientSecret,publicKey;
 public CodefSettings(Properties p){
  enabled="true".equals(p.getProperty("enabled","false"));
  try{environment=Environment.valueOf(p.getProperty("environment","SANDBOX"));}
  catch(Exception e){throw new CodefFailure(CONFIGURATION);}
  clientId=p.getProperty("clientId","");clientSecret=p.getProperty("clientSecret","");publicKey=p.getProperty("publicKey","");
  if(enabled&&(!"true".equals(p.getProperty("usageApproved","false"))||clientId.isBlank()||clientSecret.isBlank()||publicKey.isBlank()||clientId.contains(":")))throw new CodefFailure(CONFIGURATION);
 }
 @Override public String toString(){return "CodefSettings[redacted]";}
}
