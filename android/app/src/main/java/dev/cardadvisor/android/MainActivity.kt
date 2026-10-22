package dev.cardadvisor.android
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.*
import java.time.*
import java.util.UUID
class MainActivity:ComponentActivity(){
 private var checkoutRequest by mutableStateOf(0L)
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);if(intent.getBooleanExtra("open_checkout",false))checkoutRequest++;setContent{AdvisorTheme{Surface(Modifier.fillMaxSize()){AdvisorApp(checkoutRequest=checkoutRequest)}}}}
 override fun onNewIntent(intent:android.content.Intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("open_checkout",false))checkoutRequest++}
}
private fun won(n:Long)="%,d원".format(n)
private fun amount(o:JSONObject,key:String)=if(o.isNull(key))"미확인" else won(o.getLong(key))
@Composable private fun PerformanceSummary(c:JSONObject){
 if(c.optString("benefitRuleVersion").startsWith("OFFICIAL_")){ActualCardSummary(c);return}
 Text(c.getString("name")+" · 이번 혜택 자격 "+if(c.isNull("currentQualified"))"미확인" else if(c.getBoolean("currentQualified"))"충족" else "미충족")
 Text("현재 자격 기준: "+c.getString("qualificationPeriod")+" · "+sourceLabel(c.optString("currentQualificationSource")))
 Text(c.getString("name")+" · 다음 달용 실적 "+amount(c,"performanceWon"))
 Text("기존 미기록 실적 "+amount(c,"untrackedPerformanceWon")+" + 연결 거래 "+amount(c,"ledgerPerformanceWon"))
 Text("실적 집계 "+c.getString("performanceMonth")+" → 혜택 대상 "+c.getString("nextBenefitMonth"))
 Text("목표 부족액 "+amount(c,"gapWon")+" · 가상 규칙 추정")
 Text(c.getString("name")+" · 연결 거래 혜택 사용액 "+amount(c,"ledgerBenefitUsedWon"))
 Text(c.getString("name")+" · 혜택 잔여 한도 "+amount(c,"remainingCapWon"))
 Text("거래 차감 전 기준 한도 "+amount(c,"baseRemainingCapWon")+" · 가상 추정")
 Text("카드 환불액을 원구매 월에 반영해 월 전체 혜택을 재계산합니다. 실제 카드사 확정 혜택이 아닙니다.")
 Text("이번 달 실적 증가로 이번 혜택 자격이 바뀌지는 않습니다.")
}
@Composable fun AdvisorApp(clientOverride:ApiClient?=null,checkoutRequest:Long=0L){
 val appContext=androidx.compose.ui.platform.LocalContext.current
 val detectedCheckout by CheckoutDetection.pending.collectAsState()
 val api=remember(clientOverride){clientOverride?:ApiClient.persistent(appContext)};val scope=rememberCoroutineScope { kotlinx.coroutines.Dispatchers.Main.immediate }
 val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
 var email by remember{mutableStateOf("")};var password by remember{mutableStateOf("")}
 var passwordRecovery by remember{mutableStateOf(false)}
 var authError by remember{mutableStateOf<String?>(null)}
 var loggedIn by remember{mutableStateOf(false)}
 var restoringSession by remember{mutableStateOf(true)}
 val sessionActive by api.sessionActive.collectAsState()
 val offline by api.offline.collectAsState();val lastUpdated by api.lastUpdated.collectAsState()
 val busyState=remember{mutableStateOf(false)};var busy by busyState
 var message by remember{mutableStateOf("로컬 개발용 · 가상 카드 규칙으로 계산합니다.")}
 var order by remember{mutableStateOf("50000")};var points by remember{mutableStateOf("0")};var money by remember{mutableStateOf("0")}
 var tolerance by remember{mutableStateOf("0")};var mode by remember{mutableStateOf("BENEFIT")}
 var recommendationId by remember{mutableStateOf<String?>(null)};var explanation by remember{mutableStateOf<JSONObject?>(null)}
 var result by remember{mutableStateOf<JSONObject?>(null)};var generation by remember{mutableLongStateOf(0L)}
 var expiresAt by remember{mutableLongStateOf(0L)};var ledger by remember{mutableStateOf(JSONArray())}
 var budgetEditingMonth by remember{mutableStateOf<String?>(null)}
 var mappingOrganization by remember{mutableStateOf<String?>(null)}
 var tab by remember{mutableIntStateOf(7)}
 var benefitTransaction by remember{mutableStateOf<String?>(null)}
 var performanceTransaction by remember{mutableStateOf<String?>(null)}
 var editEntry by remember{mutableStateOf<JSONObject?>(null)}
 var budgetSummary by remember{mutableStateOf<JSONObject?>(null)}
 var cancelEntry by remember{mutableStateOf<JSONObject?>(null)}
 var cancelAmount by remember{mutableStateOf("")}
 var cardRefundAmount by remember{mutableStateOf("")}
 var cardStates by remember{mutableStateOf(JSONArray())}
 var actualMode by remember{mutableStateOf(false)}
 var paymentMethod by remember{mutableStateOf("NAVER_PAY_CARD")}
 var checkoutCategory by remember{mutableStateOf("SHOPPING")};var installmentMonths by remember{mutableStateOf("1")};var splitApproval by remember{mutableStateOf("NO")}
 var splitCategories by remember{mutableStateOf(false)};val checkoutAllocations=remember{mutableStateMapOf<String,String>()}
 var confirmedDetectionGeneration by remember{mutableStateOf<Long?>(null)}
 var merchant by remember{mutableStateOf("NAVER_PLUS_STORE")};var ordinaryGoods by remember{mutableStateOf(true)};var factsConfirmed by remember{mutableStateOf(false)}
 var cancelReceivedDate by remember{mutableStateOf(LocalDate.now(ZoneId.of("Asia/Seoul")).toString())}
 fun purchaseFacts()=JSONObject().put("merchant",merchant).put("purchaseKind",if(ordinaryGoods)"GENERAL_GOODS" else "GIFT_CARD").put("confirmed",factsConfirmed)
 var checkoutExpanded by remember{mutableStateOf(false)}
 var manualExpanded by remember{mutableStateOf(false)}
 var manualFactsConfirmed by remember{mutableStateOf(false)}
 var statsExpanded by remember{mutableStateOf(false)}
 var detailId by remember{mutableStateOf<String?>(null)}
 var ledgerFilter by remember{mutableStateOf("ALL")}
 var selectedCardId by remember{mutableStateOf<String?>(null)}
 val month=YearMonth.now(ZoneId.of("Asia/Seoul")).toString()
 suspend fun refreshPerformance(){cardStates=JSONArray(api.request("/v1/cards/performance?month="+month))}
 suspend fun refreshLedger(){
  val snapshot=api.dashboard(month)
  ledger=snapshot.getJSONArray("ledger");cardStates=snapshot.getJSONArray("performance");budgetSummary=if(snapshot.isNull("budget"))null else snapshot.getJSONObject("budget")
 }
 fun invalidate(){generation++;result=null;recommendationId=null;explanation=null;expiresAt=0}
 fun runTask(task:suspend()->Unit){if(busy)return;focus.clearFocus();keyboard?.hide();busy=true;Diagnostics.event(DiagnosticEvent.APP_TASK_ENQUEUED);scope.launch{Diagnostics.event(DiagnosticEvent.APP_TASK_BEGIN);try{task()}catch(e:NumberFormatException){message="금액은 숫자로 입력해 주세요."}catch(e:Exception){message=e.message?:"처리하지 못했습니다."}finally{busy=false;Diagnostics.event(DiagnosticEvent.APP_TASK_END)}}}
 fun compareCards(){runTask{
      if(actualMode&&!factsConfirmed){message="결제 금액과 조건을 먼저 확인해 주세요.";return@runTask}
      val source=confirmedDetectionGeneration
      if(source!=null&&CheckoutDetection.current()?.generation!=source){invalidate();factsConfirmed=false;message="주문서가 바뀌거나 만료됐습니다. 다시 확인해 주세요.";return@runTask}
      val requestedGeneration=generation
      val sum=order.toLong();val p=points.toLong();val m=money.toLong()
      val context=JSONObject().put("orderValueWon",sum).put("pointUsedWon",p).put("prepaidMoneyUsedWon",m).put("cardChargeWon",sum-p-m).put("category",checkoutCategory).put("paymentMethod",paymentMethod).put("installments",installmentMonths.toInt()).put("splitApproval",splitApproval).put("confirmedByUser",true)
      if(splitCategories){val allocations=JSONObject();checkoutAllocations.forEach{(k,v)->if(v.isNotBlank())allocations.put(k,v.toLong())};context.put("allocationsWon",allocations)}
      if(actualMode)context.put("purchaseFacts",purchaseFacts())
      val c=JSONObject(api.request("/v1/checkout-sessions","POST",context))
      val response=JSONObject(api.request("/v1/recommendations","POST",JSONObject().put("checkoutId",c.getString("id")).put("snapshotVersion",c.getLong("snapshotVersion")).put("mode",mode).put("maxOpportunityCostWon",tolerance.toLong())))
      if(generation==requestedGeneration&&(source==null||CheckoutDetection.current()?.generation==source)){recommendationId=response.getString("id");result=response.getJSONObject("result");checkoutExpanded=false;expiresAt=Instant.parse(response.getString("expiresAt")).toEpochMilli();message="입력한 금액 기준의 예상 결과입니다. 결제는 쇼핑몰에서 직접 진행하세요."}
 }}
 fun authenticate(register:Boolean){
  focus.clearFocus();keyboard?.hide();authError=null
  if(email.length>254||!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email)){authError="이메일 형식을 확인해 주세요.";return}
  if(password.length !in 12..128){authError="비밀번호는 12~128자로 입력해 주세요.";return}
  runTask{
   try{
    val response=JSONObject(api.request(if(register)"/v1/auth/register" else "/v1/auth/login","POST",JSONObject().put("email",email).put("password",password)))
    api.setSession(response);loggedIn=true;password=""
    message=if(register)"가입했습니다. 가상 데이터로 계산을 시작할 수 있습니다." else "로그인했습니다."
   }catch(e:kotlinx.coroutines.CancellationException){throw e}
   catch(e:Exception){authError=when{e is ApiException&&e.status==409->"이미 등록된 이메일일 수 있습니다. 로그인으로 확인해 주세요.";e is ApiException&&e.status==401->"이메일 또는 비밀번호를 확인해 주세요.";else->e.message?:"인증을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."};throw e}
  }
 }
 LaunchedEffect(Unit){try{loggedIn=api.restoreSession();if(loggedIn)message="저장된 로그인으로 다시 연결했습니다."}finally{restoringSession=false}}
 LaunchedEffect(offline){if(offline){invalidate();factsConfirmed=false;manualFactsConfirmed=false;CheckoutDetection.clear()}}
 LaunchedEffect(sessionActive){if(!sessionActive&&loggedIn&&api.token==null){busy=false;loggedIn=false;invalidate();ledger=JSONArray();budgetSummary=null;cardStates=JSONArray();message="로그인이 만료되었습니다. 다시 로그인해 주세요."}}
 val appLifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
 DisposableEffect(appLifecycle){val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP){invalidate();factsConfirmed=false}};appLifecycle.addObserver(observer);onDispose{appLifecycle.removeObserver(observer)}}
 LaunchedEffect(loggedIn,restoringSession){
  if(clientOverride!=null){CheckoutDetection.authorize(false);return@LaunchedEffect}
  val preferences=appContext.getSharedPreferences("advisor_detection",0)
  if(!loggedIn){if(!restoringSession){CheckoutDetection.authorize(false);preferences.edit().putBoolean("enabled",false).putBoolean("consent",false).apply()}}
  else try{
   val settings=JSONObject(api.request("/v1/me/preferences"));val consents=settings.getJSONArray("consents")
   val allowed=(0 until consents.length()).any{val c=consents.getJSONObject(it);c.getString("purpose")=="CHECKOUT_DETECTION"&&c.getBoolean("accepted")&&c.getString("policy_version")==settings.getString("policyVersion")}
   val edit=preferences.edit().putBoolean("consent",allowed);if(!allowed)edit.putBoolean("enabled",false);edit.apply();CheckoutDetection.authorize(true)
  }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){preferences.edit().putBoolean("enabled",false).putBoolean("consent",false).apply();CheckoutDetection.authorize(false)}
 }
 LaunchedEffect(detectedCheckout?.generation){
  val source=confirmedDetectionGeneration
  if(source!=null&&detectedCheckout?.generation!=source){invalidate();factsConfirmed=false;confirmedDetectionGeneration=null;message="주문서가 바뀌거나 만료됐습니다. 주문서 금액을 다시 확인해 주세요."}
  else if(detectedCheckout!=null&&source==null){invalidate();factsConfirmed=false}
 }
 LaunchedEffect(loggedIn){if(loggedIn)while(true){CheckoutDetection.current();delay(500)}}
 LaunchedEffect(expiresAt){if(expiresAt>0){while(System.currentTimeMillis()<expiresAt){delay(500)};result=null;message="추천이 만료되었습니다. 주문 금액을 다시 확인해 주세요."}}
 LaunchedEffect(checkoutRequest,loggedIn){if(checkoutRequest>0&&loggedIn)tab=11}
 LaunchedEffect(loggedIn,tab){if(loggedIn&&tab in listOf(7,2))runTask{invalidate();refreshLedger()}}
 AdvisorShell(loggedIn,tab,busyState,message,onNavigate={if(tab==1&&it!=1)invalidate();tab=it;manualExpanded=false;statsExpanded=false;detailId=null},onLogout={runTask{var logoutConfirmed=true;try{api.request("/v1/auth/logout","POST")}catch(e:Exception){logoutConfirmed=e is ApiException&&e.status==401};api.clearSession();loggedIn=false;invalidate();ledger=JSONArray();budgetSummary=null;editEntry=null;cardStates=JSONArray();selectedCardId=null;manualExpanded=false;statsExpanded=false;detailId=null;tab=7;message=if(logoutConfirmed)"로그아웃했습니다." else "기기에서 로그아웃했습니다. 서버 세션 종료는 확인되지 않았습니다."}}){
  if(restoringSession){LinearProgressIndicator(Modifier.fillMaxWidth());Text("저장된 로그인을 확인하고 있습니다.")}else if(!loggedIn){
   DesignPanel {
   Text("소비 전에, 더 나은 선택",style=MaterialTheme.typography.titleLarge)
   Text("예산과 실적을 확인하고 결제할 카드를 비교해 보세요.")
   Text("내 계정으로 시작하기",style=MaterialTheme.typography.titleMedium)
   OutlinedTextField(email,{email=it;authError=null},label={Text("이메일")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("email"))
   OutlinedTextField(password,{password=it;authError=null},label={Text("비밀번호 · 12~128자")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth().testTag("password"))
   Text("앱 계정용 비밀번호는 12~128자입니다. 카드사 로그인 정보는 금융 연결 화면에서 별도로 입력합니다.",style=MaterialTheme.typography.bodySmall)
   authError?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.fillMaxWidth().testTag("authError").semantics{liveRegion=LiveRegionMode.Polite})}
   if(busy)Text("가입·로그인 정보를 확인하고 있습니다.",Modifier.testTag("authPending"))
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    Button(enabled=!busy,onClick={authenticate(true)}){Text("가입")}
    OutlinedButton(enabled=!busy,onClick={authenticate(false)}){Text("로그인")}
    TextButton(enabled=!busy,onClick={passwordRecovery=true},modifier=Modifier.testTag("passwordRecoveryOpen")){Text("비밀번호 찾기")}
   }
   }
  }else{
   if(offline){DesignPanel{Text("오프라인 · 저장된 최근 내역",modifier=Modifier.testTag("offlineBanner"));Text("저장·새 추천은 연결 후 가능합니다. 마지막 갱신: "+(lastUpdated?.let{Instant.ofEpochMilli(it).atZone(ZoneId.of("Asia/Seoul")).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))}?:"없음"));OutlinedButton(enabled=!busy,onClick={runTask{refreshLedger();message=if(api.offline.value)"아직 서버에 연결되지 않았습니다." else "최신 내역으로 갱신했습니다."}}){Text("연결 다시 확인")}}}
   when(tab){
    7->{
     if(detectedCheckout!=null)OutlinedButton(enabled=!busy,onClick={tab=11}){Text("감지한 주문서 금액 확인")}
     HomeBudget(budgetSummary,busy,onBudget={budgetEditingMonth=null;tab=4},onRefresh={runTask{invalidate();refreshLedger()}})
     CompareCallout(!busy){tab=1}
     DashboardCards(cardStates,onEdit={tab=3},enabled=!busy)
     ServiceMenu("내 실제 카드 등록","보유 상품을 선택하고 금융 카드와 연결해요",!busy){tab=9}
     Text("소비를 더 편하게",style=MaterialTheme.typography.titleLarge)
     ServiceMenu("쇼핑 주문서 감지","결제 정보를 확인하고 카드 비교로 이어가요",!busy){tab=11}
     ServiceMenu("소비 분석·예산 제안","지출을 돌아보고 다음 소비를 준비해요",!busy){tab=10}
     if(cardStates.length()==0)ExpandablePanel("가상 데이터로 둘러보기"){
      Text("실제 카드와 구분된 테스트 데이터로 기능을 확인합니다.")
      Button(enabled=!busy,onClick={runTask{api.request("/v1/demo/setup","POST");refreshLedger();invalidate();message="가상 카드 2개와 예산이 준비되었습니다. 기존 예산은 유지합니다."}}){Text("가상 데이터 준비")}
     }
    }
    11->DetectionEditor(api,onBusyChanged={busy=it},onConfirmed={o,p,m,shop,method,source->order=o;points=p;money=m;merchant=shop;paymentMethod=method;actualMode=true;ordinaryGoods=true;checkoutCategory="SHOPPING";installmentMonths="1";splitApproval="NO";splitCategories=false;checkoutAllocations.clear();factsConfirmed=true;confirmedDetectionGeneration=source;selectedCardId=null;invalidate();tab=1;compareCards()},onManual={confirmedDetectionGeneration=null;actualMode=true;factsConfirmed=false;invalidate();tab=1})
    10->InsightsEditor(api,onBusyChanged={busy=it},onChanged={invalidate();refreshLedger()})
    9->CardProductsEditor(api,onBusyChanged={busy=it},onChanged={actualMode=true;selectedCardId=null;invalidate();refreshPerformance()},onEditMonth={tab=3},onOpenFinance={mappingOrganization=it;tab=6})
    8->AccountSettingsEditor(api,onBusyChanged={busy=it},onInvalidated={invalidate()},onDeleted={loggedIn=false;invalidate();ledger=JSONArray();budgetSummary=null;cardStates=JSONArray();tab=7;message="계정을 삭제했습니다."})
    6->FinanceEditor(api,onBusyChanged={busy=it},onInvalidated={invalidate();budgetSummary=null;ledger=JSONArray()},onChanged={refreshLedger()},initialMappingOrganization=mappingOrganization,onMappingOpened={mappingOrganization=null})
    5->FixedCostEditor(api,onBusyChanged={busy=it},onInvalidated={invalidate();budgetSummary=null},onChanged={refreshLedger()},onOpenBudget={budgetEditingMonth=it;tab=4})
    4->BudgetEditor(api,onBusyChanged={busy=it},onInvalidated={invalidate();budgetSummary=null},onChanged={refreshLedger()},initialMonth=budgetEditingMonth)
    3->MonthlyStateEditor(api,onBusyChanged={busy=it},onChanged={invalidate();refreshPerformance()})
    0->{
     Text("카드와 금융",style=MaterialTheme.typography.titleMedium)
     ServiceMenu("내 실제 카드 등록","보유 카드 상품 관리",!busy){tab=9}
     ServiceMenu("금융 연결 관리","카드사 연결 · 이용내역 가져오기",!busy){tab=6}
     ServiceMenu("월별 실적·자격 수정","이번 혜택 자격과 다음 달 실적",!busy){tab=3}
     Text("소비 관리",style=MaterialTheme.typography.titleMedium)
     ServiceMenu("월별 예산 수정","전체 예산 · 카테고리별 한도",!busy){budgetEditingMonth=null;tab=4}
     ServiceMenu("반복 고정비 관리","정기 지출 일정과 거래 연결",!busy){tab=5}
     ServiceMenu("소비 분석·예산 제안","소비 패턴을 확인해요",!busy){tab=10}
     ServiceMenu("쇼핑 주문서 감지","주문서 확인 및 권한 설정",!busy){tab=11}
     Text("앱 설정",style=MaterialTheme.typography.titleMedium)
     ServiceMenu("동의·계정 관리","선택 기능 동의 · 계정 삭제",!busy){tab=8}
     ExpandablePanel("가상 데이터로 기능 확인"){
      Text("실제 카드와 구분된 A·B 카드로 기능을 확인합니다.")
      Button(enabled=!busy,onClick={runTask{api.request("/v1/demo/setup","POST");refreshPerformance();invalidate();message="가상 카드 2개와 예산이 준비되었습니다. 기존 예산은 유지합니다."}}){Text("가상 데이터 준비")}
     }
    }
    1->{
     Row{FilterChip(selected=actualMode,onClick={actualMode=true;selectedCardId=null;invalidate()},label={Text("내 실제 카드")});FilterChip(selected=!actualMode,onClick={actualMode=false;selectedCardId=null;invalidate()},label={Text("가상 시연")})}
     if(actualMode)DesignPanel{
      Text("약관 기반 예상 · 할부·분할 승인은 조건 확인 전 추천 보류")
      Row{FilterChip(selected=merchant=="NAVER_PLUS_STORE",onClick={merchant="NAVER_PLUS_STORE";factsConfirmed=false;invalidate()},label={Text("네이버플러스 스토어")});FilterChip(selected=merchant=="MUSINSA",onClick={merchant="MUSINSA";factsConfirmed=false;invalidate()},label={Text("무신사")})}
      Row{Checkbox(ordinaryGoods,{ordinaryGoods=it;factsConfirmed=false;invalidate()});Text("일반 상품 (해제 시 상품권·충전 등 혜택 제외)")}
      Row{Checkbox(factsConfirmed,{factsConfirmed=it;invalidate()},modifier=Modifier.testTag("actualFactsConfirmed"));Text("금액·국내 원화·구매 종류와 입력한 결제 조건을 확인했습니다.")}
      Text("실제 매입 업종·행사·취소 정산 결과에 따라 달라질 수 있습니다.",style=MaterialTheme.typography.bodySmall)
     }
     CheckoutOverview(order,points,money,busy,paymentLabel=when(paymentMethod){"DIRECT_CARD"->"일반 카드";"NAVER_PAY_CARD"->"네이버페이 카드";else->"기타 결제수단"},categoryText=if(splitCategories)"여러 분류" else categoryLabel(checkoutCategory)){checkoutExpanded=!checkoutExpanded}
     if(checkoutExpanded) DesignPanel {
      PaymentFields(order,points,money,{order=it;factsConfirmed=false;confirmedDetectionGeneration=null;invalidate()},{points=it;factsConfirmed=false;confirmedDetectionGeneration=null;invalidate()},{money=it;factsConfirmed=false;confirmedDetectionGeneration=null;invalidate()})
      CheckoutConditions(checkoutCategory,paymentMethod,installmentMonths,splitApproval,splitCategories,checkoutAllocations,!busy,
       onCategory={checkoutCategory=it;factsConfirmed=false;confirmedDetectionGeneration=null;invalidate()},
       onPayment={paymentMethod=it;factsConfirmed=false;confirmedDetectionGeneration=null;invalidate()},
       onInstallments={installmentMonths=it;factsConfirmed=false;confirmedDetectionGeneration=null;invalidate()},
       onSplit={splitApproval=it;factsConfirmed=false;confirmedDetectionGeneration=null;invalidate()},
       onAllocationToggle={splitCategories=it;factsConfirmed=false;confirmedDetectionGeneration=null;invalidate()},
       onAllocation={k,v->checkoutAllocations[k]=v;factsConfirmed=false;confirmedDetectionGeneration=null;invalidate()})
      TextButton(onClick={checkoutExpanded=false}){Text("입력 접기")}
     }
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){listOf("BENEFIT" to "이번 혜택","PERFORMANCE" to "실적","BALANCED" to "균형").forEach{(value,label)->FilterChip(selected=mode==value,onClick={mode=value;invalidate()},label={Text((if(mode==value)"✓ " else "")+label)})}}
     if(mode=="BALANCED")OutlinedTextField(tolerance,{tolerance=it;invalidate()},label={Text("허용할 이번 혜택 차이 · 기본 0원")},modifier=Modifier.fillMaxWidth())
     result?.let{r-> androidx.compose.foundation.layout.Column(Modifier.testTag("recommendationResult")){RecommendationResults(r)} }
     recommendationId?.let{id->OutlinedButton(enabled=!busy,onClick={runTask{val response=JSONObject(api.request("/v1/recommendations/$id/explanation","POST"));if(id==recommendationId)explanation=response}},modifier=Modifier.testTag("recommendationExplain")){Text("추천 이유 확인")}}
     explanation?.let{DesignPanel{AssistanceView(it)}}
     if(result==null) EmptyRecommendation()
     Button(enabled=!busy,shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("compareCards"),onClick={compareCards()}){Text("금액 확인 후 카드 비교")}
     Text(if(actualMode)"약관 기반 예상 · 실제 결제는 쇼핑몰에서 진행" else "가상 규칙으로 계산 · 실제 결제는 쇼핑몰에서 진행",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    2->{
     LedgerOverview(budgetSummary,month)
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
      TextButton(enabled=!busy,onClick={manualExpanded=!manualExpanded;manualFactsConfirmed=false}){Text(if(manualExpanded)"수동 입력 접기" else "+ 수동 구매 기록")}
      TextButton(enabled=!busy,onClick={runTask{invalidate();refreshLedger();message="내역을 조회했습니다."}}){Text("새로고침")}
     }
     if(manualExpanded) DesignPanel {
     Text("수동 구매 기록",style=MaterialTheme.typography.titleLarge)
     PaymentFields(order,points,money,{order=it;factsConfirmed=false;manualFactsConfirmed=false;confirmedDetectionGeneration=null;invalidate()},{points=it;factsConfirmed=false;manualFactsConfirmed=false;confirmedDetectionGeneration=null;invalidate()},{money=it;factsConfirmed=false;manualFactsConfirmed=false;confirmedDetectionGeneration=null;invalidate()})
     Text("실제로 사용한 카드를 선택해 주세요. 추천 조회는 실적에 반영되지 않습니다.")
     for(i in 0 until cardStates.length()){val c=cardStates.getJSONObject(i)
      if(c.optString("benefitRuleVersion").startsWith("OFFICIAL_")!=actualMode)continue
      FilterChip(selected=selectedCardId==c.getString("id"),onClick={selectedCardId=c.getString("id");manualFactsConfirmed=false},enabled=!busy,label={Text(c.getString("name")+" 사용")})

     }
     if(actualMode){
      Text("수동 기록 조건 · "+(if(merchant=="NAVER_PLUS_STORE")"네이버플러스 스토어" else "무신사")+" · "+(if(ordinaryGoods)"일반 상품" else "상품권·충전 등 제외 상품"))
      Row{Checkbox(manualFactsConfirmed,{manualFactsConfirmed=it},modifier=Modifier.testTag("manualFactsConfirmed"));Text("입력한 금액과 위 구매 조건·국내 원화·일시불·단일 카드 승인을 직접 확인했습니다.")}
     }
     Text("포인트를 제외한 지출을 기록합니다. 실제 결제는 실행하지 않습니다.")
     Row{
      Button(enabled=!busy&&selectedCardId!=null&&(!actualMode||manualFactsConfirmed),onClick={runTask{api.request("/v1/transactions/manual","POST",JSONObject().put("clientId",UUID.randomUUID().toString()).put("amountWon",order.toLong()-points.toLong()).put("cardChargeWon",order.toLong()-points.toLong()-money.toLong()).put("cardId",selectedCardId).put("purchasedAt",LocalDate.now(ZoneId.of("Asia/Seoul")).toString()).put("category","SHOPPING").put("included",true).apply{if(actualMode)put("purchaseFacts",purchaseFacts().put("confirmed",manualFactsConfirmed))});invalidate();refreshLedger();manualExpanded=false;message="수동 구매 기록을 추가했습니다."}}){Text("구매 기록 추가")}
      TextButton(enabled=!busy,onClick={manualExpanded=false}){Text("입력 닫기")}
     }
     }
     LedgerFilters(ledgerFilter){ledgerFilter=it;detailId=null}
     if(ledger.length()==0) Text("아직 기록한 거래가 없어요. 수동 구매를 추가해 보세요.",color=MaterialTheme.colorScheme.onSurfaceVariant)
     for(i in 0 until ledger.length()){val item=ledger.getJSONObject(i)
     if(ledgerFilter=="ALL"||item.getString("category")==ledgerFilter){
     LedgerRow(item,detailId==item.getString("id"),!busy){detailId=if(detailId==item.getString("id"))null else item.getString("id")}
     if(detailId==item.getString("id")) DesignPanel {
      Text(item.getString("purchased_at"))
      Text("분류: "+categoryLabel(item.getString("category"))+" · "+if(item.getBoolean("included"))"예산 포함" else "예산 제외")
      OutlinedButton(enabled=!busy,onClick={editEntry=item}){Text("분류·예산 포함 수정")}
      if(!item.isNull("financial_transaction_id"))OutlinedButton(enabled=!busy,onClick={performanceTransaction=item.getString("financial_transaction_id")},modifier=Modifier.testTag("reviewPerformance_"+item.getString("id"))){Text("다음 달용 실적 인정·취소 확인")}
      if(!item.isNull("financial_transaction_id"))OutlinedButton(enabled=!busy,onClick={benefitTransaction=item.getString("financial_transaction_id")},modifier=Modifier.testTag("reviewBenefit_"+item.getString("id"))){Text("혜택 사용·잔여 한도 확인")}
      Text("원래 금액 "+won(item.getLong("amount_won")))
      Text("취소 반영 "+won(item.getLong("amount_won")-item.getLong("net_won")))
      Text("남은 지출 "+won(item.getLong("net_won")),style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
      Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
       for(j in 0 until cardStates.length()){val card=cardStates.getJSONObject(j);FilterChip(selected=selectedCardId==card.getString("id"),enabled=!busy,onClick={selectedCardId=card.getString("id")},label={Text(card.getString("name"))})}
      }
      Text("계산 연결 카드: "+if(item.isNull("card_name"))"미연결 · 실적 미반영" else item.getString("card_name"))
      if(!item.isNull("financial_status"))Text("표시된 금융 카드 상품과 계산 연결은 별개입니다. 실제 금융 거래의 실적·혜택은 검수 전 미확인입니다.",style=MaterialTheme.typography.bodySmall)
      Text("남은 카드 승인액 "+amount(item,"card_net_won"))
      var linkAmount by remember(item.getString("id"),item.opt("card_charge_won")){mutableStateOf(if(item.isNull("card_charge_won"))"" else item.getLong("card_charge_won").toString())}
      OutlinedTextField(linkAmount,{linkAmount=it},label={Text("거래 카드 승인액")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
      TextButton(enabled=!busy&&selectedCardId!=null&&linkAmount.toLongOrNull()!=null,onClick={runTask{
       api.request("/v1/transactions/"+item.getString("id")+"/card","PUT",JSONObject().put("cardId",selectedCardId).put("cardChargeWon",linkAmount.toLong()))
       invalidate();refreshLedger();message="선택한 카드로 거래 연결을 변경했습니다."
      }}){Text("선택한 카드로 거래 연결")}
      if(item.getLong("net_won")>0)OutlinedButton(enabled=!busy,onClick={cancelEntry=item;cancelAmount="";cardRefundAmount=""}){Text("부분 취소 반영")}
      if(item.getLong("net_won")>0)TextButton(enabled=!busy,onClick={if(!item.isNull("purchase_facts")){cancelEntry=item;cancelAmount=item.getLong("net_won").toString();cardRefundAmount=item.getLong("card_net_won").toString()}else runTask{api.request("/v1/transactions/"+item.getString("id")+"/cancellations","POST",JSONObject().put("clientId",UUID.randomUUID().toString()).put("amountWon",item.getLong("net_won")).put("cardRefundWon",item.opt("card_net_won")));invalidate();refreshLedger();message="테스트 기록의 취소를 반영했습니다."}}){Text("이 기록 전액 취소 반영")}
     }}}
     ExpandablePanel("예산·실적 상세",statsExpanded,{statsExpanded=it}){
      budgetSummary?.let{BudgetSummaryView(it)}
      for(i in 0 until cardStates.length()){PerformanceSummary(cardStates.getJSONObject(i));HorizontalDivider()}
     }
    }
   }
  }
 }
 if(passwordRecovery)AccountRecoveryEditor(api,false,onBusyChanged={busy=it},onClose={passwordRecovery=false})
 benefitTransaction?.let{id->FinancialBenefitEditor(api,id,onBusyChanged={busy=it},onChanged={invalidate();refreshLedger()},onClose={benefitTransaction=null})}
 performanceTransaction?.let{id->FinancialPerformanceEditor(api,id,onBusyChanged={busy=it},onChanged={invalidate();refreshLedger()},onClose={performanceTransaction=null})}
 editEntry?.let{item->ClassificationEditor(api,item,onBusyChanged={busy=it},onCommitted={invalidate()},onRefresh={refreshLedger()},onSaved={editEntry=null;message="분류와 예산 포함 여부를 저장했습니다."},onClose={editEntry=null})}
 cancelEntry?.let{item->
  val remaining=item.getLong("net_won");val amount=cancelAmount.toLongOrNull()
  val mixed=!item.isNull("card_id")&&item.getLong("card_charge_won")!=item.getLong("amount_won")
  val refund=if(mixed)cardRefundAmount.toLongOrNull() else amount
  val cardRemaining=if(item.isNull("card_net_won"))null else item.getLong("card_net_won")
  val dateValid=item.isNull("purchase_facts")||runCatching{val d=LocalDate.parse(cancelReceivedDate);!d.isBefore(LocalDate.parse(item.getString("purchased_at")))&&!d.isAfter(LocalDate.now(ZoneId.of("Asia/Seoul")))}.getOrDefault(false)
  val valid=dateValid&&amount!=null&&amount>0&&amount<=remaining&&(!mixed||(refund!=null&&cardRemaining!=null&&refund>=0&&refund<=amount&&refund<=cardRemaining&&amount-refund<=remaining-cardRemaining))
  AlertDialog(onDismissRequest={if(!busy)cancelEntry=null},title={Text("부분 취소 기록")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
   Text("취소 가능한 금액 "+won(remaining))
   if(!item.isNull("purchase_facts"))OutlinedTextField(cancelReceivedDate,{cancelReceivedDate=it.take(10)},label={Text("취소 접수일 · 사용자 확인 YYYY-MM-DD")},singleLine=true,modifier=Modifier.testTag("cancelReceivedDate"))
   if(!dateValid)Text("접수일은 구매일부터 오늘 사이의 YYYY-MM-DD로 입력해 주세요.",color=MaterialTheme.colorScheme.error)
   Text("실제 금융기관 취소가 아니라 가계부 기록에 반영합니다.")
   OutlinedTextField(cancelAmount,{cancelAmount=it},label={Text("취소할 금액")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),isError=cancelAmount.isNotEmpty()&&!valid,modifier=Modifier.testTag("cancelAmount"))
   if(mixed)OutlinedTextField(cardRefundAmount,{cardRefundAmount=it},label={Text("이 중 카드로 환불된 금액")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
   if(cancelAmount.isNotEmpty()&&!valid)Text("1원 이상, 남은 지출 이하로 입력해 주세요.",color=MaterialTheme.colorScheme.error)
  }},confirmButton={Button(enabled=!busy&&valid,onClick={val confirmedAmount=amount?:return@Button;runTask{
   api.request("/v1/transactions/"+item.getString("id")+"/cancellations","POST",JSONObject().put("clientId",UUID.randomUUID().toString()).put("amountWon",confirmedAmount).put("cardRefundWon",if(item.isNull("card_id"))JSONObject.NULL else refund).apply{if(!item.isNull("purchase_facts"))put("receivedAt",cancelReceivedDate)})
   invalidate();cancelEntry=null;refreshLedger();message="부분 취소 "+won(confirmedAmount)+"을 반영했습니다."
  }}){Text("취소 금액 반영")}},dismissButton={TextButton(enabled=!busy,onClick={cancelEntry=null}){Text("닫기")}})
 }
}
// Keep navigation in its own recomposition scope with an explicit immutable input.
@Composable private fun AdvisorNavigation(enabled:Boolean,onTabChanged:(Int)->Unit,onLogout:()->Unit){
 Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
  listOf("설정","카드 비교","가계부").forEachIndexed{i,label->TextButton(onClick={onTabChanged(i)},enabled=enabled){Text(label)}}
  TextButton(onClick=onLogout,enabled=enabled){Text("나가기")}
 }
}
