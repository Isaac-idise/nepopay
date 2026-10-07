package com.nepopay.transfer;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nepopay.AbstractIntegrationTest;
import com.nepopay.TestUsers;
import com.nepopay.TestUsers.TestUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Daily limit lowered to N150,000 so it can be reached with a N100,000 starting balance. */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"nepopay.limits.daily-kobo=15000000", "nepopay.fraud.enabled=false"})
class TransferLimitsIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    private ResultActions send(TestUser from, String to, long kobo) throws Exception {
        return mvc.perform(post("/api/v1/transfers")
                .header("Authorization", from.auth())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientUsername\":\"%s\",\"amountKobo\":%d,\"pin\":\"1234\"}".formatted(to, kobo)));
    }

    @Test
    void dailyLimitCountsEarlierTransfersAndIsCheckedBeforeBalance() throws Exception {
        TestUsers users = new TestUsers(mvc);
        TestUser a = users.register();
        TestUser b = users.register();

        send(a, b.username(), 10_000_000).andExpect(status().isCreated());              // N100,000 sent; balance now 0

        send(a, b.username(), 6_000_000)                                                 // 100k + 60k > 150k
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message", containsString("₦50,000.00 left today")));

        send(a, b.username(), 5_000_000)                                                 // exactly at the limit: passes it,
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_BALANCE"));       // then fails on the empty balance
    }
}
