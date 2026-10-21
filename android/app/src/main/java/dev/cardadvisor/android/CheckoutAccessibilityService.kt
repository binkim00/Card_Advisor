package dev.cardadvisor.android

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.*
import android.app.*
import android.content.*
import android.os.*
import android.Manifest
import android.content.pm.PackageManager

/** Never performs clicks, gestures or payment. Reads only whitelisted labels and adjacent amounts. */
class CheckoutAccessibilityService:AccessibilityService(){
 private val handler=Handler(Looper.getMainLooper());private var scheduled:Runnable?=null
 // Some WebView routes do not deliver content events after an APK/service reconnect.
 // Sample only the currently active allowlisted window while consent/session/unlock permit it.
 private val refreshHandler=Handler(Looper.getMainLooper())
 private var previousSample:CheckoutObservation?=null
 private val handoff=CheckoutHandoffPolicy("dev.cardadvisor.android",CheckoutDetection.packages.keys)
 private fun keepDuringNavigation(pkg:String?):Boolean{
  val trusted=pkg in setOf("com.android.systemui","com.sec.android.app.launcher")&&runCatching{(packageManager.getApplicationInfo(pkg!!,0).flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM)!=0}.getOrDefault(false)
  return handoff.keep(pkg,CheckoutDetection.current()!=null,trusted,SystemClock.elapsedRealtime())
 }
 private val liveRefresh=object:Runnable{override fun run(){
  try{
   if(!allowed()){previousSample=null;clear();CheckoutAudit.record(this@CheckoutAccessibilityService,packageName,"internal",if(!CheckoutDetection.sessionActive)"SESSION_INACTIVE" else "CONSENT_SWITCH_OR_LOCK_BLOCKED",false,false,emptyMap(),emptySet(),0)}
   else{
    val pkg=rootInActiveWindow?.packageName?.toString()
    if(pkg in CheckoutDetection.packages){
     val observation=observe(pkg!!)
     if(observation!=null&&observation==previousSample){CheckoutDetection.accept(observation);notifyCandidate()}
     else{CheckoutDetection.clear();getSystemService(NotificationManager::class.java).cancel(401)}
     previousSample=observation
    }else{
     val kept=keepDuringNavigation(pkg)
     if(!kept){clear()}
     CheckoutAudit.record(this@CheckoutAccessibilityService,packageName,"internal",if(kept&&pkg==packageName)"CANDIDATE_HANDOFF_RETAINED" else if(kept)"NAVIGATION_GRACE" else if(pkg==null)"ACTIVE_WINDOW_UNAVAILABLE" else "UNSUPPORTED_ACTIVE_WINDOW",kept,false,emptyMap(),emptySet(),0)
    }
   }
  }finally{refreshHandler.postDelayed(this,2000)}
 }}
 private val preferences by lazy{getSharedPreferences("advisor_detection",0)}
 private val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->if(!allowed())clear()}
 private val receiver=object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){clear()}}

 private fun allowed()=CheckoutDetection.sessionActive&&preferences.getBoolean("consent",false)&&preferences.getBoolean("enabled",false)&&!(getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
 override fun onServiceConnected(){
  CheckoutAudit.record(this,packageName,"internal","SERVICE_CONNECTED",false,false,emptyMap(),emptySet(),0)
  preferences.registerOnSharedPreferenceChangeListener(listener)
  refreshHandler.removeCallbacks(liveRefresh);refreshHandler.postDelayed(liveRefresh,2000)
  val filter=IntentFilter(Intent.ACTION_SCREEN_OFF)
  if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED) else registerReceiver(receiver,filter)
  val notifications=getSystemService(NotificationManager::class.java);notifications.createNotificationChannel(NotificationChannel("checkout_candidates","주문서 확인",NotificationManager.IMPORTANCE_LOW).apply{lockscreenVisibility=Notification.VISIBILITY_SECRET})
 }
 override fun onAccessibilityEvent(event:AccessibilityEvent?){
  if(event==null)return
  val pkg=event.packageName?.toString()?:return
  if(!allowed()){if(pkg in CheckoutDetection.packages)CheckoutAudit.record(this,pkg,"unknown","CONSENT_SESSION_OR_LOCK_BLOCKED",false,false,emptyMap(),emptySet(),0);clear();return}
  if(pkg==packageName){if(!keepDuringNavigation(pkg))clear();return}
  if(pkg !in CheckoutDetection.packages){if(!keepDuringNavigation(pkg))clear();return}
  handoff.reset()
  scheduled?.let(handler::removeCallbacks);handler.removeCallbacksAndMessages(null);CheckoutDetection.clear()
  scheduled=Runnable{
   if(!allowed())return@Runnable
   val first=observe(pkg)?:run{CheckoutAudit.record(this,pkg,"unknown","WINDOW_UNAVAILABLE",false,false,emptyMap(),emptySet(),0);clear();return@Runnable}
   handler.postDelayed({
    if(!allowed())return@postDelayed
    val second=observe(pkg)
    if(first==second){CheckoutDetection.accept(first);CheckoutAudit.record(this,pkg,first.appVersion,"STABLE_"+(CheckoutDetection.current()?.state?:"NO_CHECKOUT"),first.checkoutTitle,first.authentication,first.amounts.mapValues{it.value.size},first.amounts.keys,0);notifyCandidate()}else clear()
   },400)
  }.also{handler.postDelayed(it,400)}
 }
 private fun observe(pkg:String):CheckoutObservation?{
  val root=rootInActiveWindow?:return null
  if(root.packageName?.toString()!=pkg)return null
  val amounts=mutableMapOf<DetectionAmount,MutableSet<Long>>();var checkout=false;var authentication=false;var visited=0;val recognized=mutableSetOf<DetectionAmount>()
  fun amount(node:AccessibilityNodeInfo?):Long?{if(node==null||node.isPassword||node.isEditable)return null;return listOfNotNull(node.text,node.contentDescription).firstNotNullOfOrNull{CheckoutTextParser.money(it.toString())}}
  fun adjacent(node:AccessibilityNodeInfo,depth:Int=0):Set<Long>{if(depth>3||node.isPassword||node.isEditable)return emptySet();amount(node)?.let{return setOf(it)};val out=mutableSetOf<Long>();for(i in 0 until minOf(node.childCount,20)){node.getChild(i)?.let{out.addAll(adjacent(it,depth+1))}};return out}
  fun visit(node:AccessibilityNodeInfo){
   if(visited>=2000)return;visited++
   if(node.isPassword){authentication=true;return};if(node.isEditable)return
   // These strings are transient comparisons only. They are never collected or logged.
   val text=node.text?.toString()?.trim();val description=node.contentDescription?.toString()?.trim()
   for(value in listOfNotNull(text,description)){
    if(CheckoutTextParser.title(value))checkout=true
    CheckoutTextParser.paymentButton(pkg,value,node.isClickable)?.let{number->recognized.add(DetectionAmount.FINAL_TOTAL);amounts.getOrPut(DetectionAmount.FINAL_TOTAL){mutableSetOf()}.add(number)}
    CheckoutTextParser.combined(value)?.let{(kind,number)->recognized.add(kind);amounts.getOrPut(kind){mutableSetOf()}.add(number)}
   }
   if(text in setOf("로그인","본인 인증","인증번호 입력","비밀번호 입력")){authentication=true;return}
   if(text in setOf("주문/결제","주문서","주문서 작성","주문 · 결제","주문결제","주문 결제"))checkout=true
   (text?.let(CheckoutTextParser::label)?:description?.let(CheckoutTextParser::label))?.let{label->
    recognized.add(label)
    val parent=node.parent
    if(parent!=null){
     val index=(0 until parent.childCount).firstOrNull{parent.getChild(it)==node}
     if(index!=null&&index+1<parent.childCount){val next=parent.getChild(index+1);if(next!=null)amounts.getOrPut(label){mutableSetOf()}.addAll(adjacent(next))
     }
    }
   }
   for(i in 0 until node.childCount){val child=node.getChild(i)?:continue;visit(child)}
  }
  visit(root)
  val version=runCatching{packageManager.getPackageInfo(pkg,0).versionName?:"unknown"}.getOrDefault("unknown")
  CheckoutAudit.record(this,pkg,version,if(authentication)"AUTHENTICATION_BLOCKED" else if(!checkout)"CHECKOUT_TITLE_NOT_RECOGNIZED" else if(amounts[DetectionAmount.FINAL_TOTAL].orEmpty().size!=1)"TOTAL_MISSING_OR_AMBIGUOUS" else "CANDIDATE_REQUIRES_CONFIRMATION",checkout,authentication,amounts.mapValues{it.value.size},recognized,visited)
  return CheckoutObservation(pkg,version,checkout,authentication,amounts.mapValues{it.value.toSet()})
 }
 private fun notifyCandidate(){
  val candidate=CheckoutDetection.current();val manager=getSystemService(NotificationManager::class.java)
  if(candidate?.amountWon==null){manager.cancel(401);return}
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
  val intent=Intent(this,MainActivity::class.java).putExtra("open_checkout",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
  val pending=PendingIntent.getActivity(this,401,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  manager.notify(401,Notification.Builder(this,"checkout_candidates").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("주문서 금액 확인이 필요합니다").setContentText("카드 어드바이저에서 금액과 결제 방식을 직접 확인해 주세요.").setVisibility(Notification.VISIBILITY_SECRET).setContentIntent(pending).setAutoCancel(true).build())
 }
 private fun clear(){previousSample=null;handoff.reset();scheduled?.let(handler::removeCallbacks);handler.removeCallbacksAndMessages(null);CheckoutDetection.clear();getSystemService(NotificationManager::class.java).cancel(401)}
 override fun onInterrupt(){clear()}
 override fun onDestroy(){refreshHandler.removeCallbacksAndMessages(null);previousSample=null;clear();preferences.unregisterOnSharedPreferenceChangeListener(listener);runCatching{unregisterReceiver(receiver)};super.onDestroy()}
}
