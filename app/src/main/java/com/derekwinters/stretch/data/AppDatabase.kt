package com.derekwinters.stretch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.derekwinters.stretch.goals.GoalHistory
import java.time.LocalDate
import java.time.ZoneId

@Database(
    entities = [
        Stretch::class,
        Schedule::class,
        Reminder::class,
        ReminderStretchCrossRef::class,
        SkippedDate::class,
        Goal::class,
        Completion::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stretchDao(): StretchDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun skipDao(): SkipDao
    abstract fun goalDao(): GoalDao
    abstract fun completionDao(): CompletionDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "stretch.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(SeedCallback)
                .build()
    }

    /**
     * GOAL-007: v1 -> v2 keeps every row. New `schedules` columns get defaults that make each
     * existing schedule a set-times schedule; `goals` and `completions` are created exactly as
     * Room would create them (column order, affinities, foreign keys and index names), so Room's
     * post-migration schema validation passes. Default literals must match the entities'
     * `@ColumnInfo(defaultValue)`.
     */
    object MIGRATION_1_2 : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `schedules` ADD COLUMN `mode` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `schedules` ADD COLUMN `windowStartMinute` INTEGER NOT NULL DEFAULT 480")
            db.execSQL("ALTER TABLE `schedules` ADD COLUMN `windowEndMinute` INTEGER NOT NULL DEFAULT 1020")
            db.execSQL("ALTER TABLE `schedules` ADD COLUMN `intervalMinutes` INTEGER NOT NULL DEFAULT 60")
            db.execSQL("ALTER TABLE `schedules` ADD COLUMN `minutePastHour` INTEGER NOT NULL DEFAULT 0")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `goals` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`stretchId` INTEGER NOT NULL, " +
                    "`timesPerDay` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`stretchId`) REFERENCES `stretches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_goals_stretchId` ON `goals` (`stretchId`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `completions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`stretchId` INTEGER NOT NULL, " +
                    "`completedAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`stretchId`) REFERENCES `stretches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_completions_stretchId` ON `completions` (`stretchId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_completions_completedAt` ON `completions` (`completedAt`)")
        }
    }

    /**
     * GOAL-010: v2 -> v3 makes goals versioned (GOAL-008). SQLite cannot add NOT NULL columns
     * without a default or turn a unique index into a plain one, so the table is rebuilt: create
     * `goals_new` exactly as Room would create the v3 `goals` table, copy every goal across as a
     * current row (same id, stretch and count) in force from [GoalHistory.migratedStartDay], drop
     * the old table (and with it the unique index), rename, and create the plain
     * `index_goals_stretchId`. Nothing references `goals`, so dropping it touches no other table.
     */
    object MIGRATION_2_3 : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val earliest: Long? = db.query("SELECT MIN(`completedAt`) FROM `completions`").use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
            }
            val fromDay = GoalHistory.migratedStartDay(earliest, LocalDate.now(), ZoneId.systemDefault())

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `goals_new` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`stretchId` INTEGER NOT NULL, " +
                    "`timesPerDay` INTEGER NOT NULL, " +
                    "`effectiveFromEpochDay` INTEGER NOT NULL, " +
                    "`effectiveToEpochDay` INTEGER, " +
                    "FOREIGN KEY(`stretchId`) REFERENCES `stretches`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "INSERT INTO `goals_new` (`id`, `stretchId`, `timesPerDay`, `effectiveFromEpochDay`, `effectiveToEpochDay`) " +
                    "SELECT `id`, `stretchId`, `timesPerDay`, ?, NULL FROM `goals`",
                arrayOf<Any?>(fromDay),
            )
            db.execSQL("DROP TABLE `goals`")
            db.execSQL("ALTER TABLE `goals_new` RENAME TO `goals`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_stretchId` ON `goals` (`stretchId`)")
        }
    }

    /** Seeds the stretch library the first time the database is created (LIB-002). */
    private object SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            for (stretch in DefaultStretches.ALL) {
                db.execSQL(
                    "INSERT INTO stretches (name, description, durationSeconds) VALUES (?, ?, ?)",
                    arrayOf<Any?>(stretch.name, stretch.description, stretch.durationSeconds),
                )
            }
        }
    }
}

object DefaultStretches {
    val ALL: List<Stretch> = listOf(
        Stretch(
            name = "Neck rolls",
            description = "Drop your chin to your chest and slowly roll your head side to side. Keep shoulders relaxed.",
            durationSeconds = 30,
        ),
        Stretch(
            name = "Shoulder shrugs",
            description = "Lift both shoulders toward your ears, hold a moment, then let them drop. Repeat 10 times.",
            durationSeconds = 30,
        ),
        Stretch(
            name = "Chest opener",
            description = "Clasp your hands behind your back, straighten your arms and gently lift while opening your chest.",
            durationSeconds = 30,
        ),
        Stretch(
            name = "Hamstring stretch",
            description = "With one heel forward and leg straight, hinge at the hips until you feel a stretch. Switch sides.",
            durationSeconds = 60,
        ),
        Stretch(
            name = "Hip flexor stretch",
            description = "Step into a lunge, lower the back knee and press the hips forward gently. Switch sides.",
            durationSeconds = 60,
        ),
        Stretch(
            name = "Wrist stretch",
            description = "Extend one arm, palm up, and gently pull the fingers back with the other hand. Then palm down. Switch.",
            durationSeconds = 30,
        ),
        Stretch(
            name = "Upper back stretch",
            description = "Clasp your hands in front, round your upper back and push your hands away from you.",
            durationSeconds = 30,
        ),
        Stretch(
            name = "Standing quad stretch",
            description = "Stand tall, pull one heel toward your glutes and keep the knees together. Switch sides.",
            durationSeconds = 60,
        ),
        Stretch(
            name = "Seated spinal twist",
            description = "Sit tall, place one hand on the opposite knee and gently rotate. Switch sides.",
            durationSeconds = 30,
        ),
        Stretch(
            name = "Calf stretch",
            description = "Hands on a wall, step one foot back with the heel down and lean forward. Switch sides.",
            durationSeconds = 60,
        ),
    )
}
