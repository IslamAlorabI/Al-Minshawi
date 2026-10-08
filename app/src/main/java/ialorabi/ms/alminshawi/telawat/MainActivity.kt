package ialorabi.ms.alminshawi.telawat

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import ialorabi.ms.alminshawi.telawat.player.PlayerViewModel
import ialorabi.ms.alminshawi.telawat.ui.home.AlMinshawiAppUi
import ialorabi.ms.alminshawi.telawat.ui.theme.AlMinshawiTheme

class MainActivity : AppCompatActivity() {
    private val playerViewModel: PlayerViewModel by viewModels()
    val openPlayerRequest = kotlinx.coroutines.flow.MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra("OPEN_PLAYER", false) == true) {
            openPlayerRequest.value = true
        }
        setContent {
            AlMinshawiTheme {
                AlMinshawiAppUi(playerViewModel, openPlayerRequest)
            }
        }
        playerViewModel.initializeController(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("OPEN_PLAYER", false)) {
            openPlayerRequest.value = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerViewModel.releaseController()
    }

    override fun onStart() {
        super.onStart()
        playerViewModel.setUiVisible(true)
    }

    override fun onStop() {
        super.onStop()
        playerViewModel.setUiVisible(false)
    }

    override fun onResume() {
        super.onResume()
        playerViewModel.syncPlayerState()
        playerViewModel.refreshMetadata()
    }
}
