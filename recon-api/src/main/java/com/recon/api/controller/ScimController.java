package com.recon.api.controller;

import com.recon.api.domain.ScimErrorResponse;
import com.recon.api.domain.ScimPatchRequest;
import com.recon.api.domain.ScimUserRequest;
import com.recon.api.domain.ScimUserResource;
import com.recon.api.security.ReconUserPrincipal;
import com.recon.api.service.ScimProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

@RestController
@RequestMapping(path = "/api/scim/v2/{tenantId}", produces = "application/scim+json")
@RequiredArgsConstructor
public class ScimController {

    private static final MediaType SCIM_MEDIA_TYPE = MediaType.parseMediaType("application/scim+json");

    private final ScimProvisioningService scimProvisioningService;

    @GetMapping("/ServiceProviderConfig")
    public ResponseEntity<?> getServiceProviderConfig(@PathVariable("tenantId") String tenantId,
                                                      @AuthenticationPrincipal ReconUserPrincipal principal) {
        return handle(tenantId, principal, () -> scimProvisioningService.getServiceProviderConfig(tenantId), HttpStatus.OK);
    }

    @GetMapping("/Schemas")
    public ResponseEntity<?> getSchemas(@PathVariable("tenantId") String tenantId,
                                        @AuthenticationPrincipal ReconUserPrincipal principal) {
        return handle(tenantId, principal, () -> scimProvisioningService.getSchemas(tenantId), HttpStatus.OK);
    }

    @GetMapping("/ResourceTypes")
    public ResponseEntity<?> getResourceTypes(@PathVariable("tenantId") String tenantId,
                                              @AuthenticationPrincipal ReconUserPrincipal principal) {
        return handle(tenantId, principal, () -> scimProvisioningService.getResourceTypes(tenantId), HttpStatus.OK);
    }

    @GetMapping("/Users")
    public ResponseEntity<?> listUsers(@PathVariable("tenantId") String tenantId,
                                       @RequestParam(value = "filter", required = false) String filter,
                                       @RequestParam(value = "startIndex", required = false) Integer startIndex,
                                       @RequestParam(value = "count", required = false) Integer count,
                                       @AuthenticationPrincipal ReconUserPrincipal principal) {
        return handle(tenantId, principal, () -> scimProvisioningService.listUsers(tenantId, filter, startIndex, count), HttpStatus.OK);
    }

    @GetMapping("/Users/{userId}")
    public ResponseEntity<?> getUser(@PathVariable("tenantId") String tenantId,
                                     @PathVariable("userId") String userId,
                                     @AuthenticationPrincipal ReconUserPrincipal principal) {
        return handle(tenantId, principal, () -> scimProvisioningService.getUser(tenantId, userId), HttpStatus.OK);
    }

    @PostMapping(path = "/Users", consumes = {"application/scim+json", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> createUser(@PathVariable("tenantId") String tenantId,
                                        @RequestBody ScimUserRequest request,
                                        @AuthenticationPrincipal ReconUserPrincipal principal) {
        return handle(tenantId, principal, () -> scimProvisioningService.createUser(
                tenantId,
                request,
                principal != null ? principal.getUsername() : "scim"), HttpStatus.CREATED);
    }

    @PutMapping(path = "/Users/{userId}", consumes = {"application/scim+json", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> replaceUser(@PathVariable("tenantId") String tenantId,
                                         @PathVariable("userId") String userId,
                                         @RequestBody ScimUserRequest request,
                                         @AuthenticationPrincipal ReconUserPrincipal principal) {
        return handle(tenantId, principal, () -> scimProvisioningService.replaceUser(
                tenantId,
                userId,
                request,
                principal != null ? principal.getUsername() : "scim"), HttpStatus.OK);
    }

    @PatchMapping(path = "/Users/{userId}", consumes = {"application/scim+json", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> patchUser(@PathVariable("tenantId") String tenantId,
                                       @PathVariable("userId") String userId,
                                       @RequestBody ScimPatchRequest request,
                                       @AuthenticationPrincipal ReconUserPrincipal principal) {
        return handle(tenantId, principal, () -> scimProvisioningService.patchUser(
                tenantId,
                userId,
                request,
                principal != null ? principal.getUsername() : "scim"), HttpStatus.OK);
    }

    @DeleteMapping("/Users/{userId}")
    public ResponseEntity<?> deactivateUser(@PathVariable("tenantId") String tenantId,
                                            @PathVariable("userId") String userId,
                                            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            assertScimPrincipal(principal, tenantId);
            scimProvisioningService.deactivateUser(tenantId, userId, principal != null ? principal.getUsername() : "scim");
            return ResponseEntity.noContent()
                    .header(HttpHeaders.CONTENT_TYPE, SCIM_MEDIA_TYPE.toString())
                    .build();
        } catch (NoSuchElementException ex) {
            return error(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return error(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (Exception ex) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "SCIM request failed");
        }
    }

    private ResponseEntity<?> handle(String tenantId,
                                     ReconUserPrincipal principal,
                                     Supplier<Object> supplier,
                                     HttpStatus successStatus) {
        try {
            assertScimPrincipal(principal, tenantId);
            Object body = supplier.get();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(SCIM_MEDIA_TYPE);
            if (successStatus == HttpStatus.CREATED
                    && body instanceof ScimUserResource resource
                    && resource.getMeta() != null
                    && resource.getMeta().getLocation() != null) {
                headers.set(HttpHeaders.LOCATION, resource.getMeta().getLocation());
            }
            return new ResponseEntity<>(body, headers, successStatus);
        } catch (NoSuchElementException ex) {
            return error(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return error(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (Exception ex) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "SCIM request failed");
        }
    }

    private void assertScimPrincipal(ReconUserPrincipal principal,
                                     String tenantId) {
        if (principal == null || !principal.hasPermission("SCIM_PROVISION")) {
            throw new IllegalArgumentException("SCIM principal is not authorized");
        }
        if (!tenantId.equals(principal.getTenantId())) {
            throw new IllegalArgumentException("SCIM tenant id does not match the authenticated principal");
        }
    }

    private ResponseEntity<ScimErrorResponse> error(HttpStatus status,
                                                    String detail) {
        return ResponseEntity.status(status)
                .contentType(SCIM_MEDIA_TYPE)
                .body(ScimErrorResponse.builder()
                        .detail(detail)
                        .status(Integer.toString(status.value()))
                        .build());
    }
}
