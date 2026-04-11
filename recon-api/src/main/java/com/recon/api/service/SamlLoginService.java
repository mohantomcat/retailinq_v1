package com.recon.api.service;

import com.recon.api.domain.AuditLedgerWriteRequest;
import com.recon.api.domain.SamlLoginStartRequest;
import com.recon.api.domain.SamlLoginStartResponse;
import com.recon.api.domain.SamlLoginState;
import com.recon.api.domain.TenantAuthConfigEntity;
import com.recon.api.domain.User;
import com.recon.api.repository.SamlLoginStateRepository;
import com.recon.api.repository.TenantAuthConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml4AuthenticationProvider;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationToken;
import org.springframework.security.saml2.provider.service.authentication.Saml2RedirectAuthenticationRequest;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.zip.Deflater;

@Service
@RequiredArgsConstructor
@Slf4j
public class SamlLoginService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final int LOGIN_STATE_TTL_MINUTES = 10;

    private final TenantAuthConfigRepository tenantAuthConfigRepository;
    private final SamlLoginStateRepository samlLoginStateRepository;
    private final RestTemplateBuilder restTemplateBuilder;
    private final EnterpriseIdentityLifecycleService enterpriseIdentityLifecycleService;
    private final SsoLoginCompletionService ssoLoginCompletionService;
    private final AuditLedgerService auditLedgerService;

    @Value("${app.security.sso.ui-base-url:http://localhost:5173}")
    private String ssoUiBaseUrl;

    @Transactional
    public SamlLoginStartResponse startLogin(SamlLoginStartRequest request) {
        String tenantId = trimToNull(request != null ? request.getTenantId() : null);
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant is required");
        }
        TenantAuthConfigEntity config = resolveEnabledConfig(tenantId);
        ResolvedSamlSettings settings = resolveSettings(config);

        if (settings.singleSignOnBinding() != Saml2MessageBinding.REDIRECT) {
            throw new IllegalArgumentException("SAML identity provider must support HTTP-Redirect binding");
        }

        samlLoginStateRepository.deleteByExpiresAtBefore(LocalDateTime.now().minusMinutes(1));

        String relayState = randomUrlToken(24);
        String requestId = "_" + UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(LOGIN_STATE_TTL_MINUTES);
        samlLoginStateRepository.save(SamlLoginState.builder()
                .relayStateHash(sha256Hex(relayState))
                .tenantId(tenantId)
                .requestId(requestId)
                .expiresAt(expiresAt)
                .build());

        String samlRequestXml = buildAuthnRequestXml(config, settings, requestId);
        String encodedRequest = deflateAndBase64(samlRequestXml);
        String redirectUrl = UriComponentsBuilder
                .fromUriString(settings.singleSignOnServiceLocation())
                .queryParam("SAMLRequest", encodedRequest)
                .queryParam("RelayState", relayState)
                .build(true)
                .toUriString();

        return SamlLoginStartResponse.builder()
                .redirectUrl(redirectUrl)
                .relayState(relayState)
                .expiresAt(expiresAt)
                .build();
    }

    @Transactional
    public String consumeAssertion(String tenantId,
                                   String samlResponse,
                                   String relayState) {
        TenantAuthConfigEntity config = resolveEnabledConfig(tenantId);
        ResolvedSamlSettings settings = resolveSettings(config);
        String decodedResponse = decodeBase64Xml(samlResponse);

        SamlLoginState loginState = consumeLoginStateIfPresent(decodedResponse, relayState);

        RelyingPartyRegistration registration = buildRegistration(config, settings);
        Saml2AuthenticationToken authenticationToken = loginState == null
                ? new Saml2AuthenticationToken(registration, decodedResponse)
                : new Saml2AuthenticationToken(
                registration,
                decodedResponse,
                Saml2RedirectAuthenticationRequest.withRelyingPartyRegistration(registration)
                        .id(loginState.getRequestId())
                        .relayState(relayState)
                        .samlRequest("")
                        .authenticationRequestUri(settings.singleSignOnServiceLocation())
                        .build());

        Authentication authentication = new OpenSaml4AuthenticationProvider().authenticate(authenticationToken);
        Saml2AuthenticatedPrincipal principal = (Saml2AuthenticatedPrincipal) authentication.getPrincipal();

        String externalSubject = firstNonBlank(principal.getName(), resolveFirstAttribute(principal, config.getSamlEmailAttribute()));
        String email = firstNonBlank(
                resolveFirstAttribute(principal, config.getSamlEmailAttribute()),
                resolveFirstAttribute(principal, "email"),
                resolveFirstAttribute(principal, "mail"));
        String username = firstNonBlank(
                resolveFirstAttribute(principal, config.getSamlUsernameAttribute()),
                email,
                externalSubject);
        String fullName = firstNonBlank(
                resolveFirstAttribute(principal, "displayName"),
                resolveFirstAttribute(principal, "name"),
                buildName(
                        resolveFirstAttribute(principal, "givenName"),
                        resolveFirstAttribute(principal, "sn")),
                username);
        Set<String> groups = resolveAttributes(principal, config.getSamlGroupsAttribute());

        User user = enterpriseIdentityLifecycleService.syncSsoIdentity(
                config,
                "SAML",
                new EnterpriseIdentityLifecycleService.ExternalIdentityProfile(
                        externalSubject,
                        username,
                        email,
                        fullName,
                        email != null,
                        groups),
                "saml");
        recordAudit(user.getTenantId(), user.getId().toString(), user.getUsername(), settings.idpEntityId());
        String code = ssoLoginCompletionService.issueCode(user, "SAML");
        return UriComponentsBuilder
                .fromUriString(trimTrailingSlash(ssoUiBaseUrl) + "/login")
                .queryParam("ssoCode", code)
                .build(true)
                .toUriString();
    }

    private TenantAuthConfigEntity resolveEnabledConfig(String tenantId) {
        TenantAuthConfigEntity config = tenantAuthConfigRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant SAML configuration was not found"));
        if (!config.isSamlEnabled()) {
            throw new IllegalArgumentException("SAML login is not enabled for this tenant");
        }
        requireField(config.getSamlEntityId(), "SAML entity id");
        requireField(config.getSamlAcsUrl(), "SAML ACS URL");
        if (trimToNull(config.getSamlIdpMetadataUrl()) == null) {
            requireField(config.getSamlSsoUrl(), "SAML SSO URL");
            requireField(config.getSamlIdpEntityId(), "SAML IdP entity id");
            requireField(config.getSamlIdpVerificationCertificate(), "SAML IdP verification certificate");
        }
        return config;
    }

    private ResolvedSamlSettings resolveSettings(TenantAuthConfigEntity config) {
        String metadataUrl = trimToNull(config.getSamlIdpMetadataUrl());
        if (metadataUrl == null) {
            return new ResolvedSamlSettings(
                    requireField(config.getSamlSsoUrl(), "SAML SSO URL"),
                    requireField(config.getSamlIdpEntityId(), "SAML IdP entity id"),
                    requireField(config.getSamlIdpVerificationCertificate(), "SAML IdP verification certificate"),
                    Saml2MessageBinding.REDIRECT);
        }

        try {
            String metadataXml = restTemplate().getForObject(metadataUrl, String.class);
            Document document = parseXml(metadataXml);
            Element root = document.getDocumentElement();
            String entityId = firstNonBlank(root.getAttribute("entityID"), config.getSamlIdpEntityId());
            if (entityId == null) {
                throw new IllegalArgumentException("SAML metadata does not contain an entityID");
            }

            Saml2MessageBinding binding = null;
            String ssoUrl = null;
            NodeList ssoServices = document.getElementsByTagNameNS("*", "SingleSignOnService");
            for (int index = 0; index < ssoServices.getLength(); index++) {
                Element element = (Element) ssoServices.item(index);
                String currentBinding = trimToNull(element.getAttribute("Binding"));
                String location = trimToNull(element.getAttribute("Location"));
                if (location == null) {
                    continue;
                }
                if ("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect".equals(currentBinding)) {
                    binding = Saml2MessageBinding.REDIRECT;
                    ssoUrl = location;
                    break;
                }
                if (binding == null && "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST".equals(currentBinding)) {
                    binding = Saml2MessageBinding.POST;
                    ssoUrl = location;
                }
            }
            if (ssoUrl == null) {
                ssoUrl = trimToNull(config.getSamlSsoUrl());
            }
            if (binding == null) {
                binding = Saml2MessageBinding.REDIRECT;
            }

            String certificate = trimToNull(config.getSamlIdpVerificationCertificate());
            if (certificate == null) {
                NodeList certificates = document.getElementsByTagNameNS("*", "X509Certificate");
                if (certificates.getLength() > 0) {
                    certificate = trimToNull(certificates.item(0).getTextContent());
                }
            }
            if (ssoUrl == null || certificate == null) {
                throw new IllegalArgumentException("SAML metadata did not contain required IdP details");
            }

            return new ResolvedSamlSettings(ssoUrl, entityId, certificate, binding);
        } catch (Exception ex) {
            throw new IllegalArgumentException("SAML metadata resolution failed: " + ex.getMessage(), ex);
        }
    }

    private SamlLoginState consumeLoginStateIfPresent(String decodedResponse, String relayState) {
        String normalizedRelayState = trimToNull(relayState);
        if (normalizedRelayState == null) {
            return null;
        }
        SamlLoginState loginState = samlLoginStateRepository
                .findByRelayStateHashAndConsumedAtIsNull(sha256Hex(normalizedRelayState))
                .orElseThrow(() -> new IllegalArgumentException("SAML relay state is invalid or already used"));
        if (loginState.getExpiresAt() == null || loginState.getExpiresAt().isBefore(LocalDateTime.now())) {
            loginState.setConsumedAt(LocalDateTime.now());
            samlLoginStateRepository.save(loginState);
            throw new IllegalArgumentException("SAML login state expired");
        }
        String inResponseTo = extractResponseAttribute(decodedResponse, "InResponseTo");
        if (inResponseTo != null && !inResponseTo.equals(loginState.getRequestId())) {
            throw new IllegalArgumentException("SAML response did not match the initiated authentication request");
        }
        loginState.setConsumedAt(LocalDateTime.now());
        samlLoginStateRepository.save(loginState);
        return loginState;
    }

    private RelyingPartyRegistration buildRegistration(TenantAuthConfigEntity config,
                                                       ResolvedSamlSettings settings) {
        X509Certificate certificate = parseCertificate(settings.verificationCertificate());
        return RelyingPartyRegistration.withRegistrationId(config.getTenantId())
                .entityId(config.getSamlEntityId())
                .assertionConsumerServiceLocation(config.getSamlAcsUrl())
                .assertionConsumerServiceBinding(Saml2MessageBinding.POST)
                .assertingPartyDetails(party -> party
                        .entityId(settings.idpEntityId())
                        .singleSignOnServiceLocation(settings.singleSignOnServiceLocation())
                        .singleSignOnServiceBinding(settings.singleSignOnBinding())
                        .wantAuthnRequestsSigned(false)
                        .verificationX509Credentials(credentials ->
                                credentials.add(Saml2X509Credential.verification(certificate))))
                .build();
    }

    private String buildAuthnRequestXml(TenantAuthConfigEntity config,
                                        ResolvedSamlSettings settings,
                                        String requestId) {
        String issueInstant = DateTimeFormatter.ISO_INSTANT.format(OffsetDateTime.now(ZoneOffset.UTC));
        return """
                <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                    xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                    ID="%s"
                    Version="2.0"
                    IssueInstant="%s"
                    Destination="%s"
                    ProtocolBinding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
                    AssertionConsumerServiceURL="%s">
                  <saml:Issuer>%s</saml:Issuer>
                  <samlp:NameIDPolicy AllowCreate="true"/>
                </samlp:AuthnRequest>
                """.formatted(
                xmlEscape(requestId),
                xmlEscape(issueInstant),
                xmlEscape(settings.singleSignOnServiceLocation()),
                xmlEscape(config.getSamlAcsUrl()),
                xmlEscape(config.getSamlEntityId()));
    }

    private String decodeBase64Xml(String value) {
        String trimmed = requireField(value, "SAMLResponse");
        try {
            return new String(Base64.getDecoder().decode(trimmed), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("SAMLResponse must be base64 encoded", ex);
        }
    }

    private X509Certificate parseCertificate(String value) {
        try {
            String pem = requireField(value, "SAML IdP verification certificate")
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(pem);
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(decoded));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to parse SAML verification certificate", ex);
        }
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String extractResponseAttribute(String xml, String attributeName) {
        try {
            Document document = parseXml(xml);
            Element root = document.getDocumentElement();
            return trimToNull(root.getAttribute(attributeName));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to parse SAML response", ex);
        }
    }

    private Set<String> resolveAttributes(Saml2AuthenticatedPrincipal principal, String attributeName) {
        String normalized = trimToNull(attributeName);
        if (normalized == null) {
            return Set.of();
        }
        List<Object> values = principal.getAttribute(normalized);
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> resolved = new LinkedHashSet<>();
        for (Object value : values) {
            String stringValue = Objects.toString(value, "").trim();
            if (!stringValue.isEmpty()) {
                resolved.add(stringValue);
            }
        }
        return resolved;
    }

    private String resolveFirstAttribute(Saml2AuthenticatedPrincipal principal, String attributeName) {
        String normalized = trimToNull(attributeName);
        if (normalized == null) {
            return null;
        }
        Object value = principal.getFirstAttribute(normalized);
        return trimToNull(value == null ? null : Objects.toString(value, null));
    }

    private String buildName(String givenName, String familyName) {
        String combined = (defaultIfBlank(givenName, "") + " " + defaultIfBlank(familyName, "")).trim();
        return trimToNull(combined);
    }

    private RestTemplate restTemplate() {
        return restTemplateBuilder
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
                .build();
    }

    private String deflateAndBase64(String xml) {
        byte[] input = xml.getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.DEFLATED, true);
        deflater.setInput(input);
        deflater.finish();
        byte[] buffer = new byte[4096];
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            outputStream.write(buffer, 0, count);
        }
        deflater.end();
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String randomUrlToken(int bytes) {
        byte[] token = new byte[bytes];
        RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private String xmlEscape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String requireField(String value, String label) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return trimmed;
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimTrailingSlash(String value) {
        String trimmed = requireField(value, "SSO UI base URL");
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private void recordAudit(String tenantId,
                             String userId,
                             String username,
                             String idpEntityId) {
        auditLedgerService.record(AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("SECURITY")
                .moduleKey("SECURITY")
                .entityType("SAML_SESSION")
                .entityKey(userId)
                .actionType("SAML_ASSERTION_ACCEPTED")
                .title("SAML assertion accepted")
                .summary(username)
                .actor(username)
                .status("SUCCESS")
                .referenceKey(userId)
                .controlFamily("ACCESS_CONTROL")
                .evidenceTags(List.of("SECURITY", "SAML", "LOGIN"))
                .metadata(Map.of("idpEntityId", idpEntityId))
                .build());
    }

    private record ResolvedSamlSettings(
            String singleSignOnServiceLocation,
            String idpEntityId,
            String verificationCertificate,
            Saml2MessageBinding singleSignOnBinding) {
    }
}
