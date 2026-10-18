package com.irhunter.core.database

import android.content.Context
import androidx.room.*
import com.irhunter.core.model.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "profiles")
data class ProfileRow(@PrimaryKey val id: String, val payload: String, val summary: String = "")

@Dao
interface ProfileDao {
    @Query("SELECT id, CASE WHEN summary = '' THEN payload ELSE summary END AS payload, '' AS summary FROM profiles ORDER BY id") fun observe(): Flow<List<ProfileRow>>
    @Query("SELECT * FROM profiles ORDER BY id") suspend fun all(): List<ProfileRow>
    @Query("SELECT * FROM profiles WHERE id = :id") suspend fun get(id: String): ProfileRow?
    @Query("SELECT id FROM profiles") suspend fun ids(): List<String>
    @Upsert suspend fun put(row: ProfileRow)
    @Upsert suspend fun putAll(rows: List<ProfileRow>)
    @Query("DELETE FROM profiles WHERE id = :id") suspend fun delete(id: String)
}

@Database(entities = [ProfileRow::class], version = 2, exportSchema = true)
abstract class RemoteDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    companion object {
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN summary TEXT NOT NULL DEFAULT ''")
            }
        }
        fun open(context: Context) = Room.databaseBuilder(context, RemoteDatabase::class.java, "ir-hunter.db")
            .addMigrations(MIGRATION_1_2).build()
    }
}

object ProfileJson {
    fun encode(p: RemoteProfile): ProfileRow {
        val commands = JSONArray()
        p.commands.forEach { c ->
            val j = JSONObject().put("name", c.name).put("confirmedOff", c.confirmedOff).put("hz", c.signal.carrierHz)
            when (val s = c.signal) {
                is IrSignal.Raw -> j.put("raw", JSONArray(s.timings.toList()))
                is IrSignal.Protocol -> j.put("protocol", s.protocol.name).put("address", s.address)
                    .put("command", s.command).put("repeat", s.repeat)
            }
            commands.put(j)
        }
        fun json(original: String, selected: List<RemoteCommand>) = JSONObject().put("name", p.name).put("category", p.category.name)
            .put("brand", p.brand).put("model", p.model).put("source", p.source).put("license", p.license)
            .put("original", original).put("saved", p.saved).put("commandCount", p.commandCount)
            .put("commands", JSONArray().also { out -> selected.forEach { command ->
                val index = p.commands.indexOf(command); if (index >= 0) out.put(commands.get(index))
            } }).toString()
        val summaryCommands = p.commands.filter { it.isOff || it.isToggle }
        return ProfileRow(p.id, json(p.originalText, p.commands), json("", summaryCommands))
    }
    fun decode(row: ProfileRow): RemoteProfile {
        val j = JSONObject(row.payload)
        val a = j.getJSONArray("commands")
        val commands = (0 until a.length()).map { i ->
            val c = a.getJSONObject(i)
            val signal = if (c.has("raw")) {
                val r = c.getJSONArray("raw")
                IrSignal.Raw(IntArray(r.length()) { r.getInt(it) }, c.getInt("hz"))
            } else IrSignal.Protocol(IrProtocol.valueOf(c.getString("protocol")), c.getLong("address"),
                c.getLong("command"), c.getInt("repeat"), c.getInt("hz"))
            RemoteCommand(c.getString("name"), signal, c.optBoolean("confirmedOff"))
        }
        return RemoteProfile(row.id, j.getString("name"), DeviceCategory.valueOf(j.getString("category")),
            j.getString("brand"), j.getString("model"), j.getString("source"), j.getString("license"),
            j.getString("original"), commands, j.getBoolean("saved"), j.optInt("commandCount", commands.size))
    }
}
