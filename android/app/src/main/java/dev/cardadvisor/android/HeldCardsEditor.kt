package dev.cardadvisor.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.*
import org.json.*
import java.util.UUID

internal data class HeldCard(val id:String,val name:String,val mask:String,val type:String,val current:Boolean,val version:Long,val cardId:String?)
internal data class HeldProduct(val id:String,val name:String,val rule:String)
internal data class HeldSnapshot(val connectionVersion:Long,val active:Boolean,val cards:List<HeldCard>,val products:List<HeldProduct>){
 companion object{fun parse(raw:String):HeldSnapshot{
  val root=JSONObject(raw);val version=root.getLong("connectionVersion");require(version>0)
  val cards=root.getJSONArray("cards");require(cards.length()<=200)
  val values=(0 until cards.length()).map{i->val c=cards.getJSONObject(i);val id=c.getString("id");UUID.fromString(id)
   val name=c.getString("name");require(name.isNotBlank()&&name.length<=120&&name.none{it.isISOControl()})
   val mask=c.getString("mask");require(Regex("(?:\\*{12}[0-9]{4}|\\*{13}[0-9]{3})").matches(mask))
   val v=c.getLong("version");require(v>0);val linked=if(c.isNull("cardId"))null else c.getString("cardId").also{UUID.fromString(it)}
   HeldCard(id,name,mask,c.getString("type"),c.getBoolean("current"),v,linked)
  };require(values.map{it.id}.distinct().size==values.size)
  val ps=root.getJSONArray("products");require(ps.length()<=100)
  val products=(0 until ps.length()).map{i->val p=ps.getJSONObject(i);val id=p.getString("id");val name=p.getString("name");val rule=p.getString("ruleVersion");require(id.matches(Regex("[A-Z0-9_]+"))&&name.isNotBlank()&&name.length<=120&&rule.startsWith("OFFICIAL_"));HeldProduct(id,name,rule)}
  return HeldSnapshot(version,root.getString("state")=="ACTIVE",values,products)
 }}
}

