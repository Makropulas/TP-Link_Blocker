package ru.dmalomoshin.tplink_blocker.exception_handling;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class DeviceGlobalExceptionHandler {

    @ExceptionHandler
    public ResponseEntity<DeviceIncorrectData> handleException(NoSuchDeviceException exception) {
        DeviceIncorrectData incorrectData = new DeviceIncorrectData();
        incorrectData.setAttention(exception.getMessage());

        return new ResponseEntity<>(incorrectData, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler
    public ResponseEntity<DeviceIncorrectData> handleException(DeviceAlreadyBlockedException exception) {
        DeviceIncorrectData incorrectData = new DeviceIncorrectData();
        incorrectData.setAttention(exception.getMessage());

        return new ResponseEntity<>(incorrectData, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler
    public ResponseEntity<DeviceIncorrectData> handleException(DeviceNotBlockedException exception) {
        DeviceIncorrectData incorrectData = new DeviceIncorrectData();
        incorrectData.setAttention(exception.getMessage());

        return new ResponseEntity<>(incorrectData, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler
    public ResponseEntity<DeviceIncorrectData> handleException(Exception exception) {
        DeviceIncorrectData incorrectData = new DeviceIncorrectData();
        incorrectData.setAttention(exception.getMessage());

        return new ResponseEntity<>(incorrectData, HttpStatus.BAD_REQUEST);
    }

}
