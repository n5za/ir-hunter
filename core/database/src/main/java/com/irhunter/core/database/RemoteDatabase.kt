package com.irhunter.core.database

import android.content.Context
import androidx.room.*
import com.irhunter.core.model.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "profiles")
data class ProfileRow(@PrimaryKey val id: String, val payload: String)

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id") fun observe(): Flow<List<ProfileRow>>
    @Query("SELECT * FROM profiles ORDER BY id") suspend fun all(): List<ProfileRow>
    @Upsert suspend fun put(row: ProfileRow)
    @Query("DELETE FROM profiles WHERE id = :id") suspend fun delete(id: String)
}

@Database(entities = [ProfileRow::class], version = 1, exportSchema = true)
abstract class RemoteDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    companion object {
        fun open(context: Context) = Room.databaseBuilder(context, RemoteDatabase::class.java, "ir-hunter.db").build()
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
        return ProfileRow(p.id, JSONObject().put("name", p.name).put("category", p.category.name)
            .put("brand", p.brand).put("model", p.model).put("source", p.source).put("license", p.license)
            .put("original", p.originalText).put("saved", p.saved).put("commands", commands).toString())
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
            j.getString("original"), commands, j.getBoolean("saved"))
    }
}
