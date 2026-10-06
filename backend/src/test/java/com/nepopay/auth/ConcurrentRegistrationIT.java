package com.nepopay.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nepopay.AbstractIntegrationTest;
import com.nepopay.TestUsers;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regression test for a real bug: several people registering at the same moment (a class demo!)
 * all draw from the single SYSTEM_FUNDING account. Every signup must still succeed.
 */
@AutoConfigureMockMvc
class ConcurrentRegistrationIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private long fundingBalance() {
        return jdbc.queryForObject("SELECT balance_kobo FROM accounts WHERE account_type = 'SYSTEM_FUNDING'", Long.class);
    }

    @Test
    void fifteenSimultaneousRegistrationsAllSucceedAndFundingIsDebitedExactly() throws Exception {
        int n = 15;
        long before = fundingBalance();
        TestUsers users = new TestUsers(mvc);

        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            results.add(pool.submit(() -> {
                start.await();
                return users.register("cr" + idx + TestUsers.unique(), "Conc").username();
            }));
        }
        start.countDown();
        for (Future<String> f : results) {
            assertThat(f.get(60, TimeUnit.SECONDS)).isNotBlank();   // any failed registration throws here
        }
        pool.shutdown();

        assertThat(fundingBalance()).isEqualTo(before - n * 10_000_000L);
    }
}
