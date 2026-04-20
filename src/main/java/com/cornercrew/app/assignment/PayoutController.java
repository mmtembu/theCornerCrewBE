package com.cornercrew.app.assignment;

import com.cornercrew.app.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class PayoutController {

    private final PayoutService payoutService;

    public PayoutController(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @PostMapping("/assignments/{id}/payout")
    public ResponseEntity<PayoutResultDto> processPayout(
            @PathVariable Long id,
            @AuthenticationPrincipal User admin) {
        Long adminId = admin != null ? admin.getId() : null;
        PayoutResultDto result = payoutService.processPayout(id, adminId);
        return ResponseEntity.ok(result);
    }
}
