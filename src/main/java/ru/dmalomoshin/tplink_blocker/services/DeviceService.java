package ru.dmalomoshin.tplink_blocker.services;

import lombok.AllArgsConstructor;
import lombok.NonNull;
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
     * Сформировать HttpRequest и получить HttpResponse
     *
     * @param requestURI     URI HttpRequest
     * @param requestReferer Referer HttpRequest
     * @return HttpResponse
     */
    private HttpResponse<String> getHttpResponse(final String requestURI, @NonNull final String requestReferer) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestURI))
                    .header(HttpHeaders.REFERER, requestReferer)
                    .header(HttpHeaders.COOKIE, authToken)
                    .GET()
                    .build();

            return HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

        } catch (final IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Получить список клиентов DHCP
     *
     * @param sessionId ID сессии
     * @return список клиентов DHCP
     */
    public List<Device> getListClientsDHCP(final String sessionId) {

        String uri = routerIP + sessionId + "/userRpm/AssignedIpAddrListRpm.htm";
        String referer = routerIP + sessionId + "/userRpm/AssignedIpAddrListRpm.htm";

        HttpResponse<String> response = getHttpResponse(uri, referer);

        return getListDevicesFromResponse(response.body());
    }

    /**
     * Получить список подключенных устройств 2.4ГГц
     *
     * @param sessionId ID сессии
     * @return список подключенных устройств 2.4ГГц
     */
    public List<Device> getListConnectedDevices2and4Ghz(final String sessionId) {

        String uri = routerIP + sessionId + "/userRpm/WlanStationRpm.htm";
        String referer = routerIP + sessionId + "/userRpm/MenuRpm.htm";

        HttpResponse<String> response = getHttpResponse(uri, referer);

        return getListDevicesFromResponse(response.body(), "hostList");
    }

    /**
     * Получить список подключенных устройств 5ГГц
     *
     * @param sessionId ID сессии
     * @return список подключенных устройств 5ГГц
     */
    public List<Device> getListConnectedDevices5Ghz(final String sessionId) {

        String uri = routerIP + sessionId + "/userRpm/WlanStationRpm_5g.htm";
        String referer = routerIP + sessionId + "/userRpm/MenuRpm.htm";

        HttpResponse<String> response = getHttpResponse(uri, referer);

        return getListDevicesFromResponse(response.body(), "hostList");
    }

    /**
     * Получить список устройств из HTTP Response роутера
     *
     * @param response HTTP Response
     * @return распарсенный список устройств
     */
    private List<Device> getListDevicesFromResponse(@NonNull final String response) {

        String[] subResponse = getSubresponseFromResponse(response)
                .replaceAll("\"", "")
                .replaceAll(" ", "")
                .replaceAll("\n", "")
                .trim()
                .split(",");

        List<Device> devicesList = new ArrayList<>();
        for (int i = 0; i < subResponse.length; i += 4) {
            Device device = new Device();
            device.setName(subResponse[i]);
            device.setMacAddress(subResponse[i + 1]);
            devicesList.add(device);
        }

        return devicesList;
    }

    /**
     * Получить список устройств из HTTP Response роутера (2 аргумента)
     *
     * @param response HTTP Response
     * @param start    начало подзапроса
     * @return распарсенный список устройств
     */
    private List<Device> getListDevicesFromResponse(@NonNull final String response, String start) {

        int firstIndex = response.indexOf(start);
        String trimmedResponse = response.substring(firstIndex);

        String[] subresponse = getSubresponseFromResponse(trimmedResponse)
                .replaceAll("\"", "")
                .replaceAll(" ", "")
                .replaceAll("\n", "")
                .trim()
                .split(",");

        List<Device> devicesList = new ArrayList<>();
        for (int i = 0; i < subresponse.length; i += 4) {
            Device device = getDeviceFromDatabase(subresponse[i]);
            devicesList.add(device);
        }

        return devicesList;
    }

    /**
     * Получить подстроку из HTTP Response
     *
     * @param response HTTP Response
     * @return подстрока из HTTP Response
     */
    private String getSubresponseFromResponse(@NonNull final String response) {
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
     * @param device    блокируемое устройство
     */
    public void blockDevice(final String sessionId, @NonNull Device device) {

        device.setIndexHosts(createHost(sessionId, device));
        device.setIndexRules(createRule(sessionId, device));
        device.setState(false);

        updateDeviceInDatabase(device);
    }

    /**
     * Создание имени для Хоста или Правила
     *
     * @param start указать "Host" или "Rule"
     * @param name  имя устройства
     * @return сгенерированное имя
     */
    private String giveNameForHostOrRule(String start, String name) {
        return String.format("%s_%.19s", start, name);
    }

    /**
     * Создать Хост
     *
     * @param sessionId ID сессии
     * @param device    переданное утройство
     * @return индекс хоста в списке хостов
     * @throws DeviceAlreadyBlockedException Устройство уже заблокировано
     */
    private int createHost(final String sessionId, @NonNull Device device) {
        String hostName = giveNameForHostOrRule("Host", device.getName());

        String uri = routerIP + sessionId + "/userRpm/AccessCtrlHostsListsRpm.htm?" +
                "addr_type=0&hosts_lists_name=" + hostName + "&src_ip_start=&src_ip_end=&" +
                "mac_addr=" + device.getMacAddress() + "&Changed=0&SelIndex=0&fromAdd=0&Page=1&" +
                "Save=%D0%A1%D0%BE%D1%85%D1%80%D0%B0%D0%BD%D0%B8%D1%82%D1%8C";
        String referer = routerIP + sessionId + "/userRpm/AssignedIpAddrListRpm.htm";

        HttpResponse<String> response = getHttpResponse(uri, referer);

        if (response.body().contains("errCode")) {
            throw new DeviceAlreadyBlockedException(device);
        }

        return getIndexFromResponse(response.body());
    }

    /**
     * Создать правило
     *
     * @param sessionId ID сессии
     * @param device    переданное устройство
     * @return индекс правила в списке правил
     */
    private int createRule(final String sessionId, @NonNull Device device) {
        String ruleName = giveNameForHostOrRule("Rule", device.getName());

        String uri = routerIP + sessionId + "/userRpm/AccessCtrlAccessRulesRpm.htm?" +
                "rule_name=" + ruleName + "&hosts_lists=" + device.getIndexHosts() +
                "&targets_lists=255&scheds_lists=255&enable=1&Changed=0&SelIndex=0&Page=1" +
                "&Save=%D0%A1%D0%BE%D1%85%D1%80%D0%B0%D0%BD%D0%B8%D1%82%D1%8C";
        String referer = routerIP + sessionId + "/userRpm/AccessCtrlAccessRulesRpm.htm?Add=Add&Page=1";

        HttpResponse<String> response = getHttpResponse(uri, referer);

        if (response.body().contains("errCode")) {
            throw new DeviceAlreadyBlockedException(device);
        }

        return getIndexFromResponse(response.body());
    }

    /**
     * Получить индекс из HTTP Response
     *
     * @param response HTTP Response
     * @return индекс
     */
    private int getIndexFromResponse(@NonNull final String response) {

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
    private String getResponseWithBlockedDevices(final String sessionId) {

        String uri = routerIP + sessionId + "/userRpm/AccessCtrlAccessRulesRpm.htm";
        String referer = routerIP + sessionId + "/userRpm/MenuRpm.htm";

        HttpResponse<String> response = getHttpResponse(uri, referer);

        return getSubresponseFromResponse(response.body());
    }

    /**
     * Проверка на наличие устройства в HTTP Response
     *
     * @param device   переданное устройство
     * @param response HTTP Response
     * @return логическое выражение
     */
    private boolean isBlocked(@NonNull Device device, @NonNull final String response) {

        return response.contains(giveNameForHostOrRule("Rule", device.getName()));
    }

    /**
     * Получить список заблокированных устройств
     *
     * @param sessionId    ID сессии
     * @param savedDevices список сохраненных в базе устройств
     * @return список заблокированных устройств
     */
    public List<Device> getListBlockedDevices(final String sessionId, List<Device> savedDevices) {

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
     * @param device    переданное устройство
     */
    public void unblockDevice(final String sessionId, @NonNull Device device) {

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
     * @param device    переданное устройство
     * @return -1 (отрицательный индекс)
     */
    private int deleteRule(final String sessionId, @NonNull Device device) {

        String uri = routerIP + sessionId + "/userRpm/AccessCtrlAccessRulesRpm.htm?" +
                "Del=" + device.getIndexRules() + "&Page=1";
        String referer = routerIP + sessionId + "/userRpm/AccessCtrlAccessRulesRpm.htm";

        HttpResponse<String> response = getHttpResponse(uri, referer);

        if (response.body().contains("errCode")) {
            throw new DeviceNotBlockedException(device);
        }

        return -1;
    }

    /**
     * Удалить Хост
     *
     * @param sessionId ID сессии
     * @param device    переданное устройство
     * @return -1 (отрицательный индекс)
     */
    private int deleteHost(final String sessionId, @NonNull Device device) {

        String uri = routerIP + sessionId + "/userRpm/AccessCtrlHostsListsRpm.htm?" +
                "Del=" + device.getIndexHosts() + "&Page=1";
        String referer = routerIP + sessionId + "/userRpm/AccessCtrlHostsListsRpm.htm";

        HttpResponse<String> response = getHttpResponse(uri, referer);

        if (response.body().contains("errCode")) {
            throw new DeviceNotBlockedException(device);
        }

        return -1;
    }

    /**
     * Пересчитать индексы устройств после разблокировки
     *
     * @param hostIndex индекс хоста разблокированного устройства
     * @param ruleIndex индекс правила разблокированного устройства
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

        String uri = routerIP + "userRpm/LoginRpm.htm?Save=Save";

        HttpResponse<String> response = getHttpResponse(uri, routerIP);
        final String body = response.body();

        if (response.statusCode() == 200 && body != null)
            return getSessionFromResponse(body);
        else
            return "";
    }

    /**
     * Получить ID сессии из HTTP Response
     *
     * @param response HTTP Response
     * @return ID сессии
     */
    @NonNull
    private String getSessionFromResponse(@NonNull final String response) {
        int firstIndex = response.indexOf(routerIP) + routerIP.length();
        int lastIndex = response.indexOf("/userRpm");

        return response.substring(firstIndex, lastIndex);
    }

    /**
     * Отключение от роутера
     *
     * @param sessionId ID сессии
     * @return информационное сообщение
     */
    public String logout(final String sessionId) {

        String uri = routerIP + sessionId + "/userRpm/LogoutRpm.htm";
        String referer = routerIP + sessionId + "/userRpm/MenuRpm.htm";

        HttpResponse<String> firstResponse = getHttpResponse(uri, referer);

        referer = routerIP + sessionId + "/userRpm/LogoutRpm.htm";

        HttpResponse<String> secondResponse = getHttpResponse(routerIP, referer);

        if (firstResponse.statusCode() == 200 && secondResponse.statusCode() == 200) {
            return "Logged out successfully";
        } else
            return "Logged out failed";
    }

}
