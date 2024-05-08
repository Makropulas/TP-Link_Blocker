package ru.dmalomoshin.tplink_blocker.exception_handling;


import ru.dmalomoshin.tplink_blocker.domain.Device;

public class DeviceAlreadyBlockedException extends RuntimeException {
    public DeviceAlreadyBlockedException(Device device) {
        super("Device '" + device.getName() + " " + device.getMacAddress() + "' has already been blocked");
    }
}
