package dev.cardadvisor.android
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*
import org.json.*
import java.time.*

/** No provider authentication, raw identifiers or automatic eligibility decisions. */
@Composable internal fun FinancialPerformanceEditor(api:ApiClient,transactionId:String,onBusyChanged:(Boolean)->Unit,onChanged:suspend()->Unit,onClose:()->Unit){
 val scope=rememberCoroutineScope { Dispatchers.Main.immediate };val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
 var saved by remember{mutableStateOf<JSONObject?>(null)};var contribution by remember{mutableStateOf<JSONObject?>(null)};var state by remember{mutableStateOf<JSONObject?>(null)}
 var decision by remember{mutableStateOf("UNKNOWN")};var dates by remember{mutableStateOf<Map<String,String>>(emptyMap())};var confirmed by remember{mutableStateOf(false)}
 var month by remember{mutableStateOf(YearMonth.now(ZoneId.of("Asia/Seoul")).toString())};var busy by remember{mutableStateOf(false)};var stale by remember{mutableStateOf(true)}
 var notice by remember{mutableStateOf("저장된 확인 상태를 불러옵니다.")}
 suspend fun totals(card:String){
  val c=JSONObject(api.request("/v1/cards/$card/financial-performance?month=$month"));val all=JSONArray(api.request("/v1/cards/performance?month=$month"))
  val s=(0 until all.length()).map{all.getJSONObject(it)}.first{it.getString("id")==card};contribution=c;state=s
 }
 suspend fun load(){val v=JSONObject(api.request("/v1/finance/transactions/$transactionId/performance-review"));totals(v.getString("cardId"));saved=v;decision=v.getString("decision");val refunds=v.getJSONArray("refunds");dates=(0 until refunds.length()).associate{val f=refunds.getJSONObject(it);f.getString("cancellationId") to if(f.isNull("receiptDate"))"" else f.getString("receiptDate")};confirmed=false;stale=false}
 fun action(write:Boolean=false,block:suspend()->Unit){if(busy)return;busy=true;notice="처리 중입니다.";onBusyChanged(true);focus.clearFocus();keyboard?.hide()
  scope.launch{try{block()}catch(e:CancellationException){throw e}catch(e:Exception){stale=true;notice=when{e is ApiException&&e.status==409->"거래·상품 연결 또는 확인 버전이 바뀌었습니다. 입력은 유지했어요. 최신 상태를 조회한 뒤 다시 확인해 주세요.";e is ApiException&&e.status==422->"인정 조건과 취소 접수일을 확인해 주세요. 해외·결제 방식 미확인 거래는 인정할 수 없습니다.";write->"저장 결과를 확인하지 못했습니다. 다시 보내기 전에 저장 상태를 조회해 주세요.";else->"조회하지 못했습니다. 연결을 확인하고 다시 조회해 주세요."}}finally{busy=false;onBusyChanged(false)}}
 }
 LaunchedEffect(transactionId){action{load();notice="카드 약관과 거래 내용을 확인한 뒤 선택해 주세요."}}
 Dialog(onDismissRequest={if(!busy)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,securePolicy=androidx.compose.ui.window.SecureFlagPolicy.SecureOn)){
  Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text("다음 달용 실적 확인",style=MaterialTheme.typography.headlineSmall)
   Text(notice,Modifier.testTag("performanceNotice"));if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
   OutlinedButton(enabled=!busy,onClick=onClose,modifier=Modifier.testTag("performanceBack")){Text("가계부·이용내역으로 돌아가기")}
   OutlinedTextField(month,{month=it;contribution=null;state=null},label={Text("집계 월 · YYYY-MM")},singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("performanceMonth"))
   OutlinedButton(enabled=!busy&&runCatching{YearMonth.parse(month)}.isSuccess,onClick={action{load();notice="저장 상태와 월별 계산을 다시 확인했습니다."}},modifier=Modifier.testTag("performanceRefresh")){Text("최신 저장 상태 조회 · 입력 초기화")}
   saved?.let{v->
    DesignPanel{
     Text("원구매 · "+v.getString("purchaseDate"));Text(if(v.isNull("amountWon"))"승인액 미확인" else "승인액 %,d원".format(v.getLong("amountWon")),style=MaterialTheme.typography.titleLarge)
     Text("확인 버전 "+v.getLong("version")+" · 사용자 확인에 따른 약관 추정",Modifier.testTag("performanceVersion"))
     Text("실적 인정 여부는 예산 포함 여부와 별개입니다. 할부는 승인 총액을 기준으로 확인합니다.")
     listOf("INCLUDE" to "실적 인정", "EXCLUDE" to "실적 제외", "UNKNOWN" to "미확인으로 보류").forEach{(key,label)->
      FilterChip(selected=decision==key,enabled=!busy&&!stale,onClick={decision=key;confirmed=false},label={Text(label)},modifier=Modifier.testTag("performanceDecision_"+key))
     }
     Text("KB Easy all: 세금·공과금, 아파트 관리비, 보험료·등록금, 수수료, 상품권·선불 충전 등 약관 제외 항목을 확인해 주세요. 가맹점 이름만으로 자동 인정하지 않습니다.",style=MaterialTheme.typography.bodySmall)
    }
    val refunds=v.getJSONArray("refunds")
    for(i in 0 until refunds.length()){val f=refunds.getJSONObject(i);val id=f.getString("cancellationId")
     DesignPanel{
      Text("취소 ${i+1} · %,d원".format(f.getLong("amountWon")),style=MaterialTheme.typography.titleMedium)
      Text("카드사 취소 전표 접수일을 입력하세요. 가져온 날짜나 원구매일로 대신하지 않습니다. 모르면 비워 두세요.")
      OutlinedTextField(dates[id]?:"",{dates=dates+(id to it);confirmed=false},label={Text("취소 접수일 · YYYY-MM-DD")},singleLine=true,enabled=!busy&&!stale,modifier=Modifier.fillMaxWidth().testTag("performanceRefundDate_$i"))
     }
    }
    Text("KB는 취소 접수 월의 실적에서 차감합니다. 다른 상품의 취소 귀속 규칙이 미검증이면 계산을 보류합니다.")
    Row{Checkbox(confirmed,{confirmed=it},enabled=!busy&&!stale,modifier=Modifier.testTag("performanceConfirm"));Text("약관에 따른 인정·제외와 입력한 취소 접수일을 직접 확인했습니다.",Modifier.weight(1f))}
    val datesValid=dates.values.all{it.isBlank()||runCatching{LocalDate.parse(it)}.isSuccess}
    Button(enabled=!busy&&!stale&&confirmed&&datesValid,onClick={action(true){
     val body=JSONObject().put("expectedVersion",v.getLong("version")).put("expectedTransactionVersion",v.getLong("transactionVersion")).put("expectedMappingVersion",v.getLong("mappingVersion")).put("decision",decision).put("confirmed",true)
     val array=JSONArray();for(i in 0 until refunds.length()){val id=refunds.getJSONObject(i).getString("cancellationId");array.put(JSONObject().put("cancellationId",id).put("receiptDate",dates[id]?.takeIf{it.isNotBlank()}?:JSONObject.NULL))};body.put("refunds",array)
     val result=JSONObject(api.request("/v1/finance/transactions/$transactionId/performance-review","PUT",body));load();check(saved!!.getLong("version")==result.getLong("version")&&saved!!.getString("decision")==result.getString("decision"));onChanged();notice="실적 확인을 저장하고 서버 계산을 다시 확인했습니다."
    }},modifier=Modifier.fillMaxWidth().testTag("performanceSave")){Text("실적 확인 저장")}
   }
   contribution?.let{c->DesignPanel{
    Text("$month 금융 거래 실적",style=MaterialTheme.typography.titleMedium)
    Text(if(c.isNull("amountWon"))"금융 실적 전체: 미확인" else "금융 실적 전체: %,d원".format(c.getLong("amountWon")),Modifier.testTag("performanceContribution"))
    Text("확인된 부분 합계: %,d원".format(c.getLong("confirmedSubtotalWon")))
    Text("인정 ${c.getLong("includedCount")} · 제외 ${c.getLong("excludedCount")} · 미확인 ${c.getLong("unknownCount")} · 접수일 미확인 ${c.getLong("unknownRefundDateCount")}")
    Text("부분 합계는 월 전체 실적이 아닙니다. 미확인은 0원으로 계산하지 않습니다.")
   }}
   state?.let{s->DesignPanel{
    Text("이번 혜택 자격 · 별도 기준",style=MaterialTheme.typography.titleMedium)
    Text("기준 "+s.getString("qualificationPeriod")+" · "+sourceLabel(s.getString("currentQualificationSource")))
    Text(if(s.isNull("currentQualified"))"이번 자격: 미확인" else if(s.getBoolean("currentQualified"))"이번 자격: 충족" else "이번 자격: 미충족",Modifier.testTag("performanceCurrentQualification"))
    Text("${s.getString("performanceMonth")} 소비 → ${s.getString("nextBenefitMonth")} 혜택용 실적")
    Text(if(s.isNull("performanceWon"))"다음 달용 총실적: 미확인" else "다음 달용 총실적: %,d원".format(s.getLong("performanceWon")),Modifier.testTag("performanceTotal"))
    Text("미기록 실적 입력값과 확인된 거래를 합산합니다. 미기록 실적이 미확인이면 총실적도 미확인입니다.")
    Text("이번 혜택 자격은 이 선택으로 바뀌지 않습니다. 실제 카드사 확정 실적·혜택과 다를 수 있습니다.")
   }}
  }}
 }
}
