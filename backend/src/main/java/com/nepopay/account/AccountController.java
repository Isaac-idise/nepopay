package com.nepopay.account;

import com.nepopay.common.ApiResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    public record AccountResponse(String accountNumber, long balanceKobo, String currency, String status) {
    }

    public record BalanceResponse(long balanceKobo, String currency) {
    }

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/me")
    public ApiResponse<AccountResponse> me(@AuthenticationPrincipal Jwt jwt) {
        Account a = accounts.getUserAccount(UUID.fromString(jwt.getSubject()));
        return ApiResponse.ok(new AccountResponse(a.getAccountNumber(), a.getBalanceKobo(), "NGN", a.getStatus().name()));
    }

    @GetMapping("/me/balance")
    public ApiResponse<BalanceResponse> balance(@AuthenticationPrincipal Jwt jwt) {
        Account a = accounts.getUserAccount(UUID.fromString(jwt.getSubject()));
        return ApiResponse.ok(new BalanceResponse(a.getBalanceKobo(), "NGN"));
    }
}
