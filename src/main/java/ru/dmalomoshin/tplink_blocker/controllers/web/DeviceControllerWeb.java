package ru.dmalomoshin.tplink_blocker.controllers.web;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.dmalomoshin.tplink_blocker.domain.Device;
import ru.dmalomoshin.tplink_blocker.services.DeviceService;

import java.util.ArrayList;
import java.util.List;

/**
 * WEB-контроллер.
 * Основная страница <a href="http://localhost:8080/tp-link">http://localhost:8080/tp-link</a>
 */
@Controller
@AllArgsConstructor
@RequestMapping("/tp-link")
public class DeviceControllerWeb {

    private final DeviceService deviceService;

    /**
     * Получить главную страницу.
     * <p>
     * На странице отображается два поля:
     * 1. Подключенные в данный момент устройства без учёта заблокированных
     * 2. Заблокированные устройства
     *
     * @param model взаимодействие с Thymeleaf
     * @return главная страница
     */
    @GetMapping()
    public String getConnectedDevices(Model model) {
        String sessionId = deviceService.login();

        List<Device> clientsDHCP = deviceService.getListClientsDHCP(sessionId);

        List<Device> connectedDevices = new ArrayList<>();
        connectedDevices.addAll(deviceService.getListConnectedDevices2and4Ghz(sessionId));
        connectedDevices.addAll(deviceService.getListConnectedDevices5Ghz(sessionId));

        List<Device> savedDevices = deviceService.getListDevicesFromDatabase();
        List<Device> blockedDevices = deviceService.getListBlockedDevices(sessionId, savedDevices);

        clientsDHCP.stream()
                .filter(device -> !savedDevices.contains(device))
                .forEach(deviceService::addDeviceInDatabase);

        connectedDevices.removeAll(blockedDevices);

        model.addAttribute("connectedDevices", connectedDevices);
        model.addAttribute("blockedDevices", blockedDevices);

        deviceService.logout(sessionId);
        return "home";
    }

    /**
     * Заблокировать устройство
     *
     * @param device переданное через кнопку устройство
     * @return возврат на главную страницу
     */
    @PostMapping("/block")
    public String blockDevice(Device device) {
        String sessionId = deviceService.login();

        deviceService.blockDevice(sessionId, device);

        deviceService.logout(sessionId);
        return "redirect:/tp-link";
    }

    /**
     * Разблокировать устройство
     *
     * @param device переданное через кнопку устройство
     * @return возврат на главную страницу
     */
    @PostMapping("/unblock")
    public String unblockDevice(Device device) {
        String sessionId = deviceService.login();

        deviceService.unblockDevice(sessionId, device);

        deviceService.logout(sessionId);
        return "redirect:/tp-link";
    }

    /**
     * Показать сохраненные в базе устройства
     *
     * @param model взаимодействие с Thymeleaf
     * @return страница с сохраненными в базе устройствами
     */
    @GetMapping("/saved")
    public String getDevicesFromDatabase(Model model) {

        List<Device> savedDevices = deviceService.getListDevicesFromDatabase();
        model.addAttribute("savedDevices", savedDevices);

        return "saved";
    }

}
