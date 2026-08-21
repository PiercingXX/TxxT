package com.piercingxx.txxt.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    version = 1,
    exportSchema = true,
)
abstract class TxxTDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao

    abstract fun messageDao(): MessageDao

    companion object {
        const val NAME = "txxt.db"

        /**
         * Builds a [TxxTDatabase] instance. Callers own the returned instance's
         * lifecycle; use a singleton holder in production.
         */
        fun build(context: Context): TxxTDatabase =
            Room.databaseBuilder(context, TxxTDatabase::class.java, NAME)
                .build()
    }
}