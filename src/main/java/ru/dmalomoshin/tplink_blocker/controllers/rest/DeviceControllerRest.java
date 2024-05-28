package ru.dmalomoshin.tplink_blocker.controllers.rest;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dmalomoshin.tplink_blocker.domain.Device;
import ru.dmalomoshin.tplink_blocker.services.DeviceService;

import java.util.List;

/**
 * REST-контроллер с реализацией основных CRUD-операций
 */
@RestController
@AllArgsConstructor
@RequestMapping("/tp-link/rest")
public class DeviceControllerRest {

    private final DeviceService deviceService;

    @GetMapping()
    public ResponseEntity<?> getConnectedDevices() {
        String sessionId = deviceService.login();
        List<Device> response = deviceService.getListClientsDHCP(sessionId);
        deviceService.logout(sessionId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<?> addDeviceInDatabase(@RequestBody Device deviceToAdd) {
        Device device = deviceService.addDeviceInDatabase(deviceToAdd);
        return new ResponseEntity<>(device, HttpStatus.CREATED);
    }

    @GetMapping("/db")
    public ResponseEntity<?> getDevicesFromDatabase() {
        List<Device> response = deviceService.getListDevicesFromDatabase();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PutMapping("/upd")
    public ResponseEntity<Device> updateDevice(@RequestBody Device updatedDevice) {
        Device device = deviceService.updateDeviceInDatabase(updatedDevice);
        return ResponseEntity.ok(device);
    }

    @DeleteMapping("/del")
    public ResponseEntity<String> deleteTask(@RequestBody Device deletedDevice) {
        deviceService.deleteDeviceFromDatabase(deletedDevice);
        return ResponseEntity.ok("Device with MAC-address '" + deletedDevice.getMacAddress() + "' deleted successfully.");
    }

}
