package ru.dmalomoshin.tplink_blocker.exception_handling;


import ru.dmalomoshin.tplink_blocker.domain.Device;

public class DeviceNotBlockedException extends RuntimeException {
    public DeviceNotBlockedException(Device device) {
        super("Device '" + device.getName() + " " + device.getMacAddress() + "' was not blocked");
    }
}
