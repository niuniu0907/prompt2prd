package com.prompt2prd.quota;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/** Read-only endpoint for the caller's current system-key allowance. */
@RestController
@RequestMapping("/api/quota")
public final class QuotaController {

    private final QuotaService quotaService;
    private final ClientIpDigest clientIpDigest;

    public QuotaController(QuotaService quotaService, ClientIpDigest clientIpDigest) {
        this.quotaService = quotaService;
        this.clientIpDigest = clientIpDigest;
    }

    @GetMapping
    public QuotaSnapshot current(ServerWebExchange exchange) {
        return quotaService.current(clientIpDigest.from(exchange));
    }
}
