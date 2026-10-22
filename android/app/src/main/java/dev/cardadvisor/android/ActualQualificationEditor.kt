package dev.cardadvisor.android
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.*

@Composable internal fun ActualQualificationEditor(api:ApiClient,cardId:String,month:String,onChanged:suspend()->Unit,onClose:()->Unit){
 val scope=rememberCoroutineScope{Dispatchers.Main.immediate}
 var saved by remember{mutableStateOf<JSONObject?>(null)};var amount by remember{mutableStateOf("")};var source by remember{mutableStateOf("USER_INPUT")};var asOf by remember{mutableStateOf(LocalDate.now(ZoneId.of("Asia/Seoul")).toString())};var first by remember{mutableStateOf("")};var confirmed by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};var stale by remember{mutableStateOf(true)};var notice by remember{mutableStateOf("저장된 자격 근거를 불러옵니다.")}
 suspend fun load(){val s=JSONObject(api.request("/v1/cards/$cardId/qualification?month=$month"));check(s.getString("cardId")==cardId&&s.getString("month")==month);saved=s;amount=if(s.isNull("previousPerformanceWon"))"" else s.getLong("previousPerformanceWon").toString();source=if(s.getString("previousSource")=="USER_ESTIMATE")"USER_ESTIMATE" else "USER_INPUT";asOf=if(s.isNull("asOf"))LocalDate.now(ZoneId.of("Asia/Seoul")).toString() else s.getString("asOf");first=if(s.isNull("firstUseDate"))"" else s.getString("firstUseDate");confirmed=false;stale=false}
 fun action(block:suspend()->Unit){if(busy)return;busy=true;scope.launch{try{block()}catch(e:CancellationException){throw e}catch(e:Exception){stale=true;notice=when{e is ApiException&&e.status==409->"저장된 값이 바뀌었습니다. 입력은 보존했습니다. 최신 근거를 다시 조회해 주세요.";e is ApiException&&e.status==422->"전월 마감 인정액·확인일·최초 사용 등록일을 확인해 주세요.";else->"결과를 확인하지 못했습니다. 다시 저장하기 전에 조회해 주세요."}}finally{busy=false}}}
 LaunchedEffect(cardId,month){action{load();notice="이번 혜택 자격을 계산합니다. 이번 달의 다음 달용 실적은 바꾸지 않습니다."}}
 Dialog(onDismissRequest={if(!busy)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,securePolicy=SecureFlagPolicy.SecureOn)){Surface{Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("현재 혜택 자격 계산",style=MaterialTheme.typography.headlineSmall);Text(notice,Modifier.testTag("actualQualificationNotice"));if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  OutlinedButton(enabled=!busy,onClick=onClose){Text("돌아가기")}
  Text("혜택 적용 $month · 실적 기준 ${YearMonth.parse(month).minusMonths(1)}")
  Text("전월 인정액은 카드사에서 확인한 마감 실적 또는 직접 계산한 값입니다. 이번 달 미기록 실적에 더하지 않습니다. 모르면 빈칸으로 두세요.")
  OutlinedTextField(amount,{amount=it.take(12);confirmed=false},enabled=!busy,label={Text("전월 인정액 (원) · 미확인은 빈칸")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("qualificationAmount"))
  if(amount.isNotBlank()){
   Row{FilterChip(source=="USER_INPUT",{source="USER_INPUT"},enabled=!busy,label={Text("직접 확인한 값")});FilterChip(source=="USER_ESTIMATE",{source="USER_ESTIMATE"},enabled=!busy,label={Text("직접 추정한 값")})}
   OutlinedTextField(asOf,{asOf=it.take(10);confirmed=false},enabled=!busy,label={Text("인정액 확인일 YYYY-MM-DD")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("qualificationAsOf"))
  }
  OutlinedTextField(first,{first=it.take(10);confirmed=false},enabled=!busy,label={Text("최초 카드 사용 등록일 · 모르면 빈칸")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("qualificationFirstUse"))
  Text("앱에 카드를 등록한 날짜가 아닙니다. KB Easy all AUTO·하나 트래블GO의 신규 유예는 최초 사용 등록일부터 다음 달 말일까지 기본 구간입니다. 전월액 미확인 시 높은 구간까지 확정하지 않습니다.")
  Row{Checkbox(confirmed,{confirmed=it},enabled=!busy,modifier=Modifier.testTag("qualificationConfirmed"));Text("기준 기간·금액 출처·최초 등록일을 확인했습니다.",Modifier.weight(1f))}
  Button(enabled=!busy&&!stale&&confirmed&&(amount.isBlank()||amount.toLongOrNull()!=null),onClick={action{val old=requireNotNull(saved);val body=JSONObject().put("expectedVersion",old.getLong("version")).put("expectedStateVersion",old.getLong("stateVersion")).put("previousPerformanceWon",if(amount.isBlank())JSONObject.NULL else amount.toLong()).put("previousSource",if(amount.isBlank())"UNKNOWN" else source).put("asOf",if(amount.isBlank())JSONObject.NULL else asOf).put("firstUseDate",first.ifBlank{null}?:JSONObject.NULL).put("confirmed",true);val response=JSONObject(api.request("/v1/cards/$cardId/qualification?month=$month","PUT",body));load();check(saved!!.getLong("version")==response.getLong("version"));onChanged();notice="자격 근거를 저장하고 재조회했습니다. 월 전체 혜택 내역은 다시 확인해 주세요."}},modifier=Modifier.fillMaxWidth().testTag("qualificationSave")){Text("근거 저장하고 현재 자격 계산")}
  OutlinedButton(enabled=!busy,onClick={action{load();notice="최신 저장 근거를 다시 불러왔습니다."}}){Text("저장된 근거 다시 조회 · 입력 초기화")}
  saved?.let{s->val c=s.getJSONObject("calculation");Text(if(c.isNull("qualified"))"현재 자격: 미확인" else if(c.getBoolean("qualified"))"현재 자격: 충족" else "현재 자격: 미충족",Modifier.testTag("qualificationResult"));Text(if(c.isNull("tier"))"확정 구간 미확인 · 최소 ${c.getInt("minimumTier")}구간" else "적용 ${c.getInt("tier")}구간");if(c.getBoolean("registrationGrace"))Text("신규 유예 종료 ${c.getString("graceThrough")}");if(!s.getBoolean("current"))Text("현재 자격 값과 이 근거의 버전이 다릅니다. 다시 확인해 저장하세요.");Text("출처: 사용자 확인 근거에 따른 약관 계산 · 공식 조회 확정값 아님")}
 }}}
}
