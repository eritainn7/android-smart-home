package com.example.api_smart_home

import tools.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class DeviceService(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper, private val events: DeviceEventPublisher) {
    fun listGroups(ownerId: UUID) = jdbc.query(
        "SELECT id,name,created_at FROM device_groups WHERE owner_id=? ORDER BY created_at DESC",
        { rs, _ -> DeviceGroup(rs.getObject("id", UUID::class.java), rs.getString("name"), rs.getTimestamp("created_at").toInstant().toString()) }, ownerId
    )

    @Transactional
    fun createGroup(ownerId: UUID, name: String): DeviceGroup {
        val id = UUID.randomUUID()
        try {
            jdbc.update("INSERT INTO device_groups(id,owner_id,name) VALUES (?,?,?)", id, ownerId, name.trim())
        } catch (ex: org.springframework.dao.DuplicateKeyException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Группа с таким именем уже существует")
        }
        return listGroups(ownerId).first { it.id == id }
    }

    @Transactional
    fun deleteGroup(ownerId: UUID, groupId: UUID) {
        if (jdbc.update("DELETE FROM device_groups WHERE id=? AND owner_id=?", groupId, ownerId) == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Группа не найдена")
        }
    }

    fun listDevices(ownerId: UUID, groupId: UUID? = null): List<DeviceDto> {
        val sql = """
            SELECT d.id,d.group_id,d.name,d.homie_device_id,d.created_at,
                   COALESCE((SELECT jsonb_object_agg(p.property_name,p.property_value) FROM device_properties p WHERE p.device_id=d.id),'{}'::jsonb)::text AS properties
            FROM devices d JOIN device_groups g ON g.id=d.group_id
            WHERE g.owner_id=? ${if (groupId != null) "AND d.group_id=?" else ""}
            ORDER BY d.created_at DESC
        """.trimIndent()
        val rowMapper = org.springframework.jdbc.core.RowMapper { rs, _ ->
            DeviceDto(rs.getObject("id", UUID::class.java), rs.getObject("group_id", UUID::class.java), rs.getString("name"), rs.getString("homie_device_id"), mapper.readTree(rs.getString("properties")), rs.getTimestamp("created_at").toInstant().toString())
        }
        return if (groupId != null) jdbc.query(sql, rowMapper, ownerId, groupId) else jdbc.query(sql, rowMapper, ownerId)
    }

    @Transactional
    fun addDevice(ownerId: UUID, groupId: UUID, name: String, homieId: String): DeviceDto {
        ensureGroup(ownerId, groupId)
        val id = UUID.randomUUID()
        try {
            jdbc.update("INSERT INTO devices(id,group_id,name,homie_device_id) VALUES (?,?,?,?)", id, groupId, name.trim(), homieId.trim())
        } catch (ex: org.springframework.dao.DuplicateKeyException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Устройство с таким Homie ID уже зарегистрировано")
        }
        return listDevices(ownerId, groupId).first { it.id == id }
    }

    @Transactional
    fun deleteDevice(ownerId: UUID, deviceId: UUID) {
        if (jdbc.update("DELETE FROM devices d USING device_groups g WHERE d.group_id=g.id AND d.id=? AND g.owner_id=?", deviceId, ownerId) == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Устройство не найдено")
        }
    }

    fun publishCommand(ownerId: UUID, deviceId: UUID, property: String, value: String) {
        val homieId = jdbc.query("SELECT d.homie_device_id FROM devices d JOIN device_groups g ON g.id=d.group_id WHERE d.id=? AND g.owner_id=?", { rs, _ -> rs.getString(1) }, deviceId, ownerId)
            .firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Устройство не найдено")
        if (property != "target-temperature") throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only target-temperature is settable in the MVP")
        if (value.toDoubleOrNull()?.takeIf { it.isFinite() && it in -40.0..125.0 } == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Temperature target must be a finite value from -40 to 125")
        }
        events.publishCommand(homieId, property, value)
    }

    fun acceptTelemetry(homieId: String, property: String, rawValue: String) {
        if (!homieId.matches(Regex("[A-Za-z0-9_-]{1,64}")) || !property.matches(Regex("[A-Za-z0-9_-]{1,64}")) || rawValue.length > 256) return
        if (property == "temperature" && rawValue.toDoubleOrNull()?.takeIf { it.isFinite() && it in -100.0..200.0 } == null) return
        val device = jdbc.query("SELECT d.id,g.owner_id FROM devices d JOIN device_groups g ON g.id=d.group_id WHERE d.homie_device_id=?", { rs, _ -> rs.getObject("id", UUID::class.java) to rs.getObject("owner_id", UUID::class.java) }, homieId).firstOrNull() ?: return
        val timestamp = Instant.now()
        jdbc.update("""
            INSERT INTO device_properties(device_id,property_name,property_value,updated_at) VALUES (?,?,?,?)
            ON CONFLICT(device_id,property_name) DO UPDATE SET property_value=EXCLUDED.property_value,updated_at=EXCLUDED.updated_at
        """.trimIndent(), device.first, property, rawValue, java.sql.Timestamp.from(timestamp))
        events.publishTelemetry(device.second.toString(), mapOf("deviceId" to device.first.toString(), "homieDeviceId" to homieId, "property" to property, "value" to rawValue, "timestamp" to timestamp.toString()))
    }

    private fun ensureGroup(ownerId: UUID, groupId: UUID) {
        if (jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM device_groups WHERE id=? AND owner_id=?)", Boolean::class.java, groupId, ownerId) != true) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Группа не найдена")
        }
    }
}

data class DeviceGroup(val id: UUID, val name: String, val createdAt: String)
data class DeviceDto(val id: UUID, val groupId: UUID, val name: String, val homieDeviceId: String, val properties: tools.jackson.databind.JsonNode, val createdAt: String)
data class CommandRequest(@field:jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9_-]{1,64}") val property: String, @field:jakarta.validation.constraints.Size(max = 256) val value: String)
