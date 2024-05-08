package ru.dmalomoshin.tplink_blocker.controllers;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dmalomoshin.tplink_blocker.domain.Device;
import ru.dmalomoshin.tplink_blocker.services.DeviceService;

import java.util.List;


@RestController
@AllArgsConstructor
@RequestMapping("/tp-link")
public class DeviceController {

    private DeviceService deviceService;

    @GetMapping()
    public ResponseEntity<?> getConnectedDevices() {
        List<Device> response = deviceService.getConnectedDevices();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<?> addDeviceInDatabase(@RequestBody Device deviceToAdd) {
        Device device = deviceService.addDeviceInDatabase(deviceToAdd);
        return new ResponseEntity<>(device, HttpStatus.CREATED);
    }

    @GetMapping("/db")
    public ResponseEntity<?> getDevicesFromDatabase() {
        List<Device> response = deviceService.getDevicesFromDatabase();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

//    @GetMapping("/block")
//    public ResponseEntity<?> blockDevice() {
//        Device device = deviceService.blockDevice();
//        return new ResponseEntity<>(device, HttpStatus.CREATED);
//    }
//
//    @GetMapping("/unblock")
//    public ResponseEntity<?> unblockDevice() {
//        Device device = deviceService.unblockDevice();
//        return new ResponseEntity<>(device, HttpStatus.OK);
//    }


}
