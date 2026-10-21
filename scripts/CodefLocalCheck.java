import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.security.*;
import java.security.spec.*;

/** Reads secrets in this process only; outputs whitelisted readiness booleans, never values or exceptions. */
class CodefLocalCheck {
 public static void main(String[] args){
  boolean readable=false,id=false,secret=false,key=false,demo=false,enabled=false,approved=false,duplicate=false;
  try{
   Path path=Path.of(args[0]);var p=new Properties();try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){p.load(reader);}readable=true;
   var seen=new HashSet<String>();for(String line:Files.readAllLines(path,StandardCharsets.UTF_8)){String s=line.strip();if(s.isEmpty()||s.startsWith("#")||s.startsWith("!"))continue;int at=s.indexOf('=');if(at>0&&!seen.add(s.substring(0,at).strip()))duplicate=true;}
   id=!p.getProperty("clientId","").isBlank()&&!p.getProperty("clientId","").contains(":");
   secret=!p.getProperty("clientSecret","").isBlank();
   try{KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(p.getProperty("publicKey",""))));key=true;}catch(Exception ignored){}
   demo="DEMO".equals(p.getProperty("environment"));enabled="true".equals(p.getProperty("enabled"));approved="true".equals(p.getProperty("usageApproved"));
  }catch(Exception ignored){}
  System.out.println("{\"readable\":"+readable+",\"clientIdPresent\":"+id+",\"clientSecretPresent\":"+secret+",\"rsaPublicKeyValid\":"+key+",\"demoEnvironment\":"+demo+",\"enabled\":"+enabled+",\"usageApproved\":"+approved+",\"duplicateFields\":"+duplicate+",\"readyForDemo\":"+(readable&&id&&secret&&key&&demo&&enabled&&approved&&!duplicate)+"}");
 }
}
