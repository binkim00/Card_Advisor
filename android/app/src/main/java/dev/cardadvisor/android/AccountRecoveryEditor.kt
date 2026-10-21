package dev.cardadvisor.android
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable internal fun AccountRecoveryEditor(api:ApiClient,verification:Boolean,onBusyChanged:(Boolean)->Unit,onClose:()->Unit){
 val scope=rememberCoroutineScope{Dispatchers.Main.immediate};val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
 var email by remember{mutableStateOf("")};var token by remember{mutableStateOf("")};var password by remember{mutableStateOf("")};var repeat by remember{mutableStateOf("")}
 var busy by remember{mutableStateOf(false)};var verified by remember{mutableStateOf(false)};var configured by remember{mutableStateOf<Boolean?>(null)};var notice by remember{mutableStateOf("확인 코드는 이메일에서 확인해 앱에 직접 입력하세요. 채팅으로 보내지 마세요.")}
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP){token="";password="";repeat=""}};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
 fun action(block:suspend()->Unit){if(busy)return;busy=true;notice="처리 중입니다.";onBusyChanged(true);focus.clearFocus();keyboard?.hide();scope.launch{try{block()}catch(e:CancellationException){throw e}catch(e:Exception){notice=when{e is ApiException&&e.code=="ACCOUNT_ACTION_REJECTED"->"코드가 만료되었거나 이미 사용됐습니다. 새 코드를 요청해 주세요.";e is ApiException&&e.code=="ACCOUNT_MAIL_UNAVAILABLE"->"서버의 이메일 발송 설정이 아직 준비되지 않았습니다. 기존 로그인은 계속 사용할 수 있습니다.";e is ApiException&&e.status==429->"요청이 많습니다. 잠시 후 다시 시도해 주세요.";else->"처리 결과를 확인하지 못했습니다. 연결과 입력 조건을 확인해 주세요."}}finally{busy=false;onBusyChanged(false)}}}
 LaunchedEffect(verification){if(verification)action{val s=JSONObject(api.request("/v1/me/email-verification"));verified=s.getBoolean("emailVerified");configured=s.getBoolean("mailConfigured");notice=if(verified)"이메일 확인이 완료된 계정입니다." else if(configured==true)"이메일 확인 코드를 요청할 수 있습니다." else "이메일 발송 설정이 아직 준비되지 않았습니다."}}
 Dialog(onDismissRequest={if(!busy)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,securePolicy=SecureFlagPolicy.SecureOn)){
 Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Text(if(verification)"이메일 확인" else "비밀번호 재설정",style=MaterialTheme.typography.headlineSmall)
  Text(notice,Modifier.testTag("recoveryNotice"));if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(!verification)OutlinedTextField(email,{email=it.take(254)},enabled=!busy,label={Text("가입한 이메일")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("recoveryEmail"))
  Button(enabled=!busy&&!verified&&configured!=false,onClick={action{if(verification){api.request("/v1/me/email-verification/request","POST");notice="확인 코드를 요청했습니다. 받은 이메일의 코드를 입력해 주세요."}else{api.request("/v1/auth/password-reset/request","POST",JSONObject().put("email",email));notice="등록된 이메일이고 발송이 가능한 경우 재설정 코드를 안내합니다. 이메일을 확인해 주세요."}}},modifier=Modifier.fillMaxWidth().testTag("recoveryRequest")){Text("이메일로 확인 코드 요청")}
  if(!verified){
   OutlinedTextField(token,{token=it.take(128).trim()},enabled=!busy,label={Text("이메일로 받은 확인 코드")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth().testTag("recoveryToken"))
   Text("코드는 15분 동안 한 번만 유효합니다. 새로 요청하면 이전 코드는 사용할 수 없습니다.")
   if(!verification){
    OutlinedTextField(password,{password=it.take(128)},enabled=!busy,label={Text("새 비밀번호 · 12~128자")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth().testTag("recoveryPassword"))
    OutlinedTextField(repeat,{repeat=it.take(128)},enabled=!busy,label={Text("새 비밀번호 다시 입력")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth().testTag("recoveryRepeat"))
    Text("재설정하면 다른 기기를 포함한 기존 로그인 세션이 모두 종료됩니다. 저장된 카드·거래는 유지됩니다.")
   }
   Button(enabled=!busy&&token.length==43&&(verification||(password.length in 12..128&&password==repeat)),onClick={val value=token;val secret=password;action{
    if(verification){val result=JSONObject(api.request("/v1/me/email-verification/confirm","POST",JSONObject().put("token",value)));verified=result.getBoolean("emailVerified");notice="이메일 확인을 완료했습니다."}
    else{api.request("/v1/auth/password-reset/confirm","POST",JSONObject().put("token",value).put("newPassword",secret));notice="비밀번호를 변경했습니다. 새 비밀번호로 로그인해 주세요."}
    token="";password="";repeat=""
   }},modifier=Modifier.fillMaxWidth().testTag("recoveryConfirm")){Text(if(verification)"이메일 확인 완료" else "비밀번호 변경")}
  }
  OutlinedButton(enabled=!busy,onClick=onClose,modifier=Modifier.testTag("recoveryBack")){Text(if(verification)"계정 설정으로 돌아가기" else "로그인으로 돌아가기")}
 }} }
}
