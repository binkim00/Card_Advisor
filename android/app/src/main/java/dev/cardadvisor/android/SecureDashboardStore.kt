package dev.cardadvisor.android
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only the last complete dashboard, encrypted, excluded from Android backup. Never auth or recommendations. */
class SecureDashboardStore(context:Context,slot:String="dashboard-v1"){
 private val file=AtomicFile(File(context.noBackupFilesDir,"$slot.enc"));private val alias="dev.cardadvisor.$slot"
 init{require(slot.matches(Regex("[a-z0-9-]{1,80}")))}
 companion object{private val lock=Any();private const val MAX=4*1024*1024;private const val AGE=7*24*60*60*1000L}
 private fun key():SecretKey{val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};return (ks.getKey(alias,null) as? SecretKey)?:KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())}.generateKey()}
 suspend fun save(snapshot:JSONObject)=withContext(Dispatchers.IO){synchronized(lock){val plain=snapshot.toString().toByteArray(Charsets.UTF_8);var output:java.io.FileOutputStream?=null
  try{require(plain.size<MAX);val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(alias.toByteArray());output=file.startWrite();output!!.write(byteArrayOf(1)+cipher.iv+cipher.doFinal(plain));file.finishWrite(output)}catch(e:Exception){output?.let{file.failWrite(it)};file.delete()}finally{plain.fill(0)}
 }}
 suspend fun load(owner:String,base:String,month:String,now:Long=System.currentTimeMillis()):JSONObject?=withContext(Dispatchers.IO){synchronized(lock){var plain:ByteArray?=null
  try{if(!file.baseFile.exists())return@synchronized null;if(file.baseFile.length() !in 30..MAX.toLong())throw IllegalStateException();val bytes=file.readFully();require(bytes[0]==1.toByte());val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(1,13)));c.updateAAD(alias.toByteArray());plain=c.doFinal(bytes.copyOfRange(13,bytes.size));val value=JSONObject(String(plain!!,Charsets.UTF_8));val age=now-value.getLong("fetchedAt");if(age !in 0..AGE){file.delete();return@synchronized null};if(value.getString("owner")!=owner||value.getString("base")!=base||value.getString("month")!=month)return@synchronized null;value.getJSONArray("ledger");value.getJSONArray("performance");value}
  catch(e:Exception){file.delete();null}finally{plain?.fill(0)}
 }}
 suspend fun clear()=withContext(Dispatchers.IO){synchronized(lock){file.delete()}}
}
