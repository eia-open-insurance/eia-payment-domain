package com.eia.payment.domain;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @GetMapping
    public List<PaymentDto> list(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                 @RequestParam(name = "sourceType", required = false) String sourceType,
                                 @RequestParam(name = "sourceId", required = false) String sourceId) {
        return service.list(tenantId, sourceType, sourceId);
    }

    @GetMapping("/search")
    public PaymentSearchResponse search(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                        @RequestParam(name = "q", required = false) String q,
                                        @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return service.search(tenantId, q, limit);
    }

    @GetMapping("/{id}")
    public PaymentDto get(@RequestHeader("X-EIA-Tenant") UUID tenantId, @PathVariable UUID id) {
        return service.get(tenantId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentDto create(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                             @Valid @RequestBody CreatePaymentCommand command) {
        return service.create(tenantId, command);
    }

    @PostMapping("/{id}/submit")
    public PaymentDto submit(@RequestHeader("X-EIA-Tenant") UUID tenantId, @PathVariable UUID id) {
        return service.submit(tenantId, id);
    }

    @PostMapping("/{id}/authorize")
    public PaymentDto authorize(@RequestHeader("X-EIA-Tenant") UUID tenantId, @PathVariable UUID id) {
        return service.authorize(tenantId, id);
    }

    @PostMapping("/{id}/approve")
    public PaymentDto approve(@RequestHeader("X-EIA-Tenant") UUID tenantId, @PathVariable UUID id) {
        return service.approve(tenantId, id);
    }

    @PostMapping("/{id}/issue")
    public PaymentDto issue(@RequestHeader("X-EIA-Tenant") UUID tenantId, @PathVariable UUID id) {
        return service.issue(tenantId, id);
    }

    @PatchMapping("/{id}/status")
    public PaymentDto updateStatus(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody UpdatePaymentStatusCommand command) {
        return service.updateStatus(tenantId, id, command);
    }

    @GetMapping("/{id}/allocations")
    public PaymentAllocationSummary allocations(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                                @PathVariable UUID id) {
        return service.allocations(tenantId, id);
    }

    @PostMapping("/{id}/allocations")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentAllocation allocate(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                      @PathVariable UUID id,
                                      @RequestBody CreatePaymentAllocationCommand command) {
        return service.allocate(tenantId, id, command);
    }
}
