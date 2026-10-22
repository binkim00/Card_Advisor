package dev.cardadvisor.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.*

internal fun sourceLabel(source:String)=when(source){
 "USER_INPUT"->"사용자 확인";"USER_ESTIMATE"->"사용자 추정";"SYNTHETIC"->"가상 시연 값";else->"미확인"
}
@Composable fun MonthlyStateEditor(api:ApiClient,onBusyChanged:(Boolean)->Unit,onChanged:suspend()->Unit){
 val scope=rememberCoroutineScope { kotlinx.coroutines.Dispatchers.Main.immediate };val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
 val today=LocalDate.now(ZoneId.of("Asia/Seoul"))
 var month by remember { mutableStateOf(YearMonth.from(today).toString()) }
 var loadedMonth by remember { mutableStateOf("") }
 var actualIds by remember{mutableStateOf(emptySet<String>())};var qualificationCard by remember{mutableStateOf<String?>(null)}
 var cards by remember { mutableStateOf(JSONArray()) }
 var selected by remember { mutableStateOf<JSONObject?>(null) }
 var baseline by remember { mutableStateOf("") };var baselineKnown by remember { mutableStateOf(false) }
 var baselineSource by remember { mutableStateOf("USER_INPUT") };var baselineDate by remember { mutableStateOf("") }
 var qualified by remember { mutableStateOf<Boolean?>(null) };var qualificationSource by remember { mutableStateOf("USER_INPUT") }
 var qualificationDate by remember { mutableStateOf("") };var period by remember { mutableStateOf("") }
 var busy by remember { mutableStateOf(false) };var conflict by remember { mutableStateOf(false) }
 var notice by remember { mutableStateOf("월별 값을 불러오는 중입니다.") }
 fun adopt(c:JSONObject){
  selected=c;baseline=if(c.isNull("untrackedPerformanceWon"))"" else c.getLong("untrackedPerformanceWon").toString()
  baselineKnown=!c.isNull("untrackedPerformanceWon");qualified=if(c.isNull("currentQualified"))null else c.getBoolean("currentQualified")
  baselineSource=if(c.optString("untrackedSource")=="USER_ESTIMATE")"USER_ESTIMATE" else "USER_INPUT"
  qualificationSource=if(c.optString("currentQualificationSource")=="USER_ESTIMATE")"USER_ESTIMATE" else "USER_INPUT"
  val defaultDate=minOf(today,YearMonth.parse(c.getString("performanceMonth")).atEndOfMonth()).toString()
  baselineDate=if(c.isNull("untrackedAsOf"))defaultDate else c.getString("untrackedAsOf")
  qualificationDate=if(c.isNull("qualificationAsOf"))defaultDate else c.getString("qualificationAsOf")
  period=c.getString("qualificationPeriod");conflict=false
 }
 suspend fun load(){
  val wanted=selected?.getString("id")
  val response=JSONArray(api.request("/v1/cards/performance?month=$month"))
  val profiles=JSONArray(api.request("/v1/cards"));actualIds=(0 until profiles.length()).map{profiles.getJSONObject(it)}.filter{it.optString("source").startsWith("OFFICIAL_")}.map{it.getString("id")}.toSet()
  cards=response;loadedMonth=month
  if(response.length()==0){selected=null;notice="등록된 카드가 없습니다. 설정에서 가상 데이터를 준비해 주세요."}
  else {adopt((0 until response.length()).map{response.getJSONObject(it)}.firstOrNull{it.getString("id")==wanted}?:response.getJSONObject(0));notice="저장된 월별 값을 불러왔습니다."}
 }
 fun action(block:suspend()->Unit){
  if(busy)return
  focus.clearFocus();keyboard?.hide();busy=true;onBusyChanged(true);notice="처리 중입니다."
  scope.launch {try{block()}catch(e:CancellationException){throw e}catch(e:ApiException){
   if(e.status==409&&e.code=="VERSION_CONFLICT"){conflict=true;notice="다른 곳에서 값이 변경되었습니다. 내 입력은 유지했습니다. 최신 값을 확인해 주세요."}
   else notice=e.message?:"요청 실패"
  }catch(e:Exception){notice=e.message?:"처리하지 못했습니다."}finally{busy=false;onBusyChanged(false)} }
 }
 LaunchedEffect(Unit){action{load()}}
 qualificationCard?.let{id->ActualQualificationEditor(api,id,loadedMonth,onChanged={load();onChanged()},onClose={qualificationCard=null});return}
 Text("월별 실적·현재 자격",style=MaterialTheme.typography.titleLarge)
 Text("실적은 다음 달용이고, 현재 혜택 자격은 별도 값입니다. 직접 입력한 값은 공식 조회값으로 저장되지 않습니다.")
 if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
 if(selected==null)Text(notice,Modifier.testTag("monthNotice"))
 OutlinedTextField(month,{month=it},enabled=!busy,label={Text("실적 집계·혜택 적용 월 (YYYY-MM)")},modifier=Modifier.fillMaxWidth().testTag("stateMonth"),singleLine=true)
 val validMonth=runCatching{YearMonth.parse(month);Regex("[0-9]{4}-[0-9]{2}").matches(month)}.getOrDefault(false)
 OutlinedButton(enabled=!busy&&validMonth,onClick={action{load()}}){Text("저장된 값 다시 조회 (입력 초기화)")}
 if(!validMonth)Text("월은 YYYY-MM 형식으로 입력해 주세요.")
 if(month!=loadedMonth)Text("변경한 월을 먼저 조회해 주세요. 조회하면 작성 중인 입력이 초기화됩니다.")
 for(i in 0 until cards.length()){
  val c=cards.getJSONObject(i)
  FilterChip(selected=selected?.getString("id")==c.getString("id"),enabled=!busy&&month==loadedMonth,onClick={adopt(c);notice="선택한 카드의 저장값을 불러왔습니다."},label={Text(c.getString("name")+" 월별 수정")})
 }
 selected?.let { c ->
  val editable=!busy&&month==loadedMonth
  if(c.getString("id") in actualIds)OutlinedButton(enabled=editable,onClick={qualificationCard=c.getString("id")},modifier=Modifier.testTag("actualQualificationOpen")){Text("실제 카드 · 전월액과 신규 유예로 자격 계산")}
  Text("거래 차감 전 기준 한도는 유지하고, 연결 거래 혜택 사용액은 자동 재계산합니다.")
  Text("저장 버전 "+c.getLong("stateVersion")+" · 카드/월 조회 시 작성 중 입력이 초기화됩니다.")
  Text("저장된 실적 출처: "+sourceLabel(c.optString("untrackedSource"))+" · 확인일 "+if(c.isNull("untrackedAsOf"))"미기록" else c.getString("untrackedAsOf"))
  Text("저장된 자격 출처: "+sourceLabel(c.optString("currentQualificationSource"))+" · 확인일 "+if(c.isNull("qualificationAsOf"))"미기록" else c.getString("qualificationAsOf"))
  Text("$loadedMonth 미기록 실적 → ${YearMonth.parse(loadedMonth).plusMonths(1)} 혜택 대상")
  Text("앱에 이미 연결한 거래는 제외한 금액을 입력하세요. 입력 금액에 연결 거래 실적을 더합니다. 확인일은 출처 확인일이며 자동 집계 마감일이 아닙니다.")
  Row {FilterChip(selected=baselineKnown,enabled=editable,onClick={baselineKnown=true},label={Text("실적 금액 입력")});Spacer(Modifier.width(8.dp));FilterChip(selected=!baselineKnown,enabled=editable,onClick={baselineKnown=false},label={Text("실적 미확인")})}
  if(baselineKnown){
   OutlinedTextField(baseline,{baseline=it},enabled=editable,label={Text("미기록 실적 (원)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("stateBaseline"))
   EvidenceSource(baselineSource,editable){baselineSource=it}
   OutlinedTextField(baselineDate,{baselineDate=it},enabled=editable,label={Text("실적 확인일 (YYYY-MM-DD)")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("baselineDate"))
  }
  Text("현재 혜택 적용 월: $loadedMonth")
  OutlinedTextField(period,{period=it},enabled=editable,label={Text("현재 자격의 실적 기준 월 (YYYY-MM)")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("qualificationPeriod"))
  Text("가상 규칙은 적용 월의 직전 월을 기준으로 합니다.")
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(true to "자격 충족",false to "자격 미충족",null to "자격 미확인").forEach{(v,label)->FilterChip(selected=qualified==v,enabled=editable,onClick={qualified=v},label={Text(label)})}}
  if(qualified!=null){
   EvidenceSource(qualificationSource,editable){qualificationSource=it}
   OutlinedTextField(qualificationDate,{qualificationDate=it},enabled=editable,label={Text("자격 확인일 (YYYY-MM-DD)")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("qualificationDate"))
  }
  val from=YearMonth.parse(loadedMonth).atDay(1);val end=YearMonth.parse(loadedMonth).atEndOfMonth()
  fun validDate(value:String,start:LocalDate)=runCatching{val d=LocalDate.parse(value);!d.isBefore(start)&&!d.isAfter(end)&&!d.isAfter(today)}.getOrDefault(false)
  val valid=(!baselineKnown||(baseline.toLongOrNull()?.let{it in 0..100_000_000}==true&&validDate(baselineDate,from)))&&
   period==YearMonth.parse(loadedMonth).minusMonths(1).toString()&&(qualified==null||validDate(qualificationDate,from.minusMonths(1)))
  if(!valid)Text("금액은 0~1억 원, 기준 월은 직전 월이어야 합니다. 확인일은 해당 기준 기간부터 적용 월 말일까지이며 미래일은 허용되지 않습니다.")
  Text(notice,Modifier.testTag("monthNotice"))
  if(conflict)TextButton(enabled=!busy,onClick={action{load();onChanged()}}){Text("최신 값 불러오기 (내 입력 초기화)")}
  Button(enabled=editable&&valid&&!conflict,onClick={action{
   val body=JSONObject().put("untrackedPerformanceWon",if(baselineKnown)baseline.toLong() else JSONObject.NULL)
    .put("currentQualified",qualified?:JSONObject.NULL).put("remainingCapWon",c.opt("baseRemainingCapWon")?:JSONObject.NULL).put("version",c.getLong("stateVersion"))
    .put("untrackedSource",if(baselineKnown)baselineSource else "UNKNOWN").put("untrackedAsOf",if(baselineKnown)baselineDate else JSONObject.NULL)
    .put("qualificationSource",if(qualified!=null)qualificationSource else "UNKNOWN").put("qualificationAsOf",if(qualified!=null)qualificationDate else JSONObject.NULL).put("qualificationPeriod",period)
   api.request("/v1/cards/${c.getString("id")}/months/$loadedMonth","PUT",body)
   // Invalidate recommendations immediately after confirmed write, even if the read-back fails.
   conflict=true
   onChanged();load();notice="월별 값을 저장하고 다시 조회했습니다."
  }}){Text("월별 값 저장")}
  if(conflict)Text("최신 값을 조회하기 전에는 다시 저장할 수 없습니다.")
 }
}
@Composable private fun EvidenceSource(value:String,enabled:Boolean,onChange:(String)->Unit){
 Text("입력 출처")
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("USER_INPUT","USER_ESTIMATE").forEach{source->FilterChip(selected=value==source,enabled=enabled,onClick={onChange(source)},label={Text(sourceLabel(source))})}}
}
