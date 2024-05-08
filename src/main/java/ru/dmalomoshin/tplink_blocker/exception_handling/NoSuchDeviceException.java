package ru.dmalomoshin.tplink_blocker.exception_handling;


public class NoSuchDeviceException extends RuntimeException {
    public NoSuchDeviceException(String macAddress) {
        super("Device '" + macAddress + "' not found in database");
    }
}
