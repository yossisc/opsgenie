package com.glassbox.rotaportal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.glassbox.rotaportal.ui.RotaPortalApp
import com.glassbox.rotaportal.ui.RotaPortalViewModel
import com.glassbox.rotaportal.ui.RotaPortalViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as RotaPortalApplication).appContainer
        setContent {
            val viewModel: RotaPortalViewModel = viewModel(
                factory = RotaPortalViewModelFactory(
                    opsgenieClient = container.opsgenieClient,
                    rotaRepository = container.rotaRepository,
                ),
            )
            RotaPortalApp(
                viewModel = viewModel,
                tokenDocsUrl = BuildConfig.TOKEN_DOCS_URL,
            )
        }
    }
}
