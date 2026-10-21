package dev.cardadvisor.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal fun institutionName(code:String)=when(code){"0301"->"KB국민카드";"0313"->"하나카드";else->"카드사 $code"}

@Composable internal fun FinanceBadge(text:String,attention:Boolean=false,modifier:Modifier=Modifier){
 Surface(shape=RoundedCornerShape(8.dp),color=if(attention)Color(0xFFFFF1DA)else Color(0xFFEAF1FF)){
  Text(text,modifier.padding(horizontal=10.dp,vertical=6.dp),style=MaterialTheme.typography.labelMedium,color=if(attention)Color(0xFF855000)else Color(0xFF245AC1))
 }
}

@Composable internal fun FinanceConnectionHeading(c:FinanceConnection){
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
  Surface(color=if(c.organization=="0301")Color(0xFFFFF4D5)else Color(0xFFDCF3EE),shape=RoundedCornerShape(16.dp)){
   Box(Modifier.size(48.dp),contentAlignment=Alignment.Center){Text(when(c.organization){"0301"->"KB";"0313"->"하나";else->"카드"},fontWeight=FontWeight.Bold)}
  }
  Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
   Text(institutionName(c.organization),style=MaterialTheme.typography.titleMedium)
   Text("카드사 이용내역 연결",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
  FinanceBadge(c.state.label,c.state!=FinanceState.ACTIVE,Modifier.testTag("financeState_${c.organization}"))
 }
}

@Composable internal fun FinanceCounts(rows:List<FinancialRow>){
 DesignPanel{
  Text("저장된 이용내역",style=MaterialTheme.typography.titleMedium)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
   listOf("전체" to rows.size,"가계부 기록" to rows.count{it.status=="APPLIED"},"확인 필요" to rows.count{it.held}).forEach{(label,count)->
    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
     Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     Text("${count}건",style=MaterialTheme.typography.titleLarge,color=if(label=="확인 필요"&&count>0)Color(0xFF9A6100)else MaterialTheme.colorScheme.primary)
    }
   }
  }
  Text("총 ${rows.size}건 · 가계부에 기록 ${rows.count{it.status=="APPLIED"}}건 · 확인 필요 ${rows.count{it.held}}건",Modifier.testTag("syncCounts"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
 }
}

/** Uses masked/provider metadata only; missing amounts stay unknown and no identity is inferred. */
@Composable internal fun FinancialTransactionHeading(row:FinancialRow){
 val cancel=row.kind=="CANCEL_EVENT"
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(12.dp)){
  Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
   Text(row.merchant?:"가맹점 확인 필요",style=MaterialTheme.typography.titleMedium)
   Text(row.date?:"날짜 미확인",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
  Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(5.dp)){
   Text(if(cancel)"취소·환불" else "결제",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
   Text(syncMoney(if(cancel)row.refund else row.amount),style=MaterialTheme.typography.titleLarge,color=if(cancel)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,modifier=Modifier.testTag("syncAmount_${row.id}"))
  }
 }
 Text(row.mappedCardName?:row.cardLabel?:"카드명 확인 필요",style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold)
 row.card?.let{Text("끝자리 "+it.takeLast(if(it.startsWith("*************"))3 else 4),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
 if(row.mappedCardName!=null&&row.cardLabel!=null&&row.mappedCardName!=row.cardLabel)Text("카드사 표기 · "+row.cardLabel,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
 if(row.source=="SYNTHETIC")FinanceBadge("테스트 내역 · 실제 금융정보 아님")
 if(cancel)FinanceBadge(if(row.status=="APPLIED")"취소 반영됨"else if(row.held)"원래 결제 확인 필요"else "계산 제외",row.held)
 else if(row.refund!=null&&row.refund>0)FinanceBadge(if(row.amount!=null&&row.refund==row.amount)"전체 취소" else "일부 취소")
 if(!cancel&&row.refund!=null&&row.refund>0){
  Text("취소 ${syncMoney(row.refund)}",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall)
  if(row.amount!=null&&row.refund<=row.amount)Text("취소 후 결제액 ${syncMoney(row.amount-row.refund)}",fontWeight=FontWeight.SemiBold)
 }
 if(!cancel&&row.refund==null)Text("취소 금액 확인 필요",color=Color(0xFF855000),style=MaterialTheme.typography.bodySmall)
}

@Composable internal fun FinancialTransactionStatus(row:FinancialRow){
 FinanceBadge(when(row.status){"APPLIED"->"가계부에 기록됨";"IGNORED"->"사용자 확인으로 계산 제외";else->"확인 필요"},row.held)
 // Preserve the full reason and its existing test/assistive label, not only a colored badge.
 Text(when(row.status){"APPLIED"->if(row.kind=="CANCEL_EVENT")"연결한 원래 결제에 환불을 기록했습니다." else "가계부에 반영한 내역입니다.";"IGNORED"->"이 내역은 지출·추천 계산에서 제외했습니다.";else->"확인 필요 · "+syncIssue(row.issue)},Modifier.testTag("syncStatus_${row.id}"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
