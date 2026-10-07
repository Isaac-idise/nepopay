package com.nepopay.user;

import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSearchService {

    private final UserRepository users;

    public UserSearchService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Page<UserSearchResult> search(UUID selfId, String q, Pageable pageable) {
        String term = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (term.startsWith("@")) {
            term = term.substring(1);
        }
        if (term.length() < 2) {
            throw new NepOpayException(ErrorCode.VALIDATION_ERROR, "Search text must be at least 2 characters.");
        }
        if (term.length() > 30) {
            throw new NepOpayException(ErrorCode.VALIDATION_ERROR, "Search text is too long.");
        }
        return users.search(selfId, escapeLike(term) + "%", pageable);
    }

    /**
     * In SQL LIKE, '_' means "any one character" and '%' means "anything". Usernames can contain '_',
     * so the user's text must be escaped, otherwise searching "a_" would also match "ab...".
     */
    public static String escapeLike(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
