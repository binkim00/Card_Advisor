package dev.cardadvisor.finance;

import dev.cardadvisor.api.ApiError;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Key ring comes from a server-local file, never from a request or the database. */
public final class ConnectedIdCipher {
 private final Map<String,byte[]> keys;
 private final String active;
 private final SecureRandom random=new SecureRandom();
 public ConnectedIdCipher(Properties properties){
  active=properties.getProperty("activeKeyId","");var loaded=new HashMap<String,byte[]>();
  try{
   for(String name:properties.stringPropertyNames())if(name.startsWith("keys.")){
    String id=name.substring(5);byte[] key=Base64.getDecoder().decode(properties.getProperty(name));
    if(!id.matches("[A-Za-z0-9_-]{1,32}")||key.length!=32)throw new IllegalArgumentException();loaded.put(id,key);
   }
   if(!active.isEmpty()&&!loaded.containsKey(active))throw new IllegalArgumentException();
  }catch(Exception e){throw new ApiError(503,"FINANCE_KEY_CONFIGURATION_INVALID");}
  keys=Map.copyOf(loaded);
 }
 public static ConnectedIdCipher fromFile(String path){
  var properties=new Properties();
  if(!path.isBlank())try(var reader=Files.newBufferedReader(Path.of(path))){properties.load(reader);}
  catch(Exception e){throw new ApiError(503,"FINANCE_KEY_CONFIGURATION_INVALID");}
  return new ConnectedIdCipher(properties);
 }
 public boolean configured(){return !active.isEmpty();}
 public void requireConfigured(){if(!configured())throw new ApiError(503,"FINANCE_KEY_NOT_CONFIGURED");}
 public String activeKeyId(){requireConfigured();return active;}
 private byte[] key(String id){byte[] value=keys.get(id);if(value==null)throw new ApiError(503,"FINANCE_KEY_UNAVAILABLE");return value;}
 public record Sealed(String keyId,byte[] nonce,byte[] ciphertext){@Override public String toString(){return "Sealed[redacted]";}}
 private byte[] aad(UUID user,UUID connection,String environment,String scope,String organization,String keyId){
  return ("card-advisor:cid:v1|"+user+"|"+connection+"|"+environment+"|"+scope+"|"+organization+"|"+keyId).getBytes(StandardCharsets.UTF_8);
 }
 public Sealed encrypt(String value,UUID user,UUID connection,String environment,String scope,String organization){
  requireConfigured();byte[] plain=value.getBytes(StandardCharsets.UTF_8);byte[] nonce=new byte[12];random.nextBytes(nonce);
  try{var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key(active),"AES"),new GCMParameterSpec(128,nonce));
   cipher.updateAAD(aad(user,connection,environment,scope,organization,active));return new Sealed(active,nonce,cipher.doFinal(plain));
  }catch(Exception e){throw new ApiError(503,"FINANCE_ENCRYPTION_FAILED");}finally{Arrays.fill(plain,(byte)0);}
 }
 public String decrypt(Sealed sealed,UUID user,UUID connection,String environment,String scope,String organization){
  byte[] plain=null;
  try{var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key(sealed.keyId()),"AES"),new GCMParameterSpec(128,sealed.nonce()));
   cipher.updateAAD(aad(user,connection,environment,scope,organization,sealed.keyId()));plain=cipher.doFinal(sealed.ciphertext());return new String(plain,StandardCharsets.UTF_8);
  }catch(ApiError e){throw e;}catch(Exception e){throw new ApiError(503,"FINANCE_CIPHERTEXT_INVALID");}finally{if(plain!=null)Arrays.fill(plain,(byte)0);}
 }
 public byte[] fingerprint(String keyId,byte[] payload){
  try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key(keyId),"HmacSHA256"));mac.update("card-advisor:request:v1\0".getBytes(StandardCharsets.UTF_8));return mac.doFinal(payload);}
  catch(ApiError e){throw e;}catch(Exception e){throw new ApiError(503,"FINANCE_FINGERPRINT_FAILED");}
 }
 public static byte[] ownershipHash(String environment,String scope,String cid){
  try{return MessageDigest.getInstance("SHA-256").digest((environment+"|"+scope+"|"+cid).getBytes(StandardCharsets.UTF_8));}
  catch(Exception e){throw new ApiError(503,"FINANCE_ENCRYPTION_FAILED");}
 }
 @Override public String toString(){return "ConnectedIdCipher[redacted]";}
}
