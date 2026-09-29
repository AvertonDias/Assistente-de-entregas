package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.example.data.local.dao.ActivityReportDao
import com.example.data.local.dao.DeliveryDao
import com.example.data.local.dao.PersonDao
import com.example.data.local.entity.AppActionLog
import com.example.data.local.entity.DailyActivitySummary
import com.example.data.local.entity.Delivery
import com.example.data.local.entity.Person
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Person::class, Delivery::class, DailyActivitySummary::class, AppActionLog::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao
    abstract fun deliveryDao(): DeliveryDao
    abstract fun activityReportDao(): ActivityReportDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pessoas_endereco` ON `pessoas` (`endereco`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pessoas_numero` ON `pessoas` (`numero`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pessoas_nome` ON `pessoas` (`nome`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pessoas_dataAtualizacao` ON `pessoas` (`dataAtualizacao`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `resumo_diario_atividades` (
                        `dateKey` TEXT NOT NULL,
                        `totalActiveTimeMs` INTEGER NOT NULL,
                        `firstOpenTime` INTEGER NOT NULL,
                        `lastActiveTime` INTEGER NOT NULL,
                        `totalDeliveries` INTEGER NOT NULL,
                        `totalAddressesDetected` INTEGER NOT NULL,
                        `totalSignaturesApplied` INTEGER NOT NULL,
                        `totalPeopleCreatedOrEdited` INTEGER NOT NULL,
                        `totalSearches` INTEGER NOT NULL,
                        `totalActionsCount` INTEGER NOT NULL,
                        PRIMARY KEY(`dateKey`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `historico_acoes_app` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `dateKey` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `actionType` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `details` TEXT NOT NULL,
                        `category` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_historico_acoes_dateKey` ON `historico_acoes_app` (`dateKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_historico_acoes_timestamp` ON `historico_acoes_app` (`timestamp`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `historico_acoes_app` ADD COLUMN `durationMs` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "assistente_entregas.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