@Composable internal fun HeldCardsEditor(api:ApiClient,onBusyChanged:(Boolean)->Unit,onChanged:suspend()->Unit,onClose:()->Unit,onConnect:()->Unit){
 val scope=rememberCoroutineScope{Dispatchers.Main.immediate}
 var connections by remember{mutableStateOf(emptyList<FinanceConnection>())}
 var selected by remember{mutableStateOf<FinanceConnection?>(null)};var snapshot by remember{mutableStateOf<HeldSnapshot?>(null)}
 var ready by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};var stale by remember{mutableStateOf(true)}
 var notice by remember{mutableStateOf("저장된 카드사 연결을 확인합니다.")}
 var choice by remember{mutableStateOf<HeldCard?>(null)};var product by remember{mutableStateOf<HeldProduct?>(null)};var confirmed by remember{mutableStateOf(false)}
 fun action(block:suspend()->Unit){if(busy)return;busy=true;onBusyChanged(true);scope.launch{
  try{block()}catch(e:CancellationException){throw e}catch(e:Exception){stale=true;notice=if(e is ApiException&&e.status==503)"카드사 조회를 사용할 수 없습니다. 실제 연결 설정을 켜지 않은 상태라면 저장된 목록만 확인할 수 있어요."else e.message?:"목록을 다시 확인해 주세요."}
  finally{if(currentCoroutineContext().isActive){busy=false;onBusyChanged(false)}}
 }}
 suspend fun load(c:FinanceConnection){val value=HeldSnapshot.parse(api.request("/v1/finance/connections/${c.id}/held-cards"));snapshot=value;selected=c;stale=false;choice=null;product=null;confirmed=false}
 LaunchedEffect(Unit){action{
  val status=FinanceReadiness.parse(api.request("/v1/finance/codef/status"));ready=status.ready
  val rows=JSONArray(api.request("/v1/finance/connections"));val values=(0 until rows.length()).map{FinanceConnection.parse(rows.getJSONObject(it))};connections=values
  val first=values.firstOrNull{it.state==FinanceState.ACTIVE};if(first!=null)load(first)else stale=false
  notice="조회한 카드 중 본인 카드를 선택해 등록하세요. 상품 연결은 직접 확인한 경우에만 선택합니다."
 }}
 Text("보유 카드 불러오기",style=MaterialTheme.typography.titleLarge)
 Text(notice,Modifier.testTag("heldNotice"))
 if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
 if(!ready)Text("현재 카드사 실시간 조회는 비활성 상태입니다. 저장된 카드 목록은 확인할 수 있습니다.")
 OutlinedButton(enabled=!busy,onClick=onConnect){Text("카드사 연결 관리")}
 for(c in connections)OutlinedButton(enabled=!busy,onClick={action{load(c)}},modifier=Modifier.testTag("heldConnection_${c.organization}")){Text("카드사 ${c.organization} 선택")}
 val c=selected;val data=snapshot
 if(c!=null&&data!=null){
  Button(enabled=!busy&&ready&&data.active&&!stale,onClick={action{
   val value=HeldSnapshot.parse(api.request("/v1/finance/connections/${c.id}/held-cards/refresh","POST",JSONObject().put("expectedConnectionVersion",data.connectionVersion).put("confirmed",true)))
   snapshot=value;stale=false;choice=null;product=null;confirmed=false;notice="보유 카드를 조회했습니다. 선택한 카드를 확인한 뒤 내 카드로 등록하세요."
  }},modifier=Modifier.fillMaxWidth().testTag("heldFetch")){Text("카드사에서 보유 카드 조회")}
  OutlinedButton(enabled=!busy,onClick={action{load(c);notice="서버에 저장된 상태를 다시 확인했습니다."}},modifier=Modifier.testTag("heldRecover")){Text("저장 상태 다시 확인")}
  if(data.cards.isEmpty())Text("아직 조회한 카드가 없어요. 보유 카드 조회 버튼을 눌러 주세요.")
  data.cards.forEachIndexed{i,card->DesignPanel{
   Text(card.name,style=MaterialTheme.typography.titleMedium);Text("끝자리 "+card.mask.takeLast(if(card.mask.startsWith("*************"))3 else 4));Text(card.type)
   if(card.cardId!=null)Text("내 카드 등록 완료",Modifier.testTag("heldRegistered_$i"))
   else Button(enabled=!busy&&!stale&&data.active&&card.current,onClick={choice=card;product=null;confirmed=false},modifier=Modifier.testTag("heldSelect_$i")){Text("이 카드 등록")}
   if(!card.current)Text("최근 조회에서 확인되지 않은 카드입니다.")
  }}
 }
 OutlinedButton(enabled=!busy,onClick=onClose){Text("내 카드로 돌아가기")}
 val card=choice
 if(card!=null&&c!=null&&data!=null)AlertDialog(onDismissRequest={if(!busy){choice=null;confirmed=false}},title={Text("내 카드 등록 확인")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
  if(stale){Text(notice);TextButton(enabled=!busy,onClick={action{load(c)}}){Text("등록 상태 다시 확인")}}
  Text(card.name);Text("끝자리 "+card.mask.takeLast(if(card.mask.startsWith("*************"))3 else 4))
  Text("혜택 규칙을 모르면 미확인으로 등록하며 추천·실적 계산에서 제외합니다.")
  OutlinedButton(enabled=!busy,onClick={product=null;confirmed=false},modifier=Modifier.testTag("heldUnknown")){Text("상품 연결 없이 보유 카드만 등록")}
  data.products.forEachIndexed{i,p->OutlinedButton(enabled=!busy,onClick={product=p;confirmed=false},modifier=Modifier.testTag("heldProduct_$i")){Text(p.name)}}
  product?.let{Text("선택한 상품: "+it.name)}
  Row{Checkbox(confirmed,{confirmed=it},enabled=!busy,modifier=Modifier.testTag("heldConfirm"));Text("본인 카드이며 선택한 상품이 있다면 같은 상품임을 확인했습니다.",Modifier.weight(1f))}
 }},confirmButton={Button(enabled=!busy&&!stale&&confirmed,onClick={
  val p=product;val body=JSONObject().put("expectedVersion",card.version).put("expectedConnectionVersion",data.connectionVersion).put("confirmed",true)
  if(p!=null)body.put("productId",p.id).put("expectedRuleVersion",p.rule)
  action{
   val saved=HeldSnapshot.parse(api.request("/v1/finance/connections/${c.id}/held-cards/${card.id}/register","POST",body));require(saved.cards.first{it.id==card.id}.cardId!=null)
   load(c);onChanged();notice=if(p==null)"내 카드로 등록하고 서버에서 다시 확인했습니다. 상품 연결 전에는 혜택을 계산하지 않습니다."else "선택한 상품으로 등록하고 서버에서 다시 확인했습니다. 이용내역의 카드 연결은 별도로 확인해 주세요."
  }
 },modifier=Modifier.testTag("heldSave")){Text("내 카드로 등록")}},dismissButton={TextButton(enabled=!busy,onClick={choice=null;confirmed=false}){Text("취소")}})
}
