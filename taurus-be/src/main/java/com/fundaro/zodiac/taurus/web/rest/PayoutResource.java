package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.AdjustmentRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.EventPayoutDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.MemberPayoutsDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.MemberSummaryDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettingDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettingRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettledEventDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettlementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettlementRequest;
import com.fundaro.zodiac.taurus.service.finance.PayoutService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The shares of the participants (docs/event-payout-shares-spec.md). The finance rule of the security chain already
 * admits only the finance managers; the tenant feature includes the finance and the calendar among its dependencies.
 */
@RestController
@RequestMapping("/api/finance/payouts")
@RequiresTenantFeature(TenantFeature.EVENT_PAYOUTS)
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_BOARD','ROLE_TREASURER')")
public class PayoutResource {

    private final PayoutService payoutService;

    public PayoutResource(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @GetMapping("/settings")
    public List<SettingDTO> settings(AbstractAuthenticationToken token) {
        return payoutService.findSettings(token);
    }

    @PostMapping("/settings")
    public ResponseEntity<SettingDTO> createSetting(@Valid @RequestBody SettingRequest request, AbstractAuthenticationToken token) {
        return ResponseEntity.status(201).body(payoutService.createSetting(request, token));
    }

    @PutMapping("/settings/{id}")
    public SettingDTO updateSetting(@PathVariable long id, @Valid @RequestBody SettingRequest request, AbstractAuthenticationToken token) {
        return payoutService.updateSetting(id, request, token);
    }

    @DeleteMapping("/settings/{id}")
    public ResponseEntity<Void> deleteSetting(@PathVariable long id, AbstractAuthenticationToken token) {
        payoutService.deleteSetting(id, token);
        return ResponseEntity.noContent().build();
    }

    /** The events already settled that a version with this date would recalculate. */
    @GetMapping("/settings/affected-events")
    public List<SettledEventDTO> affectedEvents(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom,
        @RequestParam(required = false) Long settingId,
        AbstractAuthenticationToken token
    ) {
        return payoutService.settledEventsAffectedBy(validFrom, settingId, token);
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<EventPayoutDTO> event(
        @PathVariable long eventId,
        @RequestParam(required = false) BigDecimal sharePercent,
        @RequestParam(required = false) BigDecimal roundingStep,
        AbstractAuthenticationToken token
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(payoutService.simulate(eventId, sharePercent, roundingStep, token));
    }

    @PutMapping("/events/{eventId}/adjustments/{userId}")
    public ResponseEntity<Void> setAdjustment(
        @PathVariable long eventId,
        @PathVariable long userId,
        @Valid @RequestBody AdjustmentRequest request,
        AbstractAuthenticationToken token
    ) {
        payoutService.setAdjustment(eventId, userId, request, token);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/events/{eventId}/adjustments/{userId}")
    public ResponseEntity<Void> removeAdjustment(@PathVariable long eventId, @PathVariable long userId, AbstractAuthenticationToken token) {
        payoutService.removeAdjustment(eventId, userId, token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users")
    public List<MemberSummaryDTO> members(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        AbstractAuthenticationToken token
    ) {
        return payoutService.findMembers(from, to, token);
    }

    @GetMapping("/users/{userId}")
    public MemberPayoutsDTO member(
        @PathVariable long userId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) String status,
        AbstractAuthenticationToken token
    ) {
        return payoutService.findMember(userId, from, to, status, token);
    }

    @PostMapping("/settlements")
    public ResponseEntity<SettlementDTO> settle(@Valid @RequestBody SettlementRequest request, AbstractAuthenticationToken token) {
        return ResponseEntity.status(201).body(payoutService.settle(request, token));
    }
}
