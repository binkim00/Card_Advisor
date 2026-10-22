package dev.cardadvisor.android
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.*
import org.json.*
import java.time.*

@Composable internal fun FinancialBenefitEditor(api:ApiClient,transactionId:String,onBusyChanged:(Boolean)->Unit,onChanged:suspend()->Unit,onClose:()->Unit){
 val scope=rememberCoroutineScope{Dispatchers.Main.immediate};val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
 var showQualification by remember{mutableStateOf(false)}
 var review by remember{mutableStateOf<JSONObject?>(null)};var summary by remember{mutableStateOf<JSONObject?>(null)}
 var decision by remember{mutableStateOf("UNKNOWN")};var area by remember{mutableStateOf("")};var tier by remember{mutableIntStateOf(0)};var month by remember{mutableStateOf(YearMonth.now(ZoneId.of("Asia/Seoul")).toString())}
 var confirmed by remember{mutableStateOf(false)};var coverage by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};var stale by remember{mutableStateOf(true)};var notice by remember{mutableStateOf("저장된 혜택 확인을 불러옵니다.")}
 suspend fun load(){val v=JSONObject(api.request("/v1/finance/transactions/$transactionId/benefit-review"));val s=JSONObject(api.request("/v1/cards/${v.getString("cardId")}/financial-benefits?month=$month"));review=v;summary=s;decision=v.getString("decision");area=if(v.isNull("area"))"" else v.getString("area");tier=if(s.isNull("tier"))0 else s.getInt("tier");coverage=s.getBoolean("coverageConfirmed");confirmed=false;stale=false}
 fun action(write:Boolean=false,block:suspend()->Unit){if(busy)return;busy=true;notice="처리 중입니다.";onBusyChanged(true);focus.clearFocus();keyboard?.hide();scope.launch{try{block()}catch(e:CancellationException){throw e}catch(e:Exception){stale=true;notice=when{e is ApiException&&e.status==409->"거래나 자격 버전이 바뀌었습니다. 입력은 유지했어요. 최신 저장 상태를 다시 조회해 주세요.";e is ApiException&&e.code=="BENEFIT_QUALIFICATION_CONFLICT"->"이번 혜택 자격과 전월 실적 구간이 맞지 않습니다. 설정에서 이번 자격을 먼저 확인해 주세요.";e is ApiException&&e.code=="BENEFIT_TIER_CONFLICT"->"저장한 전월 인정액·신규 유예로 계산한 구간과 다릅니다. 자격 근거를 확인해 주세요.";e is ApiException&&e.status==422->"약관의 업종·지정 가맹점·결제 조건을 확인해 주세요. 미검증 상품·해외·할부는 자동 계산하지 않습니다.";write->"저장 결과를 확인하지 못했습니다. 재전송 전에 저장 상태를 조회해 주세요.";else->"조회하지 못했습니다. 상품 연결과 서버 연결을 확인해 주세요."}}finally{busy=false;onBusyChanged(false)}}}
 LaunchedEffect(transactionId){action{load();notice="실적 인정 여부와 혜택 대상 여부는 별도입니다. 약관을 확인해 주세요."}}
 if(showQualification&&review!=null){ActualQualificationEditor(api,review!!.getString("cardId"),month,onChanged={load();onChanged()},onClose={showQualification=false});return}
 Dialog(onDismissRequest={if(!busy)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false,securePolicy=SecureFlagPolicy.SecureOn)){
 Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("혜택 사용·잔여 한도",style=MaterialTheme.typography.headlineSmall);Text(notice,Modifier.testTag("benefitNotice"));if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  OutlinedButton(enabled=!busy,onClick=onClose,modifier=Modifier.testTag("benefitBack")){Text("거래로 돌아가기")}
  OutlinedTextField(month,{month=it;summary=null},enabled=!busy,label={Text("집계 월 · YYYY-MM")},singleLine=true,modifier=Modifier.fillMaxWidth())
  OutlinedButton(enabled=!busy&&runCatching{YearMonth.parse(month)}.isSuccess,onClick={action{load();notice="최신 혜택 확인을 불러왔습니다."}},modifier=Modifier.testTag("benefitRefresh")){Text("최신 저장 상태 조회 · 입력 초기화")}
  review?.let{v->DesignPanel{
   Text("이 거래의 혜택 조건",style=MaterialTheme.typography.titleMedium)
   Text("확인 버전 ${v.getLong("version")} · 원자료 변경 시 재확인")
   listOf("RULE" to "약관 조건에 따라 계산", "EXCLUDE" to "혜택 대상 아님", "UNKNOWN" to "미확인으로 보류").forEach{(key,label)->FilterChip(selected=decision==key,enabled=!busy&&!stale,onClick={decision=key;confirmed=false},label={Text(label)},modifier=Modifier.testTag("benefitDecision_$key"))}
   if(decision=="RULE"){
    if(v.getString("productRule").contains("TRAVELOG"))FilterChip(selected=area=="DOMESTIC_GENERAL",enabled=!busy&&!stale,onClick={area="DOMESTIC_GENERAL";confirmed=false},label={Text("국내 일반 가맹점 · 적립 제외 아님")},modifier=Modifier.testTag("benefitArea_DOMESTIC_GENERAL"))
    else {val all=v.getJSONArray("areas");for(i in 0 until all.length()){val a=all.getJSONObject(i);if(a.getString("id")!="FOREIGN_DUTYFREE")FilterChip(selected=area==a.getString("id"),enabled=!busy&&!stale,onClick={area=a.getString("id");confirmed=false},label={Text(a.getString("label"))},modifier=Modifier.testTag("benefitArea_"+a.getString("id")))}}
   }
   Text("지정 가맹점·업종·최소 금액·온라인 제외·무이자할부·상품권·충전 제외를 확인해 주세요. 가계부 카테고리나 상점 이름으로 자동 판단하지 않습니다.")
   Text("네이버플러스 스토어·무신사 일반 주문은 KB AUTO 지정 온라인 쇼핑 영역이 아닙니다. 온라인 패션 결제도 제외입니다.",style=MaterialTheme.typography.bodySmall)
   Row{Checkbox(confirmed,{confirmed=it},enabled=!busy&&!stale,modifier=Modifier.testTag("benefitConfirm"));Text("취소 후에도 혜택 조건을 충족하는지 직접 확인했습니다.",Modifier.weight(1f))}
   Button(enabled=!busy&&!stale&&confirmed&&(decision!="RULE"||area.isNotBlank()),onClick={action(true){
    val body=JSONObject().put("expectedVersion",v.getLong("version")).put("expectedTransactionVersion",v.getLong("transactionVersion")).put("expectedMappingVersion",v.getLong("mappingVersion")).put("decision",decision).put("area",area.takeIf{decision=="RULE"}?:JSONObject.NULL).put("conditionsConfirmed",true)
    val result=JSONObject(api.request("/v1/finance/transactions/$transactionId/benefit-review","PUT",body));load();check(review!!.getLong("version")==result.getLong("version"));onChanged();notice="거래의 혜택 확인을 저장하고 다시 조회했습니다."
   }},modifier=Modifier.fillMaxWidth().testTag("benefitSave")){Text("거래 혜택 확인 저장")}
  }}
  summary?.let{s->DesignPanel{
   Text("$month 전체 혜택 계산 기준",style=MaterialTheme.typography.titleMedium)
   OutlinedButton(enabled=!busy&&!stale,onClick={showQualification=true},modifier=Modifier.testTag("benefitQualification")){Text("전월 실적·신규 등록일로 자격 계산")}
   if(review?.optString("productRule")?.let{it.contains("KB_EASY_ALL")||it.contains("TRAVELGO")}==true){Text("이번 혜택에 적용되는 전월 실적 구간 · 당월 실적과 별도")
    (if(review?.optString("productRule")?.contains("TRAVELGO")==true)listOf(0 to "자격 미충족",1 to "20만~50만원 미만 · 최초 등록 우대 확인 포함",2 to "50만원 이상") else listOf(0 to "자격 미충족",1 to "50만~100만원 미만",2 to "100만~150만원 미만",3 to "150만원 이상")).forEach{(value,label)->FilterChip(selected=tier==value,enabled=!busy&&!stale,onClick={tier=value},label={Text(label)},modifier=Modifier.testTag("benefitTier_$value"))}
   }
   Row{Checkbox(coverage,{coverage=it},enabled=!busy&&!stale,modifier=Modifier.testTag("benefitCoverage"));Text("조회 기준 현재까지 이 카드의 해당 월 구매·취소 내역이 모두 기록된 것을 확인했습니다.",Modifier.weight(1f))}
   Text("누락 내역이 있거나 확인하지 않았다면 체크하지 마세요. 확인된 거래의 부분 계산과 월 전체 계산을 구분합니다.")
   Button(enabled=!busy&&!stale,onClick={action(true){val cid=review!!.getString("cardId");api.request("/v1/cards/$cid/financial-benefits?month=$month","PUT",JSONObject().put("expectedVersion",s.getLong("version")).put("expectedQualificationVersion",s.getLong("qualificationVersion")).put("tier",tier).put("coverageConfirmed",coverage));load();onChanged();notice="월별 혜택 기준을 저장하고 다시 계산했습니다."}},modifier=Modifier.fillMaxWidth().testTag("benefitMonthSave")){Text("월별 혜택 기준 저장")}
   Text(if(s.isNull("estimatedUsedWon"))"예상 혜택 사용: 미확인" else "예상 혜택 사용: %,d원".format(s.getLong("estimatedUsedWon")),Modifier.testTag("benefitUsed"))
   Text(if(s.getBoolean("unlimited"))"국내 일반 적립 월 한도 없음" else if(s.isNull("estimatedRemainingCapWon"))"예상 잔여 한도: 미확인" else "예상 잔여 한도: %,d원".format(s.getLong("estimatedRemainingCapWon")),Modifier.testTag("benefitRemaining"))
   Text("미확인 ${s.getLong("unknownCount")}건 · 혜택 제외 ${s.getLong("excludedCount")}건")
   Text("확인된 범위의 혜택 ${s.getLong("confirmedLowerWon")}~${s.getLong("confirmedUpperWon")}원 · 소수점/동률 조건이 확정되지 않으면 전체값을 보류합니다.")
   val all=s.getJSONArray("areas");for(i in 0 until all.length()){val a=all.getJSONObject(i);if(a.getLong("netWon")>0)Text(a.getString("label")+" · 순사용 %,d원 · 예상 %d~%d원".format(a.getLong("netWon"),a.getLong("lowerBenefitWon"),a.getLong("upperBenefitWon")))}
   if(review?.optString("productRule")?.contains("TRAVELGO")==true)Text("GO는 편의점·커피 그룹 5천원, 교통·디지털·생활비 그룹 5천원을 공유합니다. 영역별 월 5회이며 초과 거래의 적립 순서가 확인되지 않으면 범위로 표시합니다. 취소 접수월의 실제 환수·횟수 복원은 별도 확인이 필요합니다.")
   Text("사용자 확인에 따른 약관 추정입니다. 취소는 원구매 월의 예상치를 재계산하며 카드사 실제 적립·환수 시점과 즉시 복원된 한도를 뜻하지 않습니다. 공식 잔여 한도는 별도 미확인입니다.",style=MaterialTheme.typography.bodySmall)
  }}
 }} }
}
