package ru.dmalomoshin.tplink_blocker.services;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import ru.dmalomoshin.tplink_blocker.domain.Device;
import ru.dmalomoshin.tplink_blocker.exception_handling.DeviceAlreadyBlockedException;
import ru.dmalomoshin.tplink_blocker.exception_handling.DeviceNotBlockedException;
import ru.dmalomoshin.tplink_blocker.exception_handling.NoSuchDeviceException;
import ru.dmalomoshin.tplink_blocker.repository.DeviceRepository;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Service
@AllArgsConstructor
public class DeviceService {
    /**
     * Захардкоженый токен авторизации
     */
    private final String authToken = "Authorization=Basic%20TWFrcm9wdWxhczo2MWY0M2QzNzExZDliOWJkOTIxNzQ5MTYxOTBkNjRjYQ%3D%3";

    /**
     * IP-адрес для подключения к роутеру
     */
    private final String routerIP = "http://192.168.0.1/";

    private final DeviceRepository deviceRepository;


    //region CRUD-operations
    public List<Device> getListDevicesFromDatabase() {
        return deviceRepository.findAll();
    }

    public Device addDeviceInDatabase(Device device) {
        return deviceRepository.save(device);
    }

    public Device getDeviceFromDatabase(String macAddress) {
        return deviceRepository.findByMacAddress(macAddress)
                .orElseThrow(() -> new NoSuchDeviceException(macAddress));
    }

    public Device updateDeviceInDatabase(Device updatedDevice) {

        int id = getDeviceFromDatabase(updatedDevice.getMacAddress()).getId();

        updatedDevice.setId(id);

        return deviceRepository.save(updatedDevice);
    }

    public void deleteDeviceFromDatabase(Device device) {
        deviceRepository.deleteByMacAddress(device.getMacAddress());
    }
    //endregion


    /**
     * Получить список подключенных устройств
     *
     * @param sessionId ID сессии
     * @return Список подключенных устройств
     */
    public List<Device> getListConnectedDevices(String sessionId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(routerIP + sessionId + "/userRpm/AssignedIpAddrListRpm.htm"))
                    .header(HttpHeaders.REFERER, routerIP + sessionId + "/userRpm/AssignedIpAddrListRpm.htm")
                    .header(HttpHeaders.COOKIE, authToken)
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            return getListDevicesFromResponse(response.body());

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Получить список устройств из HTTP Response роутера
     *
     * @param response HTTP Response
     * @return Распарсенный список устройств
     */
    private List<Device> getListDevicesFromResponse(String response) {

        String[] subresponse = getSubresponseFromResponse(response)
                .replaceAll("\"", "")
                .replaceAll(" ", "")
                .replaceAll("\n", "")
                .trim()
                .split(",");

        List<Device> devicesList = new ArrayList<>();
        for (int i = 0; i < subresponse.length; i += 4) {
            Device device = new Device();
            device.setName(subresponse[i]);
            device.setMacAddress(subresponse[i + 1]);
            devicesList.add(device);
        }

        return devicesList;
    }

    /**
     * Получить подстроку из HTTP Response
     *
     * @param response HTTP Response
     * @return Подстрока из HTTP Response
     */
    private String getSubresponseFromResponse(String response) {
        String startSubstring = "(\n";
        String endSubstring = "0,0";

        int firstIndex = response.indexOf(startSubstring) + startSubstring.length();
        int lastIndex = response.indexOf(endSubstring);

        return response.substring(firstIndex, lastIndex);
    }

    /**
     * Заблокировать устройство
     *
     * @param sessionId ID сессии
     * @param device Блокируемое устройство
     */
    public void blockDevice(String sessionId, Device device) {

        device.setIndexHosts(createHost(sessionId, device));
        device.setIndexRules(createRule(sessionId, device));
        device.setState(false);

        updateDeviceInDatabase(device);
    }

    /**
     * Создание имени для Хоста или Правила
     *
     * @param start Указать "Host" или "Rule"
     * @param name Имя устройства
     * @return Сгенерированное имя
     */
    private String giveNameForHostOrRule(String start, String name) {
        return String.format("%s_%.19s", start, name);
    }

