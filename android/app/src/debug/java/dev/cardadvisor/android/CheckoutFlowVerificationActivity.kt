package dev.cardadvisor.android
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
/** Internal synthetic-session host. Does not open the personal session. */
class CheckoutFlowVerificationActivity:ComponentActivity(){
 companion object {var client:ApiClient?=null}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or android.view.WindowManager.LayoutParams.FLAG_SECURE);setContent{AdvisorTheme{AdvisorApp(requireNotNull(client),checkoutRequest=1)}}}
}
