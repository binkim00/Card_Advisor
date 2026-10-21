package dev.cardadvisor.android
import android.content.*
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable internal fun DetectionEditor(api:ApiClient,onBusyChanged:(Boolean)->Unit,onConfirmed:(String,String,String,String)->Unit,onManual:()->Unit){
 val context=LocalContext.current;val prefs=remember{context.getSharedPreferences("advisor_detection",0)}
 val scope=rememberCoroutineScope { Dispatchers.Main.immediate };val candidate by CheckoutDetection.pending.collectAsState()
 var consent by remember{mutableStateOf(false)};var enabled by remember{mutableStateOf(prefs.getBoolean("enabled",false))}
 var busy by remember{mutableStateOf(false)};var notice by remember{mutableStateOf("동의와 권한 상태를 확인합니다.")}
 var total by remember{mutableStateOf("")};var points by remember{mutableStateOf("0")};var money by remember{mutableStateOf("0")};var confirmed by remember{mutableStateOf(false)}
 fun refresh(){if(busy)return;busy=true;onBusyChanged(true);scope.launch{try{
  val p=JSONObject(api.request("/v1/me/preferences"));val rows=p.getJSONArray("consents")
  consent=(0 until rows.length()).any{val r=rows.getJSONObject(it);r.getString("purpose")=="CHECKOUT_DETECTION"&&r.getBoolean("accepted")&&r.getString("policy_version")==p.getString("policyVersion")}
  prefs.edit().putBoolean("consent",consent).apply();if(!consent){enabled=false;prefs.edit().putBoolean("enabled",false).apply();CheckoutDetection.clear()}
  notice=if(consent)"동의가 확인됐습니다. 감지는 직접 켜고 끌 수 있습니다." else "먼저 동의·계정 관리에서 쇼핑 감지 안내를 확인하고 동의해 주세요."
 }catch(e:CancellationException){throw e}catch(e:Exception){consent=false;enabled=false;prefs.edit().putBoolean("enabled",false).apply();CheckoutDetection.clear();notice="동의를 확인하지 못해 감지를 중단했습니다. 네트워크를 확인하고 다시 조회해 주세요."}finally{if(currentCoroutineContext().isActive){busy=false;onBusyChanged(false)}}}}
 LaunchedEffect(Unit){refresh()}
 LaunchedEffect(candidate?.generation){total=candidate?.amountWon?.toString()?:"";points="0";money="0";confirmed=false}
 val systemEnabled=(context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager).getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any{it.resolveInfo.serviceInfo.packageName==context.packageName&&it.resolveInfo.serviceInfo.name.endsWith("CheckoutAccessibilityService")}
 Text("쇼핑 주문서 감지",style=MaterialTheme.typography.titleLarge)
 Text("네이버플러스 스토어·무신사 주문서의 금액 후보를 기기에서 확인합니다. 전체 화면과 배송지·인증정보는 저장·전송하지 않습니다. 앱 버전별 실제 주문 경로 검증 전이므로 자동 확정하지 않습니다.")
 Text(notice,Modifier.testTag("detectionNotice"))
 OutlinedButton(enabled=!busy,onClick={refresh()},modifier=Modifier.testTag("detectionRefresh")){Text("동의·권한 상태 다시 확인")}
 Text(if(systemEnabled)"Android 접근성 권한 사용 중" else "Android 접근성 권한 꺼짐 · 수동 입력 가능")
 OutlinedButton(enabled=consent&&!busy,onClick={context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))},modifier=Modifier.testTag("detectionPermission")){Text("Android 접근성 설정 열기")}
 Row{Switch(enabled=consent&&!busy,checked=enabled,onCheckedChange={enabled=it;prefs.edit().putBoolean("enabled",it).apply();if(!it)CheckoutDetection.clear()},modifier=Modifier.testTag("detectionEnabled"));Text("지원 쇼핑 앱에서 감지 사용")}
 if(!systemEnabled||!enabled)Text("금액 감지는 시작되지 않습니다. 권한을 허용하지 않아도 아래 수동 입력은 사용할 수 있습니다.")
 val current=candidate
 LaunchedEffect(current?.generation){CheckoutAudit.confirmation(context,current!=null,CheckoutDetection.current()?.generation==current?.generation&&current!=null)}
 if(current==null)Text("읽어 온 주문서가 없습니다. 네이버·무신사 주문서를 연 뒤 최근 앱에서 카드 어드바이저로 바로 돌아와 주세요. 홈 화면 등에 오래 머물거나 다른 앱으로 이동하면 후보를 지웁니다.") else DesignPanel{
  Text((if(current.merchant=="NAVER_PLUS_STORE")"네이버플러스 스토어" else "무신사")+" · 앱 버전 "+current.appVersion)
  Text(if(current.amountWon==null)"금액이 없거나 후보가 충돌해 추천을 보류합니다." else "읽은 금액은 후보입니다. 원화면과 아래 항목을 직접 대조해 주세요.")
  PaymentFields(total,points,money,{total=it;confirmed=false},{points=it;confirmed=false},{money=it;confirmed=false})
  Row{Checkbox(confirmed,{confirmed=it},modifier=Modifier.testTag("detectionConfirmFacts"));Text("최신 주문 총액·포인트·머니·국내 일반 상품·일시불·단일 카드 승인 조건을 직접 확인했습니다.")}
  val o=total.toLongOrNull();val p=points.toLongOrNull();val m=money.toLongOrNull();val valid=o!=null&&p!=null&&m!=null&&o>0&&o<=10000000&&p>=0&&m>=0&&p+m<=o
  Button(enabled=confirmed&&valid&&!busy,onClick={
   if(CheckoutDetection.current()?.generation!=current.generation){notice="주문서 정보가 바뀌거나 만료됐습니다. 다시 확인해 주세요.";confirmed=false}
   else{onConfirmed(total,points,money,current.merchant);CheckoutDetection.clear()}
  },modifier=Modifier.testTag("detectionUse")){Text("확인한 금액으로 카드 비교")}
 }
 OutlinedButton(enabled=!busy,onClick=onManual,modifier=Modifier.testTag("detectionManual")){Text("수동 입력으로 카드 비교")}
}
