package dev.cardadvisor.android

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable internal fun AccountSettingsEditor(api:ApiClient,onBusyChanged:(Boolean)->Unit,onInvalidated:()->Unit,onDeleted:()->Unit){
 val scope=rememberCoroutineScope { Dispatchers.Main.immediate }
 var verifyingEmail by remember{mutableStateOf(false)}
 var profile by remember{mutableStateOf<JSONObject?>(null)};var busy by remember{mutableStateOf(false)}
 var stale by remember{mutableStateOf(true)};var notice by remember{mutableStateOf("설정을 불러옵니다.")}
 var disclosure by remember{mutableStateOf<String?>(null)};var deleting by remember{mutableStateOf(false)}
 var password by remember{mutableStateOf("")};var confirmation by remember{mutableStateOf("")}
 val context=LocalContext.current;val activity=context as Activity;val lifecycle=LocalLifecycleOwner.current.lifecycle
 DisposableEffect(lifecycle){
  val wasSecure=activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0
  activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
  val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP){password="";confirmation="";disclosure=null}}
  lifecycle.addObserver(observer)
  onDispose{lifecycle.removeObserver(observer);if(!wasSecure)activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)}
 }
 fun action(block:suspend()->Unit){if(busy)return;busy=true;onBusyChanged(true)
  scope.launch{try{block()}catch(e:CancellationException){throw e}catch(e:Exception){stale=true;notice=if(e is ApiException&&e.status==409)"상태가 변경됐습니다. 연결 해제가 필요한지 확인하고 최신 설정을 조회해 주세요." else e.message?:"설정을 처리하지 못했습니다."}finally{if(currentCoroutineContext().isActive){busy=false;onBusyChanged(false)}}}
 }
 suspend fun load(){profile=JSONObject(api.request("/v1/me"));stale=false;notice="저장된 설정을 조회했습니다."}
 fun enabled(purpose:String):Boolean{
  val p=profile?.optJSONObject("preferences")?:return false;val rows=p.getJSONArray("consents")
  return (0 until rows.length()).any{val r=rows.getJSONObject(it);r.getString("purpose")==purpose&&r.getBoolean("accepted")&&r.getString("policy_version")==p.getString("policyVersion")}
 }
 fun consent(purpose:String,accepted:Boolean){action{
  val p=requireNotNull(profile).getJSONObject("preferences")
  val saved=JSONObject(api.request("/v1/me/consents/$purpose","PUT",JSONObject().put("accepted",accepted).put("policyVersion",p.getString("policyVersion")).put("expectedVersion",p.getLong("version"))))
  profile=JSONObject(requireNotNull(profile).toString()).put("preferences",saved);stale=false;onInvalidated()
  if(purpose=="CHECKOUT_DETECTION")context.getSharedPreferences("advisor_detection",0).edit().putBoolean("consent",accepted).apply()
  notice=if(accepted)"선택한 목적의 동의를 저장했습니다." else "동의를 철회했습니다. 해당 목적의 새 처리를 중단합니다."
 }}
 LaunchedEffect(Unit){action{load()}}
 if(verifyingEmail)AccountRecoveryEditor(api,true,onBusyChanged=onBusyChanged,onClose={verifyingEmail=false})
 OutlinedButton(enabled=!busy,onClick={verifyingEmail=true},modifier=Modifier.testTag("emailVerificationOpen")){Text("이메일 확인 관리")}
 Text("동의·계정 관리",style=MaterialTheme.typography.titleLarge)
 Text(notice,Modifier.testTag("accountNotice"));if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
 profile?.let{Text(it.getString("email"))}
 OutlinedButton(enabled=!busy,onClick={action{load()}},modifier=Modifier.testTag("accountRefresh")){Text("최신 설정 조회")}
 for((purpose,label) in listOf("AI" to "AI 분석", "CHECKOUT_DETECTION" to "쇼핑 주문서 감지")){
  DesignPanel{
   Text(label,style=MaterialTheme.typography.titleMedium)
   Text(if(enabled(purpose))"동의함" else "동의하지 않음",Modifier.testTag("consentState_$purpose"))
   Text(if(purpose=="AI")"선택 기능입니다. 카드 추천과 금액 계산은 AI 동의 없이도 사용할 수 있습니다." else "선택 기능입니다. 감지를 켜지 않아도 수동 결제 입력을 사용할 수 있습니다.")
   Button(enabled=!busy&&!stale,onClick={if(enabled(purpose))consent(purpose,false) else disclosure=purpose},modifier=Modifier.testTag("consentAction_$purpose")){Text(if(enabled(purpose))"$label 동의 철회" else "$label 동의 내용 확인")}
  }
 }
 Text("계정 삭제 시 앱의 원장·예산·설정·세션을 삭제합니다. 외부 금융 연결은 먼저 해제해야 합니다. 운영 백업의 삭제 정책과 외부 제공자 보존은 별도로 적용됩니다.")
 OutlinedButton(enabled=!busy,onClick={deleting=!deleting;password="";confirmation=""},modifier=Modifier.testTag("accountDeleteOpen")){Text("계정 삭제")}
 if(api.refreshToken!=null)OutlinedButton(enabled=!busy,onClick={action{api.renewSession();load();notice="세션을 갱신했습니다."}},modifier=Modifier.testTag("accountRenew")){Text("로그인 세션 갱신")}
 if(deleting){
  OutlinedTextField(password,{password=it.take(128)},label={Text("현재 비밀번호")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("accountDeletePassword"))
  OutlinedTextField(confirmation,{confirmation=it.take(16)},label={Text("계정 삭제 입력")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("accountDeleteConfirmation"))
  Button(enabled=!busy&&!stale&&password.isNotEmpty()&&confirmation=="계정 삭제",onClick={val body=JSONObject().put("password",password).put("confirmation",confirmation);password="";confirmation="";action{try{api.request("/v1/me/deletion","POST",body)}finally{body.remove("password")};context.getSharedPreferences("advisor_detection",0).edit().clear().apply();api.clearSession();onDeleted()}},modifier=Modifier.testTag("accountDeleteConfirm")){Text("모든 계정 데이터 삭제 확인")}
 }
 disclosure?.let{purpose->AlertDialog(onDismissRequest={disclosure=null},title={Text(if(purpose=="AI")"AI 분석에 사용할 정보" else "쇼핑 감지 접근성 사용 안내")},text={Text(if(purpose=="AI")"동의 후 요청할 때 카테고리별 집계와 계산 결과의 최소 항목을 OpenAI로 전송할 수 있습니다. 인증정보, 카드번호, 계좌번호, 배송지, 연락처, 원시 화면은 보내지 않습니다. 생성 결과는 검증 후 제안으로 표시하며 예산이나 분류를 자동 변경하지 않습니다. 설정에서 언제든 철회할 수 있습니다." else "사용자가 켠 동안 지원 쇼핑 앱의 주문서에서 결제 금액과 결제 방식만 기기 안에서 추출합니다. 화면 전체를 저장하거나 서버·AI에 보내지 않으며, 자동 카드 선택이나 결제 버튼 조작을 하지 않습니다. 금액은 사용자 확인 후에만 추천에 사용합니다. 앱 이탈·잠금·동의 철회 시 이전 추천을 무효화합니다.")},confirmButton={Button(onClick={disclosure=null;consent(purpose,true)},modifier=Modifier.testTag("consentAccept")){Text("내용을 확인하고 동의")}},dismissButton={TextButton(onClick={disclosure=null}){Text("동의하지 않음")}})}
}
