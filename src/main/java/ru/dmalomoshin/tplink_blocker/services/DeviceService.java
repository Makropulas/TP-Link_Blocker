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

    private final String authToken = "Authorization=Basic%20TWFrcm9wdWxhczo2MWY0M2QzNzExZDliOWJkOTIxNzQ5MTYxOTBkNjRjYQ%3D%3";
    private final String routerIP = "http://192.168.0.1/";

    private final DeviceRepository deviceRepository;


    //region CRUD-operations
    public List<Device> getDevicesFromDatabase() {
        return deviceRepository.findAll();
    }

    public Device addDeviceInDatabase(Device device) {
        return deviceRepository.save(device);
    }

    public Device getDeviceFromDatabase(String macAddress) {
        return deviceRepository.findByMacAddress(macAddress)
                .orElseThrow(() -> new NoSuchDeviceException(macAddress));
    }

    public Device updateDeviceIndexesInDatabase(Device updatedDevice) {
        Device device = getDeviceFromDatabase(updatedDevice.getMacAddress());

        device.setIndexHosts(updatedDevice.getIndexHosts());
        device.setIndexRules(updatedDevice.getIndexRules());

        return deviceRepository.save(device);
    }

    public void deleteDeviceFromDatabase(Device device) {
        deviceRepository.deleteByMacAddress(device.getMacAddress());
    }
    //endregion


    public List<Device> getConnectedDevices() {
        String sessionId = login();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(routerIP + sessionId + "/userRpm/AssignedIpAddrListRpm.htm"))
                    .header(HttpHeaders.REFERER, routerIP + sessionId + "/userRpm/AssignedIpAddrListRpm.htm")
                    .header(HttpHeaders.COOKIE, authToken)
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            String message = logout(sessionId);
            System.out.println(message);

            return getListDevicesFromResponse(response.body());

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Device> getListDevicesFromResponse(String response) {
        int firstIndex = response.indexOf("(") + 1;
        int lastIndex = response.indexOf("0,0");

        String[] substring = response.substring(firstIndex, lastIndex)
                .replaceAll("\"", "")
                .replaceAll(" ", "")
                .replaceAll("\n", "")
                .trim()
                .split(",");

        List<Device> devicesList = new ArrayList<>();
        for (int i = 0; i < substring.length; i += 4) {
            Device device = new Device();
            device.setName(substring[i]);
            device.setMacAddress(substring[i + 1]);
            devicesList.add(device);
        }

        return devicesList;
    }

    public Device blockDevice(Device device) {
        String sessionId = login();

        createHost(sessionId, device);
        createRule(sessionId, device);

        String message = logout(sessionId);
        System.out.println(message);

        return device;
    }

    private void createHost(String sessionId, Device device) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(routerIP + sessionId + "/userRpm/AccessCtrlHostsListsRpm.htm?" +
                            "addr_type=0&hosts_lists_name=Host_" + device.getName() + "&src_ip_start=&src_ip_end=&" +
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

            device.setIndexHosts(getIndexFromResponse(response.body()));

            System.out.println("**  **  **  **  **  **  **  **  **  **  **  **  **  **  **");
            System.out.println("createHost: " + response.body()); //TODO дописать return индекса для перезаписи в БД
            System.out.println("**  **  **  **  **  **  **  **  **  **  **  **  **  **  **");

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void createRule(String sessionId, Device device) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(routerIP + sessionId + "/userRpm/AccessCtrlAccessRulesRpm.htm?" +
                            "rule_name=Rule_" + device.getName() + "&hosts_lists=" + device.getIndexHosts() +
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

            device.setIndexRules(getIndexFromResponse(response.body()));

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private int getIndexFromResponse(String response) {
        String startSubstring = "(\n";
        String endSubstring = "0,0";
        int firstIndex = response.indexOf(startSubstring) + startSubstring.length();
        int lastIndex = response.indexOf(endSubstring);

        String[] substring = response.substring(firstIndex, lastIndex)
                .trim()
                .split("\n");

        return substring.length - 1;
    }

    private boolean isBlocked(String sessionId, Device device) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(routerIP + sessionId + "/userRpm/AccessCtrlAccessRulesRpm.htm"))
                    .header(HttpHeaders.REFERER, routerIP + sessionId + "/userRpm/MenuRpm.htm")
                    .header(HttpHeaders.COOKIE, authToken)
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            return response.body().contains("Rule_" + device.getName());

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public Device unblockDevice(Device device) {
        String sessionId = login();

        if (isBlocked(sessionId, device)) {
            deleteRule(sessionId, device);
            deleteHost(sessionId, device);
        } else
            throw new DeviceNotBlockedException(device);

        String message = logout(sessionId);
        System.out.println(message);

        return device;
    }

    private void deleteRule(String sessionId, Device device) {
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

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteHost(String sessionId, Device device) {
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

            System.out.println("**  **  **  **  **  **  **  **  **  **  **  **  **  **  **");
            System.out.println("deleteHost: " + response.body()); //TODO дописать return индекса для перезаписи в БД
            System.out.println("**  **  **  **  **  **  **  **  **  **  **  **  **  **  **");

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private String login() {
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
                return null;

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private String getSessionFromResponse(String response) {
        int firstIndex = response.indexOf(routerIP) + routerIP.length();
        int lastIndex = response.indexOf("/userRpm");

        return response.substring(firstIndex, lastIndex);
    }

    private String logout(String sessionId) {
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
