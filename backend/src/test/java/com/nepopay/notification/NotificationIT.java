package com.nepopay.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nepopay.AbstractIntegrationTest;
import com.nepopay.TestUsers;
import com.nepopay.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class NotificationIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void welcomeNotificationCanBeMarkedReadButOnlyByItsOwner() throws Exception {
        TestUsers users = new TestUsers(mvc);
        TestUser a = users.register();
        TestUser other = users.register();

        String body = mvc.perform(get("/api/v1/notifications").header("Authorization", a.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("WELCOME"))
                .andExpect(jsonPath("$.data.content[0].read").value(false))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.data.content[0].id");

        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", a.auth()))
                .andExpect(jsonPath("$.data.unread").value(1));

        // Someone else's notification id looks like a missing one.
        mvc.perform(post("/api/v1/notifications/" + id + "/read").header("Authorization", other.auth()))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/notifications/" + id + "/read").header("Authorization", a.auth()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", a.auth()))
                .andExpect(jsonPath("$.data.unread").value(0));

        mvc.perform(post("/api/v1/notifications/not-a-uuid/read").header("Authorization", a.auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void readAllMarksEverythingRead() throws Exception {
        TestUser a = new TestUsers(mvc).register();
        mvc.perform(post("/api/v1/notifications/read-all").header("Authorization", a.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(1));
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", a.auth()))
                .andExpect(jsonPath("$.data.unread").value(0));
    }
}
