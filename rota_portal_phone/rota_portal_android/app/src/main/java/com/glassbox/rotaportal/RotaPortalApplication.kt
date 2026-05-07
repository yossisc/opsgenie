package com.glassbox.rotaportal

import android.app.Application
import com.glassbox.rotaportal.data.secure.SecretManager
import com.glassbox.rotaportal.shared.OpsgenieClient
import com.glassbox.rotaportal.shared.RotaConfig
import com.glassbox.rotaportal.shared.RotaRepository

class RotaPortalApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val secretManager = SecretManager(this)
        val opsgenieClient = OpsgenieClient(
            baseUrl = BuildConfig.OPS_API_BASE,
            tokenStore = secretManager,
        )
        appContainer = AppContainer(
            secretManager = secretManager,
            opsgenieClient = opsgenieClient,
            rotaRepository = RotaRepository(
                config = RotaConfig(
                    scheduleName = BuildConfig.OPSGENIE_SCHEDULE_NAME,
                    rotationName = BuildConfig.OPSGENIE_ROTATION_NAME,
                ),
                opsgenieClient = opsgenieClient,
            ),
        )
    }
}

data class AppContainer(
    val secretManager: SecretManager,
    val opsgenieClient: OpsgenieClient,
    val rotaRepository: RotaRepository,
)
