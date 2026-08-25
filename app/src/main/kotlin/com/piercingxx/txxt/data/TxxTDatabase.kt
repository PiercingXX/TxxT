package com.piercingxx.txxt.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The app's Room database.
 *
 * Declares the two entities at [version] = 1 and exposes the DAOs. The
 * `app/build.gradle` kapt `room.schemaLocation` argument makes the Room
 * compiler export the schema JSON into `app/schemas/` when this class is
 * compiled.
 */
@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class TxxTDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao

    abstract fun messageDao(): MessageDao

    companion object {
        const val NAME = "txxt.db"

        @Volatile
        private var instance: TxxTDatabase? = null

        /**
         * The process-wide singleton. Every production call site (activities,
         * deliver receivers, quick reply, boot reconcile) must share ONE Room
         * instance: separate instances each hold their own SQLite connection
         * (leaked per broadcast) and — worse — Room's invalidation tracker is
         * per-instance, so a message persisted through one instance never
         * triggers the Flow observers reading through another.
         */
        fun instance(context: Context): TxxTDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        /**
         * Builds a [TxxTDatabase] instance. Callers own the returned instance's
         * lifecycle; production code goes through [instance] instead.
         */
        fun build(context: Context): TxxTDatabase =
            Room.databaseBuilder(context, TxxTDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()

        /**
         * v1 -> v2: adds the `sent` column to `messages` (T2 pending-send flag).
         * Existing rows are treated as already sent (`1`); new outgoing messages
         * persist `0` until transmitted.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN sent INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}