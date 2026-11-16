package com.example

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.folioscan.core.vision.OpenCvDocumentProcessor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidApp
class FolioScanApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var visionProcessor: OpenCvDocumentProcessor

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // StrictMode verifies zero accidental network sockets or disk-on-main-thread stalls
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectCleartextNetwork()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
        visionProcessor.ensureInitialized()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppWorkModule {
    @Provides
    @Singleton
    fun provideWorkManager(app: Application): WorkManager = WorkManager.getInstance(app)
}
