package com.recon.api.service;

import com.recon.api.domain.BusinessValueContextDto;
import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.ItemDiscrepancy;
import com.recon.api.domain.ReconSummary;
import com.recon.api.domain.TenantConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ExceptionBusinessValueService {

    private final ReconQueryService reconQueryService;
    private final TenantService tenantService;

    public Map<String, BusinessValueContextDto> enrichCases(String tenantId, List<ExceptionCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return Map.of();
        }

        TenantConfig tenant = tenantService.getTenant(tenantId);
        List<String> transactionKeys = cases.stream()
                .map(ExceptionCase::getTransactionKey)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();

        Map<String, ReconSummary> reconByKey = reconQueryService.getByTransactionKeys(transactionKeys, tenant);
        Map<String, BusinessValueContextDto> result = new LinkedHashMap<>();
        for (ExceptionCase exceptionCase : cases) {
            result.put(caseKey(exceptionCase), buildCaseValue(exceptionCase, reconByKey.get(caseKey(exceptionCase)), tenant));
        }
        return result;
    }

    public BusinessValueContextDto aggregateIncidentValue(List<BusinessValueContextDto> caseValues, long openCaseCount) {
        if (caseValues == null || caseValues.isEmpty()) {
            return BusinessValueContextDto.builder()
                    .businessValueScore(0)
                    .businessValueBand("LOW")
                    .customerImpact("LOW")
                    .summary("Business value not yet available")
                    .build();
        }

        BigDecimal valueAtRisk = sum(caseValues, BusinessValueContextDto::getValueAtRisk);
        BigDecimal amountVariance = sum(caseValues, BusinessValueContextDto::getAmountVariance);
        BigDecimal totalQuantity = sum(caseValues, BusinessValueContextDto::getTotalQuantity);
        BigDecimal quantityImpact = sum(caseValues, BusinessValueContextDto::getQuantityImpact);
        int lineItemCount = sumInteger(caseValues, BusinessValueContextDto::getLineItemCount);
        int affectedItemCount = sumInteger(caseValues, BusinessValueContextDto::getAffectedItemCount);
        int maxScore = caseValues.stream()
                .map(BusinessValueContextDto::getBusinessValueScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        int incidentScore = Math.min(100, maxScore + (int) Math.min(30L, Math.max(0L, openCaseCount - 1L) * 6L));

        return BusinessValueContextDto.builder()
                .currencyCode(firstNonBlank(caseValues, BusinessValueContextDto::getCurrencyCode))
                .valueAtRisk(valueAtRisk)
                .amountVariance(amountVariance)
                .lineItemCount(lineItemCount > 0 ? lineItemCount : null)
                .affectedItemCount(affectedItemCount > 0 ? affectedItemCount : null)
                .totalQuantity(isPositive(totalQuantity) ? totalQuantity : null)
                .quantityImpact(isPositive(quantityImpact) ? quantityImpact : null)
                .businessValueScore(incidentScore)
                .businessValueBand(businessValueBand(incidentScore))
                .customerImpact(customerImpactLabel(incidentScore))
                .summary(buildIncidentSummary(caseValues, openCaseCount, valueAtRisk, affectedItemCount, quantityImpact))
                .build();
    }

    public int impactBoost(BusinessValueContextDto businessValue) {
        if (businessValue == null || businessValue.getBusinessValueScore() == null) {
            return 0;
        }
        return Math.min(25, Math.max(0, businessValue.getBusinessValueScore() / 4));
    }

    public String caseKey(ExceptionCase exceptionCase) {
        return caseKey(exceptionCase.getReconView(), exceptionCase.getTransactionKey());
    }

    public String caseKey(String reconView, String transactionKey) {
        return Objects.toString(reconView, "").toUpperCase(Locale.ROOT)
                + "::"
                + Objects.toString(transactionKey, "");
    }

    private BusinessValueContextDto buildCaseValue(ExceptionCase exceptionCase,
                                                   ReconSummary summary,
                                                   TenantConfig tenant) {
        BigDecimal valueAtRisk = firstNonZero(
                summary != null ? summary.getTransactionAmount() : null,
                summary != null ? summary.getAmountVariance() : null
        );
        BigDecimal amountVariance = summary != null ? positiveOrNull(summary.getAmountVariance()) : null;
        Integer lineItemCount = summary != null ? positiveOrNull(summary.getLineItemCount()) : null;
        Integer affectedItemCount = summary != null
                ? positiveOrNull(summary.getAffectedItemCount())
                : null;
        if (affectedItemCount == null && summary != null && summary.getDiscrepancies() != null && !summary.getDiscrepancies().isEmpty()) {
            affectedItemCount = summary.getDiscrepancies().size();
        }
        BigDecimal totalQuantity = summary != null ? positiveOrNull(summary.getTotalQuantity()) : null;
        BigDecimal quantityImpact = summary != null
                ? positiveOrNull(summary.getQuantityImpact())
                : null;
        if (quantityImpact == null && summary != null) {
            quantityImpact = deriveQuantityImpact(summary.getDiscrepancies());
        }
        if (lineItemCount == null && affectedItemCount != null) {
            lineItemCount = affectedItemCount;
        }

        int score = businessValueScore(valueAtRisk, lineItemCount, affectedItemCount, quantityImpact);

        return BusinessValueContextDto.builder()
                .currencyCode(tenant != null ? tenant.getCurrencyCode() : null)
                .valueAtRisk(valueAtRisk)
                .amountVariance(amountVariance)
                .lineItemCount(lineItemCount)
                .affectedItemCount(affectedItemCount)
                .totalQuantity(totalQuantity)
                .quantityImpact(quantityImpact)
                .businessValueScore(score)
                .businessValueBand(businessValueBand(score))
                .customerImpact(customerImpactLabel(score))
                .summary(buildCaseSummary(tenant, exceptionCase, valueAtRisk, amountVariance, lineItemCount, affectedItemCount, quantityImpact))
                .build();
    }

    private int businessValueScore(BigDecimal valueAtRisk,
                                   Integer lineItemCount,
                                   Integer affectedItemCount,
                                   BigDecimal quantityImpact) {
        int score = 0;
        if (valueAtRisk != null) {
            if (valueAtRisk.compareTo(new BigDecimal("1000")) >= 0) {
                score += 45;
            } else if (valueAtRisk.compareTo(new BigDecimal("500")) >= 0) {
                score += 32;
            } else if (valueAtRisk.compareTo(new BigDecimal("100")) >= 0) {
                score += 20;
            } else if (valueAtRisk.compareTo(BigDecimal.ZERO) > 0) {
                score += 10;
            }
        }
        if (lineItemCount != null) {
            if (lineItemCount >= 10) {
                score += 12;
            } else if (lineItemCount >= 5) {
                score += 6;
            } else if (lineItemCount >= 1) {
                score += 3;
            }
        }
        if (affectedItemCount != null) {
            if (affectedItemCount >= 5) {
                score += 18;
            } else if (affectedItemCount >= 2) {
                score += 10;
            } else if (affectedItemCount >= 1) {
                score += 4;
            }
        }
        if (quantityImpact != null) {
            if (quantityImpact.compareTo(new BigDecimal("10")) >= 0) {
                score += 15;
            } else if (quantityImpact.compareTo(new BigDecimal("3")) >= 0) {
                score += 8;
            } else if (quantityImpact.compareTo(BigDecimal.ZERO) > 0) {
                score += 4;
            }
        }
        return Math.min(100, score);
    }

    private String businessValueBand(int score) {
        if (score >= 70) {
            return "HIGH";
        }
        if (score >= 40) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String customerImpactLabel(int score) {
        if (score >= 70) {
            return "HIGH";
        }
        if (score >= 40) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String buildCaseSummary(TenantConfig tenant,
                                    ExceptionCase exceptionCase,
                                    BigDecimal valueAtRisk,
                                    BigDecimal amountVariance,
                                    Integer lineItemCount,
                                    Integer affectedItemCount,
                                    BigDecimal quantityImpact) {
        List<String> parts = new java.util.ArrayList<>();
        if (valueAtRisk != null) {
            parts.add(formatMoney(tenant, valueAtRisk) + " at risk");
        }
        if (amountVariance != null) {
            parts.add("variance " + formatMoney(tenant, amountVariance));
        }
        if (affectedItemCount != null && affectedItemCount > 0) {
            parts.add(affectedItemCount + " items affected");
        } else if (lineItemCount != null && lineItemCount > 0) {
            parts.add(lineItemCount + " line items");
        }
        if (quantityImpact != null && quantityImpact.compareTo(BigDecimal.ZERO) > 0) {
            parts.add("qty impact " + strip(quantityImpact));
        }
        if (parts.isEmpty()) {
            String status = Objects.toString(exceptionCase.getReconStatus(), "transaction");
            return status + " requires business review";
        }
        return String.join(" | ", parts);
    }

    private String buildIncidentSummary(List<BusinessValueContextDto> caseValues,
                                        long openCaseCount,
                                        BigDecimal valueAtRisk,
                                        int affectedItemCount,
                                        BigDecimal quantityImpact) {
        List<String> parts = new java.util.ArrayList<>();
        String currencyCode = firstNonBlank(caseValues, BusinessValueContextDto::getCurrencyCode);
        if (valueAtRisk != null) {
            parts.add((currencyCode != null ? currencyCode + " " : "") + strip(valueAtRisk) + " total at risk");
        }
        parts.add(openCaseCount + " cases");
        if (affectedItemCount > 0) {
            parts.add(affectedItemCount + " items affected");
        }
        if (quantityImpact != null && quantityImpact.compareTo(BigDecimal.ZERO) > 0) {
            parts.add("qty impact " + strip(quantityImpact));
        }
        return String.join(" | ", parts);
    }

    private BigDecimal deriveQuantityImpact(List<ItemDiscrepancy> discrepancies) {
        if (discrepancies == null || discrepancies.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ItemDiscrepancy discrepancy : discrepancies) {
            if (discrepancy == null) {
                continue;
            }
            BigDecimal left = discrepancy.getXstoreQuantity();
            BigDecimal right = discrepancy.getSiocsQuantity();
            if (left != null && right != null) {
                total = total.add(left.subtract(right).abs());
            } else if (left != null) {
                total = total.add(left.abs());
            } else if (right != null) {
                total = total.add(right.abs());
            }
        }
        return total.compareTo(BigDecimal.ZERO) > 0 ? total : null;
    }

    private BigDecimal sum(List<BusinessValueContextDto> values,
                           java.util.function.Function<BusinessValueContextDto, BigDecimal> getter) {
        BigDecimal total = BigDecimal.ZERO;
        boolean found = false;
        for (BusinessValueContextDto value : values) {
            BigDecimal current = value != null ? getter.apply(value) : null;
            if (current != null) {
                total = total.add(current);
                found = true;
            }
        }
        return found ? total : null;
    }

    private int sumInteger(List<BusinessValueContextDto> values,
                           java.util.function.Function<BusinessValueContextDto, Integer> getter) {
        int total = 0;
        for (BusinessValueContextDto value : values) {
            Integer current = value != null ? getter.apply(value) : null;
            if (current != null) {
                total += current;
            }
        }
        return total;
    }

    private String firstNonBlank(List<BusinessValueContextDto> values,
                                 java.util.function.Function<BusinessValueContextDto, String> getter) {
        for (BusinessValueContextDto value : values) {
            String current = value != null ? getter.apply(value) : null;
            if (current != null && !current.isBlank()) {
                return current;
            }
        }
        return null;
    }

    private BigDecimal firstNonZero(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal positiveOrNull(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0 ? value : null;
    }

    private Integer positiveOrNull(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private String formatMoney(TenantConfig tenant, BigDecimal amount) {
        String currencyCode = tenant != null ? tenant.getCurrencyCode() : null;
        return (currencyCode != null ? currencyCode + " " : "") + strip(amount);
    }

    private String strip(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