    /**
     * Создать Хост
     *
     * @param sessionId ID сессии
     * @param device Переданное утройство
     * @return Индекс хоста в списке хостов
     */
    private int createHost(String sessionId, Device device) {
        String hostName = giveNameForHostOrRule("Host", device.getName());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(routerIP + sessionId + "/userRpm/AccessCtrlHostsListsRpm.htm?" +
                            "addr_type=0&hosts_lists_name=" + hostName + "&src_ip_start=&src_ip_end=&" +
                            "mac_addr=" + device.getMacAddress() + "&Changed=0&SelIndex=0&fromAdd=0&Page=1&" +
                            "Save=%D0%A1%D0%BE%D1%85%D1%80%D0%B0%D0%BD%D0%B8%D1%82%D1%8C"))
                    .header(HttpHeaders.REFERER, routerIP + sessionId + "/userRpm/AssignedIpAddrListRpm.htm")
                    .header(HttpHeaders.COOKIE, authToken)
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.body().contains("errCode")) {
                throw new DeviceAlreadyBlockedException(device);
            }

            return getIndexFromResponse(response.body());

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Создать правило
     *
     * @param sessionId ID сессии
     * @param device Переданное устройство
     * @return Индекс правила в списке правил
     */
    private int createRule(String sessionId, Device device) {
        String ruleName = giveNameForHostOrRule("Rule", device.getName());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(routerIP + sessionId + "/userRpm/AccessCtrlAccessRulesRpm.htm?" +
                            "rule_name=" + ruleName + "&hosts_lists=" + device.getIndexHosts() +
                            "&targets_lists=255&scheds_lists=255&enable=1&Changed=0&SelIndex=0&Page=1" +
                            "&Save=%D0%A1%D0%BE%D1%85%D1%80%D0%B0%D0%BD%D0%B8%D1%82%D1%8C"))
                    .header(HttpHeaders.REFERER, routerIP + sessionId +
                            "/userRpm/AccessCtrlAccessRulesRpm.htm?Add=Add&Page=1")
                    .header(HttpHeaders.COOKIE, authToken)
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.body().contains("errCode")) {
                throw new DeviceAlreadyBlockedException(device);
            }

            return getIndexFromResponse(response.body());

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Получить индекс из HTTP Response
     *
     * @param response HTTP Response
     * @return Индекс
     */
    private int getIndexFromResponse(String response) {

        String[] subresponse = getSubresponseFromResponse(response)
                .trim()
                .split("\n");

        return subresponse.length - 1;
    }

    /**
     * Получить HTTP Response содержащий заблокированные устройства
     *
     * @param sessionId ID сессии
     * @return HTTP Response
     */
    private String getResponseWithBlockedDevices(String sessionId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(routerIP + sessionId + "/userRpm/AccessCtrlAccessRulesRpm.htm"))
                    .header(HttpHeaders.REFERER, routerIP + sessionId + "/userRpm/MenuRpm.htm")
                    .header(HttpHeaders.COOKIE, authToken)
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            return getSubresponseFromResponse(response.body());

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Проверка на наличие устройства в HTTP Response
     *
     * @param device Переданное устройство
     * @param response HTTP Response
     * @return Логическое выражение
     */
    private boolean isBlocked(Device device, String response) {

        return response.contains(giveNameForHostOrRule("Rule", device.getName()));
    }

    /**
     * Получить список заблокированных устройств
     *
     * @param sessionId ID сессии
     * @param savedDevices Список сохраненных в базе устройств
     * @return Список заблокированных устройств
     */
    public List<Device> getListBlockedDevices(String sessionId, List<Device> savedDevices) {

        String response = getResponseWithBlockedDevices(sessionId);

        for (Device device : savedDevices) {
            if (isBlocked(device, response)) {
                device.setState(false);
                updateDeviceInDatabase(device);
            } else {
                if (!device.isState()) {
                    device.setIndexRules(-1);
                    device.setIndexHosts(-1);
                    device.setState(true);
                    updateDeviceInDatabase(device);
                }
            }
        }

        return savedDevices.stream()
                .filter(device -> !device.isState())
                .toList();
    }

    /**
     * Разблокировать устройство
     *
     * @param sessionId ID сессии
     * @param device Переданное устройство
     */
    public void unblockDevice(String sessionId, Device device) {

        device = getDeviceFromDatabase(device.getMacAddress());
        String response = getResponseWithBlockedDevices(sessionId);


        if (isBlocked(device, response)) {

            int hostIndex = device.getIndexHosts();
            int ruleIndex = device.getIndexRules();

            device.setIndexRules(deleteRule(sessionId, device));
            device.setIndexHosts(deleteHost(sessionId, device));
            device.setState(true);

            updateDeviceInDatabase(device);

            recalculateIndexes(hostIndex, ruleIndex);

        } else
            throw new DeviceNotBlockedException(device);
    }

    /**
     * Удалить Правило
     *
     * @param sessionId ID сессии
     * @param device Переданное устройство
     * @return -1 (отрицательный индекс)
     */
    private int deleteRule(String sessionId, Device device) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(routerIP + sessionId + "/userRpm/AccessCtrlAccessRulesRpm.htm?" +
                            "Del=" + device.getIndexRules() + "&Page=1"))
                    .header(HttpHeaders.REFERER, routerIP + sessionId + "/userRpm/AccessCtrlAccessRulesRpm.htm")
                    .header(HttpHeaders.COOKIE, authToken)
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.body().contains("errCode")) {
                throw new DeviceNotBlockedException(device);
            }

            return -1;

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Удалить Хост
     *
     * @param sessionId ID сессии
     * @param device Переданное устройство
     * @return -1 (отрицательный индекс)
     */
    private int deleteHost(String sessionId, Device device) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(routerIP + sessionId + "/userRpm/AccessCtrlHostsListsRpm.htm?" +
                            "Del=" + device.getIndexHosts() + "&Page=1"))
                    .header(HttpHeaders.REFERER, routerIP + sessionId + "/userRpm/AccessCtrlHostsListsRpm.htm")
                    .header(HttpHeaders.COOKIE, authToken)
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.body().contains("errCode")) {
                throw new DeviceNotBlockedException(device);
            }

            return -1;

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Пересчитать индексы устройств после разблокировки
     *
     * @param hostIndex Индекс хоста разблокированного устройства
     * @param ruleIndex Индекс правила разблокированного устройства
     */
    private void recalculateIndexes(int hostIndex, int ruleIndex) {
        List<Device> devices = getListDevicesFromDatabase();

        for (Device device : devices) {
            int deviceIndexHosts = device.getIndexHosts();
            int deviceIndexRules = device.getIndexRules();
            boolean flag = false;

            if (deviceIndexHosts > hostIndex) {
                device.setIndexHosts(--deviceIndexHosts);
                flag = true;
            }
            if (deviceIndexRules > ruleIndex) {
                device.setIndexRules(--deviceIndexRules);
                flag = true;
            }

            if (flag) {
                deviceRepository.save(device);
            }
        }
    }

    /**
     * Подключение к роутеру
     *
     * @return ID сессии
     */
    public String login() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(routerIP + "userRpm/LoginRpm.htm?Save=Save"))
                    .header(HttpHeaders.REFERER, routerIP)
                    .header(HttpHeaders.COOKIE, authToken)
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return getSessionFromResponse(response.body());
            } else
                return "";

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Получить ID сессии из HTTP Response
     *
     * @param response HTTP Response
     * @return ID сессии
     */
    private String getSessionFromResponse(String response) {
        int firstIndex = response.indexOf(routerIP) + routerIP.length();
        int lastIndex = response.indexOf("/userRpm");

        return response.substring(firstIndex, lastIndex);
    }

    /**
     * Отключение от роутера
     *
     * @param sessionId ID сессии
     * @return Информационное сообщение
     */
    public String logout(String sessionId) {
        try {
            HttpRequest firstRequest = HttpRequest.newBuilder()
                    .uri(URI.create(routerIP + sessionId + "/userRpm/LogoutRpm.htm"))
                    .header(HttpHeaders.REFERER, routerIP + sessionId + "/userRpm/MenuRpm.htm")
                    .header(HttpHeaders.COOKIE, authToken)
                    .GET()
                    .build();

            HttpResponse<String> firstResponse = HttpClient.newHttpClient()
                    .send(firstRequest, HttpResponse.BodyHandlers.ofString());

            HttpRequest secondRequest = HttpRequest.newBuilder()
                    .uri(URI.create(routerIP))
                    .header(HttpHeaders.REFERER, routerIP + sessionId + "/userRpm/LogoutRpm.htm")
                    .GET()
                    .build();

            HttpResponse<String> secondResponse = HttpClient.newHttpClient()
                    .send(secondRequest, HttpResponse.BodyHandlers.ofString());

            if (firstResponse.statusCode() == 200 && secondResponse.statusCode() == 200) {
                return "Logged out successfully";
            } else
                return "Logged out failed";

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
