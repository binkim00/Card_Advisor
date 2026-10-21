package dev.cardadvisor.android
import android.content.Context
/** Release does not retain or export checkout observations. */
internal object CheckoutAudit {fun record(context:Context,pkg:String,version:String,state:String,checkout:Boolean,authentication:Boolean,counts:Map<DetectionAmount,Int>,labels:Set<DetectionAmount>,visited:Int)=Unit fun confirmation(context:android.content.Context,visible:Boolean,valid:Boolean){}
}
