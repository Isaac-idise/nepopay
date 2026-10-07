package com.nepopay.transfer;

import com.nepopay.auth.CurrentUser;
import com.nepopay.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transfers;

    public TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    /**
     * 201 = delivered. 202 = accepted but held for review (status FLAGGED).
     * 200 = this Idempotency-Key already succeeded; same result returned, nothing moved again.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        TransferResponse result = transfers.transfer(CurrentUser.id(jwt), request, idempotencyKey);
        HttpStatus status = result.idempotentReplay() ? HttpStatus.OK
                : "FLAGGED".equals(result.status()) ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.ok(result));
    }
}
