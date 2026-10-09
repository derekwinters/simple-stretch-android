package com.derekwinters.stretch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Stretch::class,
        Schedule::class,
        Reminder::class,
        ReminderStretchCrossRef::class,
        SkippedDate::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stretchDao(): StretchDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun skipDao(): SkipDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "stretch.db")
                .addCallback(SeedCallback)
                .build()
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
