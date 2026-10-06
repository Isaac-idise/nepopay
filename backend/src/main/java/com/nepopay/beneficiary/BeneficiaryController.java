package com.nepopay.beneficiary;

import com.nepopay.auth.CurrentUser;
import com.nepopay.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Beneficiaries are addressed by username, so no internal ids ever leave the server. */
@RestController
@RequestMapping("/api/v1/beneficiaries")
public class BeneficiaryController {

    private final BeneficiaryService service;

    public BeneficiaryController(BeneficiaryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<BeneficiaryView>> list(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.list(CurrentUser.id(jwt)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BeneficiaryView>> add(@AuthenticationPrincipal Jwt jwt,
                                                            @Valid @RequestBody AddBeneficiaryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.add(CurrentUser.id(jwt), request)));
    }

    @DeleteMapping("/{username}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable String username) {
        service.remove(CurrentUser.id(jwt), username);
        return ApiResponse.<Void>ok(null);
    }
}
