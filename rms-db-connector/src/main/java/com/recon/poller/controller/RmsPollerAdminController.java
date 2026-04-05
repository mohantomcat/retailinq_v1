package com.recon.rms.controller;

import com.recon.rms.domain.RmsPollerActionResponse;
import com.recon.rms.domain.RmsPollerStatusResponse;
import com.recon.rms.domain.RmsResetCheckpointRequest;
import com.recon.rms.service.RmsPollerAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rms-poller")
@RequiredArgsConstructor
public class RmsPollerAdminController {

    private final RmsPollerAdminService adminService;

    @GetMapping("/status")
    public RmsPollerStatusResponse status() {
        return adminService.getStatus();
    }

    @PostMapping("/actions/poll")
    public RmsPollerActionResponse poll() {
        return adminService.triggerPoll();
    }

    @PostMapping("/actions/release-lease")
    public RmsPollerActionResponse releaseLease() {
        return adminService.releaseLease();
    }

    @PostMapping("/actions/reset-checkpoint")
    public RmsPollerActionResponse resetCheckpoint(
            @RequestBody RmsResetCheckpointRequest request) {
        return adminService.resetCheckpoint(request);
    }
}

