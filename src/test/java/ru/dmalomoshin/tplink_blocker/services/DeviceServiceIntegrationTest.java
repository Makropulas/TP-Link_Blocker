package ru.dmalomoshin.tplink_blocker.services;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.dmalomoshin.tplink_blocker.domain.Device;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DeviceServiceIntegrationTest {

    @Autowired
    private DeviceService deviceService;

    @Test
    void getListDevices_when_databaseAvailable() {
        List<Device> devices = deviceService.getListDevicesFromDatabase();

        assertNotNull(devices);
        assertFalse(devices.isEmpty());
    }

    @Test
    void getListConnectedDevices() {
        String sessionId = deviceService.login();
        List<Device> devices = deviceService.getListConnectedDevices(sessionId);
        deviceService.logout(sessionId);

        assertNotNull(devices);
        assertFalse(devices.isEmpty());
    }
}