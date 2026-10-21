package dev.cardadvisor.android
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SavedSession(val access:String,val refresh:String){override fun toString()="SavedSession[redacted]"}
/** Tokens only, excluded from backup; all key/file work runs off the UI thread. */
class SecureSessionStore(context:Context,private val slot:String="session-v1"){
 private val file=AtomicFile(File(context.noBackupFilesDir,"$slot.enc"))
 private val alias="dev.cardadvisor.$slot"
 init{require(slot.matches(Regex("[a-z0-9-]{1,80}")))}
 companion object{private val lock=Any()}
 private fun key():SecretKey{val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};return (ks.getKey(alias,null) as? SecretKey)?:KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())}.generateKey()}
 suspend fun load():SavedSession?=withContext(Dispatchers.IO){synchronized(lock){
  if(!file.baseFile.exists())return@synchronized null
  var plain:ByteArray?=null
  try{val bytes=file.readFully();require(bytes.size in 30..4096&&bytes[0]==1.toByte());val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(1,13)));c.updateAAD(alias.toByteArray());plain=c.doFinal(bytes.copyOfRange(13,bytes.size));val j=JSONObject(String(plain!!,Charsets.UTF_8));val a=j.getString("accessToken");val r=j.getString("refreshToken");require(a.length in 16..256&&r.length in 16..256);SavedSession(a,r)}catch(e:Exception){file.delete();if(e is android.security.keystore.KeyPermanentlyInvalidatedException)KeyStore.getInstance("AndroidKeyStore").apply{load(null);deleteEntry(alias)};null}finally{plain?.fill(0)}
 }}
 suspend fun save(session:SavedSession)=withContext(Dispatchers.IO){synchronized(lock){
  val plain=JSONObject().put("accessToken",session.access).put("refreshToken",session.refresh).toString().toByteArray();var stream:java.io.FileOutputStream?=null
  try{val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());c.updateAAD(alias.toByteArray());val data=byteArrayOf(1)+c.iv+c.doFinal(plain);stream=file.startWrite();stream!!.write(data);file.finishWrite(stream)}catch(e:Exception){stream?.let{file.failWrite(it)};throw IllegalStateException("로그인 정보를 안전하게 저장하지 못했습니다.")}finally{plain.fill(0)}
 }}
 suspend fun clear()=withContext(Dispatchers.IO){synchronized(lock){file.delete()}}
}
