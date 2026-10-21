package dev.cardadvisor.android
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.*
import java.util.UUID

@Composable internal fun FinancialCardMappingEditor(api:ApiClient,connection:String,onBusyChanged:(Boolean)->Unit,onChanged:suspend()->Unit,onClose:()->Unit){
 val scope=rememberCoroutineScope{Dispatchers.Main.immediate}
 var data by remember{mutableStateOf<JSONObject?>(null)};var products by remember{mutableStateOf(JSONArray())}
 var busy by remember{mutableStateOf(false)};var stale by remember{mutableStateOf(true)};var expired by remember{mutableStateOf(false)}
 var notice by remember{mutableStateOf("조회된 카드와 내 실제 카드를 불러옵니다.")}
 var group by remember{mutableStateOf<JSONObject?>(null)};var target by remember{mutableStateOf<JSONObject?>(null)};var unlink by remember{mutableStateOf(false)};var confirmed by remember{mutableStateOf(false)}
 suspend fun load(){val d=JSONObject(api.request("/v1/finance/connections/$connection/card-mappings"));require(d.getString("policy")=="ASSOCIATION_ONLY_NO_AUTOMATIC_CALCULATION");val groups=d.getJSONArray("groups");for(i in 0 until groups.length()){val g=groups.getJSONObject(i);require(g.getString("sourceKey").matches(Regex("[a-f0-9]{64}")));require(g.getLong("version")>=0)};val p=JSONArray(api.request("/v1/card-products"));data=d;products=p;stale=false;group=null;target=null;confirmed=false;unlink=false}
 fun act(change:Boolean=false,block:suspend()->Unit){if(busy||expired)return;busy=true;onBusyChanged(true);scope.launch{try{block()}catch(e:CancellationException){throw e}catch(e:ApiException){stale=true;notice=when(e.status){401->{expired=true;"로그인이 만료되었습니다. 다시 로그인해 주세요."};409->"연결 상태나 저장 내용이 바뀌었습니다. 새로고침 후 다시 확인해 주세요.";422->"카드사와 실제 상품, 직접 확인 여부를 확인해 주세요.";else->"카드 정보를 불러오지 못했습니다. 새로고침해 주세요."}}catch(e:Exception){stale=true;notice=if(change)"저장 결과를 확인하지 못했습니다. 다시 저장하지 말고 새로고침해 주세요." else "카드 정보를 불러오지 못했습니다. 서버 연결을 확인해 주세요."}finally{if(currentCoroutineContext().isActive){busy=false;onBusyChanged(false)}}}}
 LaunchedEffect(connection){act{load();notice="금융 카드와 보유 상품을 직접 확인해 연결하세요."}}
 Text("금융 카드와 내 카드 연결",style=MaterialTheme.typography.titleLarge)
 Text("카드명과 끝자리는 서로 다른 카드에서도 같을 수 있습니다. 카드사 앱과 대조한 뒤 직접 연결해 주세요.")
 Text("이 단계는 상품 연결만 저장합니다. 거래별 실적·혜택 조건을 확인하기 전에는 금액을 자동 합산하지 않으며 추천을 보류합니다.")
 Text(notice,Modifier.testTag("mappingNotice"));if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
 OutlinedButton(enabled=!busy,onClick=onClose,modifier=Modifier.testTag("mappingBack")){Text("금융 연결 관리로 돌아가기")}
 OutlinedButton(enabled=!busy&&!expired,onClick={act{load();notice="저장된 연결을 확인했습니다."}},modifier=Modifier.testTag("mappingRefresh")){Text("카드 연결 새로고침")}
 val d=data;val groups=d?.getJSONArray("groups")?:JSONArray();val targets=d?.getJSONArray("targets")?:JSONArray()
 if(!stale&&groups.length()==0)Text("조회된 금융 카드가 없습니다. 이용내역을 먼저 불러와 주세요.")
 for(i in 0 until groups.length()){
  val g=groups.getJSONObject(i);val key=g.getString("sourceKey")
  DesignPanel {
   Text(g.optString("label").ifBlank{"카드명 미제공"},style=MaterialTheme.typography.titleMedium)
   Text("카드 끝자리 · "+g.getString("mask").takeLast(if(g.getString("mask").startsWith("*************"))3 else 4))
   Text("조회된 이용내역 ${g.getLong("transaction_count")}건")
   Text(if(g.isNull("cardId"))"아직 상품에 연결하지 않았습니다." else "연결한 상품: "+g.getString("mappedName"),Modifier.testTag("mappingState_$i"))
   FinanceBadge(if(g.isNull("cardId"))"연결 저장 필요"else "연결 저장됨",g.isNull("cardId"))
   Text("서버 저장 버전 ${g.getLong("version")}",Modifier.testTag("mappingVersion_$i"),style=MaterialTheme.typography.bodySmall)
   if(!g.isNull("cardId"))Text("상품 연결은 완료됐습니다. 실제 거래별 실적·혜택 자동 계산은 아직 지원 범위를 검수 중입니다.")
   OutlinedButton(enabled=!busy&&!stale&&!expired,onClick={group=g;target=null;unlink=false;confirmed=false},modifier=Modifier.testTag("mappingChoose_$i")){Text(if(g.isNull("cardId"))"내 카드 상품 선택" else "연결 상품 변경")}
   if(!g.isNull("cardId"))TextButton(enabled=!busy&&!stale&&!expired,onClick={group=g;target=null;unlink=true;confirmed=false},modifier=Modifier.testTag("mappingUnlink_$i")){Text("상품 연결 해제")}
  }
 }
 group?.let{g->
  fun save(){
   val removing=unlink;val requested=if(removing)null else target!!.getString("id");val source=g.getString("sourceKey")
   val body=JSONObject().put("sourceKey",source).put("cardId",requested?:JSONObject.NULL).put("expectedVersion",g.getLong("version")).put("expectedConnectionVersion",d!!.getLong("connectionVersion")).put("confirmed",true)
   act(true){
    val saved=JSONObject(api.request("/v1/finance/connections/$connection/card-mappings","PUT",body));val savedGroups=saved.getJSONArray("groups")
    val savedVersion=(0 until savedGroups.length()).map{savedGroups.getJSONObject(it)}.first{it.getString("sourceKey")==source}.getLong("version")
    load()
    val latest=data!!.getJSONArray("groups");val verified=(0 until latest.length()).map{latest.getJSONObject(it)}.first{it.getString("sourceKey")==source}
    require((if(verified.isNull("cardId"))null else verified.getString("cardId"))==requested&&verified.getLong("version")==savedVersion)
    notice="상품 연결을 저장하고 서버에서 다시 확인했습니다. 가계부 금액은 바꾸지 않았습니다."
    try{onChanged()}catch(e:CancellationException){throw e}catch(e:Exception){notice="상품 연결은 저장·재조회됐습니다. 카드 현황은 다시 조회해 주세요."}
   }
  }
  AlertDialog(onDismissRequest={if(!busy){group=null;target=null;confirmed=false}},title={Text(if(unlink)"상품 연결 해제"else "연결할 상품 확인")},
   text={Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
    if(stale){Text(notice,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("mappingDialogError"));TextButton(enabled=!busy&&!expired,onClick={act{load();notice="저장된 연결을 확인했습니다."}},modifier=Modifier.testTag("mappingRecover")){Text("서버 저장 상태 다시 확인")}}
    Text("조회된 카드: "+g.optString("label").ifBlank{"이름 미확인"})
    Text("끝자리 "+g.getString("mask").takeLast(if(g.getString("mask").startsWith("*************"))3 else 4))
    if(!unlink){
     Text("1. 연결할 상품을 선택하세요",style=MaterialTheme.typography.titleSmall)
     for(i in 0 until targets.length()){val c=targets.getJSONObject(i);val chosen=target?.getString("id")==c.getString("id")
      OutlinedButton(enabled=!busy&&!stale,onClick={target=c;confirmed=false},border=BorderStroke(if(chosen)2.dp else 1.dp,if(chosen)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth().testTag("mappingTarget_$i")){Text((if(chosen)"✓ "else "")+c.getString("name"))}
     }
     if(targets.length()==0)Text("아직 등록한 실제 상품이 없습니다. 상품을 먼저 등록한 뒤 이 화면에서 선택해 주세요.")
     for(i in 0 until products.length()){val p=products.getJSONObject(i);val productId=p.getString("id");val matches=if(d?.getString("organization")=="0301")productId.startsWith("KB_")else productId.startsWith("HANA_");val registered=(0 until targets.length()).any{targets.getJSONObject(it).getString("source")==p.getString("ruleVersion")}
      if(matches&&!registered)OutlinedButton(enabled=!busy&&!stale,onClick={act(true){val source=g.getString("sourceKey");api.request("/v1/cards/from-product","POST",JSONObject().put("productId",productId).put("expectedRuleVersion",p.getString("ruleVersion")));load();val refreshed=data!!.getJSONArray("groups");group=(0 until refreshed.length()).map{refreshed.getJSONObject(it)}.first{it.getString("sourceKey")==source};notice="상품을 등록했습니다. 연결할 상품을 선택한 뒤 저장해 주세요.";try{onChanged()}catch(e:CancellationException){throw e}catch(e:Exception){notice="상품은 등록됐습니다. 연결할 상품을 선택한 뒤 저장해 주세요."}}},modifier=Modifier.testTag("mappingAdd_$productId")){Text(p.getString("name")+" · 상품 등록")}
     }
     target?.let{Text("선택한 상품: "+it.getString("name"),Modifier.testTag("mappingSelection"))}
    }
    Text("2. 같은 카드인지 확인하세요. 아래 저장 버튼을 눌러야 연결됩니다.")
    Row{Checkbox(confirmed,{confirmed=it},enabled=!busy&&!stale,modifier=Modifier.testTag("mappingConfirm"));Text(if(unlink)"상품 연결만 해제하는 것을 확인했습니다."else "카드사 앱과 대조해 같은 카드임을 확인했습니다.")}
    Text("상품 연결은 거래 금액을 바꾸지 않습니다. 실제 거래별 실적·혜택 자동 계산은 아직 미완성입니다.",style=MaterialTheme.typography.bodySmall)
   }},
   confirmButton={Button(enabled=!busy&&!stale&&!expired&&confirmed&&(unlink||target!=null),onClick={save()},modifier=Modifier.testTag("mappingSave")){Text(if(unlink)"연결 해제 저장"else "선택한 연결 저장")}},
   dismissButton={TextButton(enabled=!busy,onClick={group=null;target=null;confirmed=false}){Text("선택 취소")}}
  )
 }
}
