package com.nepopay.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserSearchServiceTest {

    @Test
    void likeWildcardsAreEscaped() {
        assertThat(UserSearchService.escapeLike("a_b%c")).isEqualTo("a!_b!%c");
        assertThat(UserSearchService.escapeLike("wow!")).isEqualTo("wow!!");
        assertThat(UserSearchService.escapeLike("plain")).isEqualTo("plain");
    }
}
