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
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (e: Exception) {
            Log.w("DeliveryApp", "Firebase initialization deferred: ${e.message}")
        }
    }

    companion object {
        lateinit var instance: DeliveryApp
            private set
    }
}
