package com.example.api_smart_home

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api")
class DeviceController(private val resolver: JwtUserResolver, private val service: DeviceService) {
    @GetMapping("/groups")
    fun groups(@RequestHeader("Authorization", required = false) authorization: String?) = service.listGroups(userId(authorization))

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    fun createGroup(@RequestHeader("Authorization", required = false) authorization: String?, @Valid @RequestBody request: CreateGroupRequest) =
        service.createGroup(userId(authorization), request.name)

    @DeleteMapping("/groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteGroup(@RequestHeader("Authorization", required = false) authorization: String?, @PathVariable groupId: UUID) =
        service.deleteGroup(userId(authorization), groupId)

    @GetMapping("/devices")
    fun devices(@RequestHeader("Authorization", required = false) authorization: String?, @RequestParam(required = false) groupId: UUID?) =
        service.listDevices(userId(authorization), groupId)

    @PostMapping("/groups/{groupId}/devices")
    @ResponseStatus(HttpStatus.CREATED)
    fun addDevice(@RequestHeader("Authorization", required = false) authorization: String?, @PathVariable groupId: UUID, @Valid @RequestBody request: CreateDeviceRequest) =
        service.addDevice(userId(authorization), groupId, request.name, request.homieDeviceId)

    @DeleteMapping("/devices/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDevice(@RequestHeader("Authorization", required = false) authorization: String?, @PathVariable deviceId: UUID) =
        service.deleteDevice(userId(authorization), deviceId)

    @PostMapping("/devices/{deviceId}/commands")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun command(@RequestHeader("Authorization", required = false) authorization: String?, @PathVariable deviceId: UUID, @Valid @RequestBody request: CommandRequest) {
        service.publishCommand(userId(authorization), deviceId, request.property, request.value)
    }

    private fun userId(authorization: String?): UUID = try { UUID.fromString(resolver.userId(authorization)) } catch (_: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user id in token")
    }
}

data class CreateGroupRequest(@field:NotBlank @field:Size(max = 120) val name: String)
data class CreateDeviceRequest(@field:NotBlank @field:Size(max = 120) val name: String, @field:NotBlank @field:Pattern(regexp = "[A-Za-z0-9_-]{1,64}") val homieDeviceId: String)
