package dev.cardadvisor.android

/** A bounded navigation grace. Never authorizes a recommendation or a payment. */
internal class CheckoutHandoffPolicy(private val ownPackage:String,private val shoppingPackages:Set<String>){
 private var deadline:Long?=null
 fun reset(){deadline=null}
 fun keep(pkg:String?,hasCandidate:Boolean,trustedNavigation:Boolean,now:Long):Boolean{
  if(!hasCandidate){reset();return false}
  if(deadline?.let{now>=it}==true){reset();return false}
  if(pkg==ownPackage||pkg in shoppingPackages){reset();return true}
  if(pkg==null||trustedNavigation){if(deadline==null)deadline=now+8000;return true}
  reset();return false
 }
}
