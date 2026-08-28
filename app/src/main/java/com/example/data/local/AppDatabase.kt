package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.DeliveryDao
import com.example.data.local.dao.PersonDao
import com.example.data.local.entity.Delivery
import com.example.data.local.entity.Person
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Person::class, Delivery::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun deliveryDao(): DeliveryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "assistente_entregas.db"
                )
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database.personDao(), database.deliveryDao())
                    }
                }
            }
        }

        private suspend fun populateInitialData(personDao: PersonDao, deliveryDao: DeliveryDao) {
            if (personDao.countPersons() > 0) return

            val samplePersons = listOf(
                Person(
                    nome = "João da Silva",
                    documento = "123.456.789-00",
                    endereco = "Cel. Francisco Paulino da Costa, 630",
                    numero = "630",
                    complemento = "",
                    bairro = "",
                    cidade = "",
                    uf = "",
                    observacao = ""
                ),
                Person(
                    nome = "Maria Oliveira Santos",
                    documento = "234.567.890-11",
                    endereco = "Av. Brasil, 1040",
                    numero = "1040",
                    complemento = "",
                    bairro = "",
                    cidade = "",
                    uf = "",
                    observacao = ""
                ),
                Person(
                    nome = "Carlos Eduardo Souza",
                    documento = "345.678.901-22",
                    endereco = "Rua Sete de Setembro, 150",
                    numero = "150",
                    complemento = "",
                    bairro = "",
                    cidade = "",
                    uf = "",
                    observacao = ""
                )
            )

            personDao.insertAll(samplePersons)
        }
    }
}
