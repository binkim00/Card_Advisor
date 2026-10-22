package dev.cardadvisor.android

import androidx.activity.compose.LocalActivity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.UUID

@Composable internal fun FinanceEditor(api:ApiClient,onBusyChanged:(Boolean)->Unit,onInvalidated:()->Unit,onChanged:suspend()->Unit,initialMappingOrganization:String?=null,onMappingOpened:()->Unit={}){
 val scope=rememberCoroutineScope { kotlinx.coroutines.Dispatchers.Main.immediate };val keyboard=LocalSoftwareKeyboardController.current;val focus=LocalFocusManager.current
 var ready by remember{mutableStateOf<FinanceReadiness?>(null)};var items by remember{mutableStateOf(emptyList<FinanceConnection>())}
 var busy by remember{mutableStateOf(false)};var stale by remember{mutableStateOf(true)};var expired by remember{mutableStateOf(false)}
 var notice by remember{mutableStateOf("금융 연결을 불러옵니다.")}
 var form by remember{mutableStateOf<String?>(null)};var selected by remember{mutableStateOf<FinanceConnection?>(null)}
 var removeTarget by remember{mutableStateOf<FinanceConnection?>(null)}
 var mappingConnection by remember{mutableStateOf<String?>(null)}
 var syncConnection by remember{mutableStateOf<String?>(null)}
 var org by remember{mutableStateOf("")};var login by remember{mutableStateOf("")};var password by remember{mutableStateOf("")};var consent by remember{mutableStateOf(false)}
 val activity=requireNotNull(LocalActivity.current);val lifecycle=LocalLifecycleOwner.current.lifecycle
 fun clear(){login="";password="";consent=false}
 DisposableEffect(lifecycle){
  val wasSecure=activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0
  activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
  val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)clear()};lifecycle.addObserver(observer)
  onDispose{lifecycle.removeObserver(observer);if(!wasSecure)activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)}
 }
 fun action(mutation:Boolean=false,block:suspend()->Unit){
  if(busy||expired)return
  busy=true;onBusyChanged(true);focus.clearFocus();keyboard?.hide()
  scope.launch{try{block();currentCoroutineContext().ensureActive()}
   catch(e:CancellationException){throw e}
   catch(e:ApiException){currentCoroutineContext().ensureActive();stale=true
    notice=when(e.status){
     401->{expired=true;clear();"로그인이 만료되었습니다. 나가기 후 다시 로그인해 주세요."}
     409->"연결 상태가 변경되었습니다. 최신 연결을 조회한 뒤 다시 선택해 주세요."
     503->"금융 연결 서버가 준비되지 않았습니다. 저장된 연결 조회만 가능합니다."
     422->"기관 코드, 인증 입력과 동의를 확인해 주세요."
     else->"연결 정보를 불러오지 못했습니다. 최신 연결 조회로 다시 확인해 주세요."
    }
   }catch(e:Exception){currentCoroutineContext().ensureActive();stale=true
    notice=if(mutation)"전송 결과를 확인할 수 없습니다. 재전송하지 말고 최신 연결을 조회해 주세요." else "연결 정보를 불러오지 못했습니다. 네트워크를 확인하고 다시 조회해 주세요."
   }finally{if(currentCoroutineContext().isActive){busy=false;onBusyChanged(false)}}
  }
 }
 suspend fun load(){
  stale=true
  val r=FinanceReadiness.parse(api.request("/v1/finance/codef/status"))
  val connections=financeConnections(api.request("/v1/finance/connections"))
  currentCoroutineContext().ensureActive();ready=r;items=connections;stale=false;form=null;selected=null;clear();notice="최신 연결을 조회했습니다."
 }
 fun edit(kind:String,c:FinanceConnection?=null){clear();form=kind;selected=c;org=c?.organization?:""}
 fun submit(){
  val kind=form?:return;val target=selected
  val body=JSONObject();val path=if(kind=="CREATE")"/v1/finance/connections" else "/v1/finance/connections/${target?.id}/"+when(kind){"REAUTHORIZE"->"reauthorize";"RETRY"->"retry";else->"disconnect"}
  if(kind=="CREATE")body.put("organization",org) else body.put("expectedVersion",requireNotNull(target).version)
  if(kind!="DISCONNECT")body.put("loginId",login).put("password",password).put("consent",consent).put("consentVersion","FINANCE_LINK_V1")
  val requestKey=UUID.randomUUID().toString();clear();form=null
  action(true){
   stale=true
   val response=try{JSONObject(api.request(path,"POST",body,requestKey))}finally{body.remove("password");body.remove("loginId")}
   val connection=FinanceConnection.parse(response.getJSONObject("connection"));val operation=response.getJSONObject("operation")
   require(operation.getString("connection_id")==connection.id);val status=financeOperation(operation)
   currentCoroutineContext().ensureActive();items=items.filterNot{it.id==connection.id}+connection;stale=false;selected=null;notice=financeOutcome(status)
  }
 }
 LaunchedEffect(Unit){action{load();if(initialMappingOrganization!=null){val match=items.firstOrNull{it.organization==initialMappingOrganization&&it.hasId};if(match!=null)mappingConnection=match.id else notice="먼저 해당 카드사에 연결하고 이용내역을 불러와 주세요. 상품 등록만으로 금융 카드가 연결되지는 않습니다.";onMappingOpened()}}}
 if(mappingConnection!=null){FinancialCardMappingEditor(api,requireNotNull(mappingConnection),onBusyChanged,{onInvalidated();onChanged()},onClose={mappingConnection=null;action{load()}});return}
 if(syncConnection!=null){
  FinanceSyncEditor(api,requireNotNull(syncConnection),ready?.ready==true,onBusyChanged,onInvalidated,onChanged,onClose={syncConnection=null;action{load()}})
  return
 }
 Text("카드사 연결을 한곳에서",style=MaterialTheme.typography.titleLarge)
 Text("이용내역을 가져오고, 내 카드 상품과 연결하세요.",color=MaterialTheme.colorScheme.onSurfaceVariant)
 Text(notice,Modifier.testTag("financeNotice"))
 if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
 OutlinedButton(enabled=!busy&&!expired,onClick={action{load()}},modifier=Modifier.testTag("financeRefresh")){Text("최신 연결 조회")}
 ready?.let{r->
  Text(if(r.synthetic)"합성 응답 검증용 · 실제 금융정보가 아닙니다." else "CODEF ${r.environment} · 실제 기관별 지원은 확인이 필요합니다.",Modifier.testTag("financeEnvironment"))
  if(!r.ready)Text("금융 연결이 비활성 상태입니다. 로컬 설정과 이용 승인을 준비한 뒤 사용할 수 있습니다.")
 }
 if(stale)Text("최신 상태를 확인하기 전까지 연결 변경을 보류합니다.")
 val enabled=!busy&&!stale&&!expired&&ready?.ready==true
 if(!stale&&items.isEmpty())DesignPanel { Text("아직 연결한 카드사가 없어요",style=MaterialTheme.typography.titleMedium); Text("저장된 금융 연결이 없습니다.",Modifier.testTag("financeEmpty")); Text("카드사를 연결하면 이용내역을 가져와 가계부에서 확인할 수 있어요.") }
 Button(enabled=enabled,onClick={edit("CREATE")},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("financeNew")){Text("금융 연결 추가")}
 if(form!=null){
  Text(when(form){"CREATE"->"새 금융 연결";"REAUTHORIZE"->"아이디·비밀번호 재인증";"RETRY"->"실패한 연결 인증정보 다시 입력";else->"금융 연결 해제 확인"},style=MaterialTheme.typography.titleMedium)
  if(form!="DISCONNECT"){
   if(form=="CREATE")OutlinedTextField(org,{org=it.take(4)},label={Text("공식 확인된 카드사 기관 코드 · 4자리")},singleLine=true,enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag("financeOrg"))
   if(form=="RETRY")Text("기관 $org · 변경할 아이디와 비밀번호를 새로 입력해 주세요. 자동으로 재전송하지 않습니다.")
   Text(if(ready?.synthetic==true)"검증용 기관 코드와 가상 아이디·비밀번호만 입력하세요." else "기관 코드와 ID/PW 지원을 확인한 카드사만 입력하세요. 인증서·OTP 이어하기는 아직 지원하지 않습니다.")
   OutlinedTextField(login,{login=it.take(128)},label={Text("금융기관 아이디")},singleLine=true,enabled=!busy,keyboardOptions=KeyboardOptions(autoCorrectEnabled=false),modifier=Modifier.fillMaxWidth().testTag("financeLogin"))
   OutlinedTextField(password,{password=it.take(256)},label={Text("금융기관 비밀번호")},singleLine=true,enabled=!busy,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false),modifier=Modifier.fillMaxWidth().testTag("financePassword"))
   Text("연결·재인증을 위해 인증정보를 서버와 CODEF에 전송합니다. 아이디·비밀번호는 앱·서버에 저장하지 않으며, 서버는 연결 식별자만 암호화해 보관합니다.")
   Row{Checkbox(consent,{consent=it},modifier=Modifier.testTag("financeConsent"));Text("금융 연결을 위한 전송·보관에 동의합니다. (FINANCE_LINK_V1)")}
  }else Text("기관 ${selected?.organization}의 연결 해제를 요청합니다. 제공자가 성공을 확인한 뒤 연결 식별자를 제거합니다. 기존 가계부 내역은 유지합니다.")
  val valid=form=="DISCONNECT"||(org.matches(Regex("[0-9]{4}"))&&login.isNotBlank()&&password.isNotEmpty()&&consent)
  val duplicate=form=="CREATE"&&items.any{it.organization==org&&it.state !in listOf(FinanceState.DISCONNECTED,FinanceState.FAILED)}
  if(duplicate)Text("이미 연결되었거나 확인 중인 기관입니다. 아래 연결 상태를 조회해 주세요.")
  Button(enabled=enabled&&valid&&!duplicate,onClick={submit()},modifier=Modifier.testTag("financeSubmit")){Text(if(form=="DISCONNECT")"해제 요청 확인" else "동의하고 요청")}
  TextButton(onClick={clear();form=null},enabled=!busy){Text("입력 닫기")}
 }
 for(c in items){key(c.id){
  DesignPanel {
   FinanceConnectionHeading(c)
   Text("확인된 승인액은 예산에 반영해요. 실제 실적·혜택은 추가 확인이 필요합니다.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Text("저장 버전 ${c.version}",Modifier.testTag("financeVersion_${c.organization}"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Button(enabled=!busy&&!stale&&!expired,onClick={clear();form=null;syncConnection=c.id},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("financeTransactions_${c.organization}")){Text("기간별 거래·보류 확인")}
   OutlinedButton(enabled=!busy&&!stale&&!expired&&c.hasId,onClick={clear();form=null;mappingConnection=c.id},modifier=Modifier.testTag("financeCardMapping_${c.organization}")){Text("조회 카드와 내 상품 연결")}
   if(c.held)Text("처리 결과 확인이 필요합니다. 아래 공급자 등록 상태 확인은 원래 요청을 다시 전송하지 않습니다. 연결 식별자가 없으면 CODEF 지원 확인이 필요합니다.")
   if(c.state in listOf(FinanceState.UNKNOWN,FinanceState.AUTH_REQUIRED))OutlinedButton(enabled=enabled,onClick={action{
    val checked=JSONObject(api.request("/v1/finance/connections/${c.id}/reconcile","POST",JSONObject().put("expectedVersion",c.version),UUID.randomUUID().toString()))
    load();notice=when(checked.getString("decision")){"DISCONNECTION_CONFIRMED"->"공급자에서 해제된 상태를 확인했습니다.";"REGISTRATION_PRESENT_REAUTH_REQUIRED"->"공급자 등록은 남아 있습니다. 인증정보의 유효성은 확인되지 않았으므로 재인증해 주세요.";"PROVIDER_SUPPORT_REQUIRED","REGISTRATION_ABSENT_SUPPORT_REQUIRED"->"안전하게 대조할 근거가 부족합니다. CODEF 지원을 통한 확인이 필요합니다. 새 연결을 반복 요청하지 마세요.";else->"공급자 확인을 완료하지 못했습니다. 보류 상태를 유지합니다."}
   }},modifier=Modifier.testTag("financeReconcile_${c.organization}")){Text("공급자 등록 상태 확인")}
   OutlinedButton(enabled=!busy&&!expired,onClick={action{
    val latest=FinanceConnection.parse(JSONObject(api.request("/v1/finance/connections/${c.id}")))
    val outcome=latest.operationId?.let{financeOperation(JSONObject(api.request("/v1/finance/operations/$it")))}
    currentCoroutineContext().ensureActive();items=items.map{if(it.id==latest.id)latest else it};form=null;selected=null;clear();notice=outcome?.let{financeOutcome(it)}?:"연결 상태를 조회했습니다."
   }},modifier=Modifier.testTag("financeInspect_${c.organization}")){Text("이 연결·요청 결과 조회")}
   if(c.state==FinanceState.FAILED){
    Text(when(c.error){"CODEF_AUTHENTICATION"->"CODEF 키/토큰 인증 단계에서 거절됐을 수 있습니다. 카드사 비밀번호 오류로 단정할 수 없습니다.";else->"연결에 실패했습니다. 인증정보를 다시 입력하거나 실패 기록을 삭제할 수 있습니다."})
    if(c.error!=null)Text("오류 코드: "+c.error,style=MaterialTheme.typography.bodySmall)
    c.providerCode?.let{Text("CODEF 세부 코드: "+it,style=MaterialTheme.typography.bodySmall)}
   }
   if(c.canRetry){
    OutlinedButton(enabled=enabled,onClick={edit("RETRY",c)},modifier=Modifier.testTag("financeRetry_${c.organization}")){Text("인증정보 수정·다시 연결")}
    OutlinedButton(enabled=!busy&&!stale&&!expired,onClick={removeTarget=c},modifier=Modifier.testTag("financeRemove_${c.organization}")){Text("실패 기록 삭제")}
   }
   OutlinedButton(enabled=enabled&&c.canChange,onClick={edit("REAUTHORIZE",c)},modifier=Modifier.testTag("financeReauth_${c.organization}")){Text("재인증")}
   OutlinedButton(enabled=enabled&&c.canChange,onClick={edit("DISCONNECT",c)},modifier=Modifier.testTag("financeDisconnect_${c.organization}")){Text("해제")}
  }
 }}
 removeTarget?.let{target->AlertDialog(onDismissRequest={if(!busy)removeTarget=null},title={Text("실패 기록 삭제 확인")},text={Text("Connected ID가 없는 확정 실패 기록만 목록에서 제거합니다. 외부 계정 해제 요청은 보내지 않으며 작업 이력은 보존합니다. 같은 기관을 다시 추가할 수 있습니다.")},confirmButton={Button(enabled=!busy,onClick={removeTarget=null;action(true){api.request("/v1/finance/connections/${target.id}/archive","POST",JSONObject().put("expectedVersion",target.version));items=items.filterNot{it.id==target.id};notice="실패 기록을 삭제했습니다. 외부 해제 요청은 보내지 않았습니다."}},modifier=Modifier.testTag("financeRemoveConfirm")){Text("실패 기록 삭제 확인")}},dismissButton={TextButton(enabled=!busy,onClick={removeTarget=null}){Text("취소")}})}
}
