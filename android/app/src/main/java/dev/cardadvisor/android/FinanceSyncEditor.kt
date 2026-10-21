package dev.cardadvisor.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.*
import java.util.UUID

@Composable internal fun FinanceSyncEditor(api:ApiClient,connectionId:String,providerReady:Boolean,onBusyChanged:(Boolean)->Unit,onInvalidated:()->Unit,onChanged:suspend()->Unit,onClose:()->Unit){
 val scope=rememberCoroutineScope { kotlinx.coroutines.Dispatchers.Main.immediate };val keyboard=LocalSoftwareKeyboardController.current;val focus=LocalFocusManager.current
 val today=LocalDate.now(ZoneId.of("Asia/Seoul"))
 var from by rememberSaveable(connectionId){mutableStateOf(today.withDayOfMonth(1).toString())};var to by rememberSaveable(connectionId){mutableStateOf(today.toString())}
 var connection by remember{mutableStateOf<FinanceConnection?>(null)};var rows by remember{mutableStateOf(emptyList<FinancialRow>())};var runs by remember{mutableStateOf(emptyList<SyncRun>())}
 var busy by remember{mutableStateOf(false)};var stale by remember{mutableStateOf(true)};var expired by remember{mutableStateOf(false)};var heldOnly by remember{mutableStateOf(false)}
 var periodExpanded by rememberSaveable(connectionId){mutableStateOf(false)}
 var notice by remember{mutableStateOf("저장된 거래를 불러옵니다.")}
 var benefitTransaction by remember{mutableStateOf<String?>(null)}
 var performanceTransaction by remember{mutableStateOf<String?>(null)}
 var draft by remember{mutableStateOf<FinancialRow?>(null)};var candidates by remember{mutableStateOf(emptyList<ManualCandidate>())}
 var choice by remember{mutableStateOf<String?>(null)};var actionName by remember{mutableStateOf<String?>(null)}
 fun clearDraft(){draft=null;candidates=emptyList();choice=null;actionName=null}
 fun action(mutation:Boolean=false,block:suspend()->Unit){
  if(busy||expired)return
  busy=true;notice="처리 중입니다.";onBusyChanged(true);focus.clearFocus();keyboard?.hide()
  Diagnostics.event(DiagnosticEvent.SYNC_TASK_ENQUEUED)
  scope.launch{Diagnostics.event(DiagnosticEvent.SYNC_TASK_BEGIN);try{block();currentCoroutineContext().ensureActive()}
   catch(e:CancellationException){throw e}
   catch(e:Exception){currentCoroutineContext().ensureActive();stale=true
    notice=when{
     e is ApiException&&e.status==401->{expired=true;"로그인이 만료되었습니다. 나가기 후 다시 로그인해 주세요."}
     e is ApiException&&e.status==409->"거래 또는 연결 버전이 바뀌었습니다. 선택은 보존했지만 저장은 중단했습니다. 저장된 결과를 다시 조회해 주세요."
     e is ApiException&&e.status==422->"기간 또는 연결 후보를 확인해 주세요. 최신 결과 조회 후 다시 선택해 주세요."
     e is ApiException&&e.status==503->"동기화 서버가 준비되지 않았습니다. 저장된 결과는 조회할 수 있습니다."
     mutation->"요청 결과를 확인하지 못했습니다. 재전송하지 말고 저장된 결과를 조회해 주세요."
     else->"저장된 거래를 불러오지 못했습니다. 연결을 확인하고 다시 조회해 주세요."
    }
   }finally{Diagnostics.event(DiagnosticEvent.SYNC_TASK_END);if(currentCoroutineContext().isActive){busy=false;onBusyChanged(false)}}
  }
 }
 suspend fun load(){
  stale=true
  val c=FinanceConnection.parse(JSONObject(api.request("/v1/finance/connections/$connectionId")))
  val t=syncList(api.request("/v1/finance/connections/$connectionId/transactions"),FinancialRow::parse)
  val r=syncList(api.request("/v1/finance/connections/$connectionId/sync-runs"),SyncRun::parse)
  require(c.id==connectionId&&t.all{it.connection==connectionId}&&r.all{it.connection==connectionId});require(t.map{it.id}.distinct().size==t.size)
  currentCoroutineContext().ensureActive();connection=c;rows=t;runs=r;clearDraft();onInvalidated();onChanged();currentCoroutineContext().ensureActive();stale=false
 }
 fun refresh(){action{load();notice="저장된 결과를 조회했습니다."}}
 LaunchedEffect(connectionId){refresh()}
 benefitTransaction?.let{id->FinancialBenefitEditor(api,id,onBusyChanged=onBusyChanged,onChanged={onInvalidated();onChanged()},onClose={benefitTransaction=null});return}
 performanceTransaction?.let{id->FinancialPerformanceEditor(api,id,onBusyChanged=onBusyChanged,onChanged={onInvalidated();onChanged()},onClose={performanceTransaction=null});return}
 val running=runs.any{it.status=="RUNNING"}
 val enabled=!busy&&!stale&&!expired
 val datesValid=runCatching{val f=LocalDate.parse(from);val t=LocalDate.parse(to);!f.isAfter(t)&&!t.isAfter(today)&&!t.isAfter(f.plusDays(30))}.getOrDefault(false)
 Text("카드 이용내역",style=MaterialTheme.typography.titleLarge)
 Text((connection?.organization?.let(::institutionName)?:"카드사 확인 중")+" · 카드별 결제와 취소를 확인하세요.",color=MaterialTheme.colorScheme.onSurfaceVariant)
 Text(notice,Modifier.testTag("syncNotice"))
 if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
 OutlinedButton(enabled=!busy,onClick=onClose,modifier=Modifier.testTag("syncBack")){Text("연결 관리로 돌아가기")}
 OutlinedButton(enabled=!busy&&!expired,onClick={refresh()},modifier=Modifier.testTag("syncRefresh")){Text("내역 새로고침")}
 if(stale)Text("최신 결과를 확인하기 전까지 동기화와 거래 확인 저장을 보류합니다.")
 FinanceCounts(rows)
 TextButton(enabled=!busy,onClick={periodExpanded=!periodExpanded},modifier=Modifier.testTag("syncPeriodToggle")){Text(if(periodExpanded)"조회 기간 접기" else "다른 기간 이용내역 불러오기")}
 if(periodExpanded) DesignPanel {
 Text("불러올 기간",style=MaterialTheme.typography.titleMedium)
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
  OutlinedButton(enabled=enabled,onClick={from=today.withDayOfMonth(1).toString();to=today.toString()},modifier=Modifier.testTag("syncThisMonth")){Text("이번 달")}
  OutlinedButton(enabled=enabled,onClick={val m=YearMonth.from(today).minusMonths(1);from=m.atDay(1).toString();to=m.atEndOfMonth().toString()},modifier=Modifier.testTag("syncPreviousMonth")){Text("지난달")}
 }
 OutlinedTextField(from,{from=it.take(10)},enabled=!busy,label={Text("조회 시작일 YYYY-MM-DD")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("syncFrom"))
 OutlinedTextField(to,{to=it.take(10)},enabled=!busy,label={Text("조회 종료일 YYYY-MM-DD")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("syncTo"))
 Text("한 번에 최대 31일, 미래 날짜 제외. 아래 저장된 거래·보류 목록은 조회 기간 밖의 내역도 포함합니다.")
 if(!datesValid)Text("시작·종료 날짜와 31일 이내 범위를 확인해 주세요.")
 if(running)Text("진행 중인 요청이 있어 새 동기화를 보류합니다. 결과 조회로 확인해 주세요.")
 if(connection?.state!=FinanceState.ACTIVE)Text("활성 연결에서만 새 거래를 가져올 수 있습니다. UNKNOWN·추가 인증·해제 상태는 보류합니다.")
 Button(enabled=enabled&&providerReady&&connection?.state==FinanceState.ACTIVE&&connection?.organization in listOf("0301","0313")&&!running&&datesValid,onClick={
  val body=JSONObject().put("from",from).put("to",to).put("expectedVersion",requireNotNull(connection).version);val key=UUID.randomUUID().toString()
  action(true){stale=true;onInvalidated();val r=SyncRun.parse(JSONObject(api.request("/v1/finance/connections/$connectionId/sync","POST",body,key)));require(r.connection==connectionId);load();notice=syncOutcome(r.status)}
 },modifier=Modifier.fillMaxWidth().heightIn(min=50.dp).testTag("syncStart")){Text("이 기간 이용내역 불러오기")}
 }
 runs.firstOrNull()?.let{r->Text("최근 요청 ${r.from} ~ ${r.to} · "+when(r.status){"IMPORTED"->"가계부 기록 완료";"PARTIAL"->"확인할 내역 있음";"FAILED"->"실패";else->"처리 중"},Modifier.testTag("syncRun"));Text("조회한 내역 ${r.received}건 · 가계부에 기록 ${r.applied}건 · 확인 필요 ${r.held}건")}
 Row{Checkbox(heldOnly,{heldOnly=it},enabled=!busy,modifier=Modifier.testTag("syncHeldOnly"));Text("확인 필요한 내역만 보기")}
 val displayed=rows.filter{!heldOnly||it.held}.sortedByDescending{it.date}
 if(!stale&&displayed.isEmpty())Text(if(heldOnly)"확인이 필요한 내역이 없습니다." else "저장된 거래가 없습니다.",Modifier.testTag("syncEmpty"))
 if(draft==null)for(row in displayed){key(row.id){DesignPanel {
  FinancialTransactionHeading(row)
  HorizontalDivider(color=MaterialTheme.colorScheme.background)
  FinancialTransactionStatus(row)
  Text(when(row.payment){"1"->"일시불";"2"->"할부 ${row.installments?.toString()?:"미확인"}개월 · 월 청구액 아님";else->"결제 방식 미확인 또는 기타"},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  if(row.mappedCardName!=null&&row.kind=="APPROVAL"&&row.ledger!=null)OutlinedButton(enabled=enabled,onClick={performanceTransaction=row.id}){Text("다음 달용 실적 인정·취소 확인")}
  if(row.mappedCardName!=null&&row.kind=="APPROVAL"&&row.ledger!=null)OutlinedButton(enabled=enabled,onClick={benefitTransaction=row.id}){Text("혜택 사용·잔여 한도 확인")}
  if(row.manualReview)OutlinedButton(enabled=enabled,onClick={action{
   val list=ManualCandidate.from(api.request("/v1/transactions?month=${requireNotNull(row.date).take(7)}"),row,rows)
   currentCoroutineContext().ensureActive();clearDraft();draft=row;candidates=list
  }},modifier=Modifier.testTag("syncManual_${row.id}")){Text("수동 구매 중복 확인")}
  if(row.cancelReview)OutlinedButton(enabled=enabled,onClick={clearDraft();draft=row;actionName="LINK_CANCELLATION"},modifier=Modifier.testTag("syncCancel_${row.id}")){Text("취소한 결제 선택")}
  if(row.canIgnore)TextButton(enabled=enabled,onClick={clearDraft();draft=row;actionName="IGNORE"},modifier=Modifier.testTag("syncIgnore_${row.id}")){Text("이 거래 계산 제외 확인")}
 }}}
 draft?.let{d->
  Text(if(d.cancelReview)"어떤 결제를 취소했나요?" else "거래 확인",style=MaterialTheme.typography.titleMedium)
  if(d.cancelReview)CancellationDetail("이번에 확인할 취소",d,true,Modifier.testTag("cancelSummary"))
  Text("선택만으로 저장되지 않습니다. 아래 확인 버튼을 눌러 반영합니다.")
  if(d.manualReview&&actionName!="IGNORE"){
   Text("같은 구매를 선택하면 기존 분류·예산 포함 설정을 유지합니다. 가상 카드 실적·혜택 계산은 제거하며 이후 취소는 금융 내역에서 확인합니다.")
   if(candidates.isEmpty())Text("연결 가능한 수동 구매가 없습니다. 다른 연결에 이미 반영된 거래인지 확인하세요.")
   for(c in candidates)OutlinedButton(enabled=enabled,onClick={choice=c.id;actionName="LINK_MANUAL"},modifier=Modifier.testTag("syncCandidate_${c.id}")){Text("${c.date} ${syncMoney(c.amount)} · 남은 지출 ${syncMoney(c.net)} · ${categoryLabel(c.category)} · ${if(c.included)"예산 포함" else "예산 제외"} · ${c.cardName?:"카드 미연결"}")}
   OutlinedButton(enabled=enabled,onClick={choice=null;actionName="IMPORT_SEPARATELY"},modifier=Modifier.testTag("syncSeparate")){Text("기존 수동 기록과 다른 구매입니다")}
  }
  if(d.cancelReview&&actionName!="IGNORE"){
   val originals=rows.filter{o->
    val already=rows.filter{it.kind=="CANCEL_EVENT"&&it.status=="APPLIED"&&it.original==o.id}.sumOf{it.refund?:0L}
    o.kind=="APPROVAL"&&o.status=="APPLIED"&&o.ledger!=null&&o.card!=null&&o.card==d.card&&o.date!=null&&d.date!=null&&o.date<=d.date&&o.amount!=null&&d.refund!=null&&o.refund==0L&&o.amount-already>=d.refund
   }.sortedWith(compareByDescending<FinancialRow>{it.merchant!=null&&it.merchant==d.merchant}.thenByDescending{it.amount==d.refund}.thenByDescending{it.date})
   val previous=runCatching{YearMonth.from(LocalDate.parse(d.date)).minusMonths(1)}.getOrNull()
   if(previous!=null)OutlinedButton(enabled=enabled&&providerReady&&connection?.state==FinanceState.ACTIVE&&!running,onClick={
    val f=previous.atDay(1).toString();val t=previous.atEndOfMonth().toString();val cancelId=d.id
    val body=JSONObject().put("from",f).put("to",t).put("expectedVersion",requireNotNull(connection).version)
    action(true){stale=true;onInvalidated();val result=SyncRun.parse(JSONObject(api.request("/v1/finance/connections/$connectionId/sync","POST",body,UUID.randomUUID().toString())));require(result.connection==connectionId);load();from=f;to=t
     rows.find{it.id==cancelId&&it.cancelReview}?.let{draft=it;actionName="LINK_CANCELLATION";choice=null}
     notice=if(result.status=="FAILED")"지난달 조회를 마치지 못했습니다. 기존 내역은 유지됩니다. 다시 조회하기 전에 결과를 확인해 주세요." else "지난달 내역도 함께 비교할 수 있습니다. 원래 결제는 직접 선택해 주세요."
    }
   },modifier=Modifier.testTag("cancelPreviousMonth")){Text("${previous.monthValue}월 결제도 불러와 비교하기")}
   Text("1. 위 취소 내역을 확인하세요.\n2. 아래에서 실제로 취소한 결제를 선택하세요.\n3. 선택한 결제와 환불액을 확인한 뒤 연결하세요.")
   Text("같은 카드에서 취소일 이전에 결제한 내역입니다. 가맹점·금액이 비슷해도 자동으로 선택하지 않습니다.")
   if(originals.isEmpty())Text("연결할 수 있는 결제가 아직 없습니다. 결제했던 달의 이용내역을 먼저 불러오세요. 확실하지 않으면 연결하지 않고 나중에 확인해도 됩니다.")
   if(originals.isNotEmpty()&&d.merchant!=null&&originals.none{it.merchant==d.merchant})Text("같은 가맹점명의 결제가 현재 내역에 없습니다. 찾는 결제가 없다면 결제한 달의 이용내역을 먼저 불러오세요. 확실하지 않은 결제에는 연결하지 마세요.")
   for(o in originals)OutlinedButton(enabled=enabled,onClick={choice=o.id},modifier=Modifier.fillMaxWidth().testTag("syncOriginal_${o.id}")){
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)){
     Text(if(choice==o.id)"선택한 결제" else "이 결제 선택",style=MaterialTheme.typography.labelLarge)
     Text(o.merchant?:"가맹점명 미제공",style=MaterialTheme.typography.titleMedium)
     Text("${o.date} · ${o.cardLabel?:"카드명 미제공"}")
     Text("결제 ${syncMoney(o.amount)} · 카드 끝자리 ${o.card?.takeLast(if(o.card?.startsWith("*************")==true)3 else 4)}")
     if(o.merchant!=null&&o.merchant==d.merchant)Text("취소 내역과 가맹점명이 같습니다")
     if(o.amount==d.refund)Text("취소 금액과 결제 금액이 같습니다")
    }
   }
  }
  Text(when(actionName){"LINK_MANUAL"->"선택: 기존 수동 구매와 연결";"IMPORT_SEPARATELY"->"선택: 별도 구매 추가 · 지출이 늘어납니다";"LINK_CANCELLATION"->if(choice==null)"원거래를 선택하세요." else "선택한 결제를 아래에서 확인해 주세요.";"IGNORE"->"선택: 계산 제외 · 이 금융 증거를 계산에서 빼고 추천을 재개할 수 있습니다. 실제 지출·환불이 사라지는 것은 아닙니다. 이후 같은 증거를 다시 가져와도 제외가 유지됩니다.";else->"연결 방법을 선택하세요."},Modifier.testTag("syncSelection"))
  if(actionName=="LINK_MANUAL")candidates.find{it.id==choice}?.let{Text("선택한 구매: ${it.date} · ${syncMoney(it.amount)} · ${categoryLabel(it.category)} · ${if(it.included)"예산 포함" else "예산 제외"} · ${it.cardName?:"카드 미연결"}")}
  if(actionName=="LINK_CANCELLATION")rows.find{it.id==choice}?.let{
   Text("마지막으로 확인해 주세요",style=MaterialTheme.typography.titleMedium)
   CancellationDetail("선택한 원래 결제",it,false,Modifier.testTag("cancelChosen"))
   Text("이 결제에서 ${syncMoney(d.refund)}를 환불로 기록합니다. 실제 카드 환불을 실행하는 기능은 아닙니다.")
  }
  val valid=actionName in listOf("IGNORE","IMPORT_SEPARATELY")||choice!=null
  Button(enabled=enabled&&valid,onClick={
   val body=JSONObject().put("version",d.version).put("action",actionName)
   if(actionName=="LINK_MANUAL")body.put("ledgerId",choice);if(actionName=="LINK_CANCELLATION")body.put("approvalId",choice)
   action(true){stale=true;onInvalidated();val result=FinancialRow.parse(JSONObject(api.request("/v1/finance/transactions/${d.id}/resolve","POST",body)));require(result.id==d.id&&result.connection==connectionId);load();notice=if(result.held)"확인 요청 후에도 보류 중입니다. 금액·취소 충돌을 확인해 주세요." else "거래 확인을 저장했습니다."}
  },modifier=Modifier.testTag("syncConfirm")){Text(if(actionName=="LINK_CANCELLATION")"이 결제의 취소로 연결하기" else "선택한 내용 확인하고 저장")}
  TextButton(enabled=!busy,onClick={clearDraft()}){Text(if(d.cancelReview)"지금 연결하지 않고 돌아가기" else "거래 확인 닫기")}
 }
}

@Composable private fun CancellationDetail(title:String,row:FinancialRow,refund:Boolean,modifier:Modifier=Modifier){
 Surface(modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surface,shape=MaterialTheme.shapes.large){Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Text(title,style=MaterialTheme.typography.labelLarge)
  Text(row.merchant?:"가맹점명 미제공",style=MaterialTheme.typography.titleMedium)
  Text((if(refund)"취소일 " else "결제일 ")+(row.date?:"미확인"))
  Text(row.cardLabel?:"카드명 미제공")
  row.mappedCardName?.let{Text("연결 상품 · "+it+" · 실적·혜택 확인 필요",style=MaterialTheme.typography.bodySmall)}
  row.card?.let{Text("카드 끝자리 · "+it.takeLast(if(it.startsWith("*************"))3 else 4))}
  Text((if(refund)"취소 금액 " else "결제 금액 ")+syncMoney(if(refund)row.refund else row.amount),style=MaterialTheme.typography.titleLarge)
 }}
}
