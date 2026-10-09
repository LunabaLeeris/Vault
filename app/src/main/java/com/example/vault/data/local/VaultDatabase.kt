package com.example.vault.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [FolderEntity::class, DocumentEntity::class, DocumentPageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var INSTANCE: VaultDatabase? = null

        fun getDatabase(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    "vault.db"
                ).fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                INSTANCE?.vaultDao()?.let { dao ->
                                    if (dao.getFolderCount() == 0) {
                                        dao.insertFolder(FolderEntity(name = "Inbox"))
                                    }
                                }
                            } catch (_: Exception) {
                                // Default folder creation fallback handled in repository
                            }
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
