package dev.cardadvisor.android
internal object CheckoutTextParser {
 val labels=mapOf("총주문금액" to DetectionAmount.ORDER,"총결제금액" to DetectionAmount.FINAL_TOTAL,"최종결제금액" to DetectionAmount.FINAL_TOTAL,"결제예정금액" to DetectionAmount.FINAL_TOTAL,"카드결제금액" to DetectionAmount.CARD,"포인트사용" to DetectionAmount.POINTS,"머니사용" to DetectionAmount.MONEY)
 fun normalize(value:String)=value.replace(Regex("\\s+"),"")
 fun title(value:String)=normalize(value).replace("/","").replace("·","") in setOf("주문결제","주문서","주문서작성")
 fun label(value:String)=labels[normalize(value)]
 fun money(value:String):Long?{val clean=normalize(value);if(!Regex("(?:₩)?[0-9][0-9,]*원?").matches(clean))return null;return clean.replace(Regex("[^0-9]"),"").toLongOrNull()?.takeIf{it in 0..10000000}}
 // Observed in Naver Plus Store 2.8.10 checkout. This is only a candidate,
 // never permission to activate the payment control or infer card/point splits.
 fun paymentButton(packageName:String,value:String,clickable:Boolean):Long?{
  if(packageName!="com.navercorp.navershopping"||!clickable)return null
  val match=Regex("([0-9][0-9,]*원)결제하기").matchEntire(normalize(value))?:return null
  return money(match.groupValues[1])?.takeIf{it>0}
 }
 fun combined(value:String):Pair<DetectionAmount,Long>?{val clean=normalize(value);for((label,kind) in labels){if(clean.startsWith(label)){val number=money(clean.removePrefix(label).removePrefix(":"));if(number!=null)return kind to number}};return null}
}
