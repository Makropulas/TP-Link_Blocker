package ru.dmalomoshin.tplink_blocker.controllers.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
class DeviceControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getConnectedDevices() throws Exception {
        mockMvc.perform(get("/tp-link").contentType(MediaType.APPLICATION_JSON))
                .andReturn();
    }

}