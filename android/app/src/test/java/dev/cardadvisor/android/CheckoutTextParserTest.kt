package dev.cardadvisor.android
import org.junit.Test
import org.junit.Assert.*
class CheckoutTextParserTest {
 @Test fun titleSpacing(){assertTrue(CheckoutTextParser.title("주문 / 결제"));assertTrue(CheckoutTextParser.title("주문결제"));assertFalse(CheckoutTextParser.title("주문 상세"))}
 @Test fun compositeLabelAndMoney(){assertEquals(DetectionAmount.FINAL_TOTAL to 10000L,CheckoutTextParser.combined("총 결제금액: 10,000원"));assertEquals(DetectionAmount.FINAL_TOTAL,CheckoutTextParser.label("총 결제 금액"))}
 @Test fun orderValueDoesNotBecomePaymentAmount(){assertNull(CheckoutTextParser.combined("총 주문금액 10,000원"));assertNull(CheckoutTextParser.combined("배송지 01012345678"));assertNull(CheckoutTextParser.combined("결제예정금액 10,000원 20,000원"))}
 @Test fun invalidAndExcessiveAmounts(){assertNull(CheckoutTextParser.money("-10,000원"));assertNull(CheckoutTextParser.money("10000001원"));assertNull(CheckoutTextParser.money("1.5원"))}
 @Test fun observedNaverPaymentControlIsOnlyACandidate(){
  assertEquals(10000L,CheckoutTextParser.paymentButton("com.navercorp.navershopping","10,000원 결제하기",true))
  assertNull(CheckoutTextParser.paymentButton("com.musinsa.store","10,000원 결제하기",true))
  assertNull(CheckoutTextParser.paymentButton("com.navercorp.navershopping","10,000원 결제하기",false))
  for(value in listOf("0원 결제하기","10,000원 20,000원 결제하기","총주문금액 10,000원 결제하기","결제하기 10,000원","10000001원 결제하기","-1원 결제하기"))assertNull(CheckoutTextParser.paymentButton("com.navercorp.navershopping",value,true))
 }
}
