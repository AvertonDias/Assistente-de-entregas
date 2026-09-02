package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.example.data.firebase.FirebaseSyncRepository
import com.example.data.firebase.FirebaseSyncRepositoryImpl
import com.example.data.local.AppDatabase
import com.example.data.repository.AuthRepository
import com.example.data.repository.FirebaseAuthRepositoryImpl
import com.example.data.repository.DeliveryRepository
import com.example.data.repository.DeliveryRepositoryImpl
import com.example.data.repository.PersonRepository
import com.example.data.repository.PersonRepositoryImpl
import com.example.data.repository.SettingsRepository
import com.example.data.repository.SettingsRepositoryImpl
import com.example.data.repository.SignatureRepository
import com.example.data.repository.SignatureRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DeliveryApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val authRepository: AuthRepository by lazy { FirebaseAuthRepositoryImpl(this) }
    val personRepository: PersonRepository by lazy { PersonRepositoryImpl(database.personDao()) }
    val deliveryRepository: DeliveryRepository by lazy { DeliveryRepositoryImpl(database.deliveryDao()) }
    val signatureRepository: SignatureRepository by lazy { SignatureRepositoryImpl(database.personDao(), database.deliveryDao()) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepositoryImpl(this, database.personDao(), database.deliveryDao()) }
    val firebaseSyncRepository: FirebaseSyncRepository by lazy {
        FirebaseSyncRepositoryImpl(this, database.personDao(), database.deliveryDao())
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        bypassHiddenApiRestrictions()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val app = FirebaseApp.initializeApp(this)
                if (app == null) {
                    val options = com.google.firebase.FirebaseOptions.Builder()
                        .setApplicationId("1:946779143583:android:ea254adf31519413b591aa")
                        .setApiKey("AIzaSyDKSJOuGg8nDr4HTiF9SXZcdYrl6FSeB0w")
                        .setProjectId("assistente-de-entregas")
                        .setStorageBucket("assistente-de-entregas.firebasestorage.app")
                        .build()
                    FirebaseApp.initializeApp(this, options)
                }
            }
        } catch (e: Exception) {
            try {
                val options = com.google.firebase.FirebaseOptions.Builder()
                    .setApplicationId("1:946779143583:android:ea254adf31519413b591aa")
                    .setApiKey("AIzaSyDKSJOuGg8nDr4HTiF9SXZcdYrl6FSeB0w")
                    .setProjectId("assistente-de-entregas")
                    .setStorageBucket("assistente-de-entregas.firebasestorage.app")
                    .build()
                FirebaseApp.initializeApp(this, options)
            } catch (e2: Exception) {
                Log.w("DeliveryApp", "Firebase initialization fallback error: ${e.message} / ${e2.message}")
            }
        }
        com.example.util.CrashReporter.init(this)
    }

    private fun bypassHiddenApiRestrictions() {
        try {
            val vmRuntimeClass = Class.forName("dalvik.system.VMRuntime")
            val getRuntimeMethod = vmRuntimeClass.getDeclaredMethod("getRuntime")
            val vmRuntime = getRuntimeMethod.invoke(null)
            val setHiddenApiExemptionsMethod = vmRuntimeClass.getDeclaredMethod(
                "setHiddenApiExemptions",
                Array<String>::class.java
            )
            setHiddenApiExemptionsMethod.invoke(
                vmRuntime,
                arrayOf(
                    "Landroid/view/accessibility/AccessibilityNodeInfo;",
                    "Landroid/view/accessibility/"
                )
            )
        } catch (e: Throwable) {
            Log.d("DeliveryApp", "HiddenApiBypass: ${e.message}")
        }
    }

    companion object {
        lateinit var instance: DeliveryApp
            private set
    }
}
