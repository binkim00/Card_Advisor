package dev.cardadvisor.android
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable internal fun FinanceSyncAuthentication(run:SyncRun,enabled:Boolean,onSubmit:(String)->Unit){
 var answer by remember(run.id,run.authVersion){mutableStateOf("")}
 var confirmed by remember(run.id,run.authVersion){mutableStateOf(false)}
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP){answer="";confirmed=false}};lifecycle.addObserver(observer);onDispose{answer="";confirmed=false;lifecycle.removeObserver(observer)}}
 DesignPanel{
  Text("추가 인증",style=MaterialTheme.typography.titleMedium)
  Text(syncOutcome(run.status))
  if(run.status=="AUTH_REQUIRED"&&run.authVersion!=null){
   Text("인증 대기는 최대 3분입니다. 입력값은 저장하지 않습니다. 만료되면 내역을 새로고침한 뒤 조회를 다시 시작하세요.")
   if(run.authMethod=="simpleAuth")Row{Checkbox(confirmed,{confirmed=it},enabled=enabled,modifier=Modifier.testTag("syncAuthConfirmed"));Text("인증 앱에서 본인 인증을 마쳤습니다")}
   else OutlinedTextField(answer,{answer=it.filter{c->c.isLetterOrDigit()&&c.code<128}.take(32)},enabled=enabled,label={Text(if(run.authMethod=="emailAuthNo")"이메일 인증번호" else "문자 인증번호")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("syncAuthAnswer"))
   Button(enabled=enabled&&(if(run.authMethod=="simpleAuth")confirmed else answer.isNotBlank()),onClick={val submitted=if(run.authMethod=="simpleAuth")"1" else answer;answer="";confirmed=false;onSubmit(submitted)},modifier=Modifier.testTag("syncAuthSubmit")){Text("인증 확인하고 이용내역 불러오기")}
  }
 }
}
