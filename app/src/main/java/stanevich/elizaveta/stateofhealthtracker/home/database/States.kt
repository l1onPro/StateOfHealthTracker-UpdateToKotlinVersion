package stanevich.elizaveta.stateofhealthtracker.home.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "states_table")
data class States(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L,

    @ColumnInfo(name = "date")
    var date: Long = 0,

    @ColumnInfo(name = "mood")
    var mood: String = "",

    @ColumnInfo(name = "pill")
    var pill: Long = 0,

    @ColumnInfo(name = "dyskinesia")
    var dyskinesia: Long = 0
) {
    fun moodToInt(): Int {
        return when (mood) {
            "-" -> -1
            "+" -> 1
            else -> 0
        }
    }
}