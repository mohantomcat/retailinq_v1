package com.recon.poller.controller;

import com.recon.poller.domain.SiocsPollerActionResponse;
import com.recon.poller.domain.SiocsPollerStatusResponse;
import com.recon.poller.domain.SiocsResetCheckpointRequest;
import com.recon.poller.service.SiocsPollerAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/siocs-poller")
@RequiredArgsConstructor
public class SiocsPollerAdminController {

    private final SiocsPollerAdminService adminService;

    @GetMapping("/status")
    public SiocsPollerStatusResponse status() {
        return adminService.getStatus();
    }

    @PostMapping("/actions/poll")
    public SiocsPollerActionResponse poll() {
        return adminService.triggerPoll();
    }

    @PostMapping("/actions/release-lease")
    public SiocsPollerActionResponse releaseLease() {
        return adminService.releaseLease();
    }

    @PostMapping("/actions/reset-checkpoint")
    public SiocsPollerActionResponse resetCheckpoint(
            @RequestBody SiocsResetCheckpointRequest request) {
        return adminService.resetCheckpoint(request);
    }
}
