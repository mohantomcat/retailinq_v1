package com.recon.api.service;

import com.recon.api.domain.DashboardStats;
import com.recon.api.domain.QuantityTotalsDto;
import com.recon.api.domain.TransactionFamilyVolumeDto;
import com.recon.api.repository.ReconElasticsearchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconQueryServiceTest {

    @Mock
    private ReconElasticsearchRepository esRepository;

    @Mock
    private ReconModuleService reconModuleService;

    @InjectMocks
    private ReconQueryService reconQueryService;

    @Test
    void getDashboardStatsIncludesFamilyAndQuantitySummaries() {
        List<String> storeIds = List.of("1201");
        List<String> transactionFamilies = List.of("PO");

        when(esRepository.aggregateByStatus(
                storeIds,
                null,
                null,
                transactionFamilies,
                "2026-04-01",
                "2026-04-04",
                "SIOCS_MFCS"))
                .thenReturn(Map.of(
                        "MATCHED", 8L,
                        "MISSING_IN_MFCS", 2L));

        when(esRepository.aggregateByStore(
                storeIds,
                null,
                null,
                transactionFamilies,
                "2026-04-01",
                "2026-04-04",
                "SIOCS_MFCS"))
                .thenReturn(Map.of("1201", 10L));

        when(esRepository.aggregateByTransactionFamily(
                storeIds,
                null,
                null,
                transactionFamilies,
                "2026-04-01",
                "2026-04-04",
                "SIOCS_MFCS"))
                .thenReturn(Map.of("PO", 10L));

        when(esRepository.aggregateTransactionFamilyVolumes(
                storeIds,
                null,
                null,
                transactionFamilies,
                "2026-04-01",
                "2026-04-04",
                "SIOCS_MFCS"))
                .thenReturn(List.of(TransactionFamilyVolumeDto.builder()
                        .transactionFamily("PO")
                        .transactionCount(10L)
                        .quantityMetricsTransactionCount(10L)
                        .sourceQuantityTotal(new BigDecimal("100.0"))
                        .targetQuantityTotal(new BigDecimal("98.0"))
                        .quantityVarianceTotal(new BigDecimal("2.0"))
                        .valueMetricsAvailable(false)
                        .build()));

        when(esRepository.aggregateQuantityTotals(
                storeIds,
                null,
                null,
                transactionFamilies,
                "2026-04-01",
                "2026-04-04",
                "SIOCS_MFCS"))
                .thenReturn(QuantityTotalsDto.builder()
                        .sourceQuantityTotal(new BigDecimal("100.0"))
                        .targetQuantityTotal(new BigDecimal("98.0"))
                        .quantityVarianceTotal(new BigDecimal("2.0"))
                        .quantityMetricsTransactionCount(10L)
                        .build());

        when(reconModuleService.resolveTargetSystem("SIOCS_MFCS", "SIM"))
                .thenReturn("MFCS");

        DashboardStats stats = reconQueryService.getDashboardStats(
                storeIds,
                null,
                transactionFamilies,
                "2026-04-01",
                "2026-04-04",
                "SIOCS_MFCS",
                null);

        assertEquals(10L, stats.getTotalTransactions());
        assertEquals(8L, stats.getMatched());
        assertEquals(2L, stats.getMissingInSiocs());
        assertEquals(Map.of("PO", 10L), stats.getByTransactionFamily());
        assertEquals(new BigDecimal("100.0"), stats.getSourceQuantityTotal());
        assertEquals(new BigDecimal("98.0"), stats.getTargetQuantityTotal());
        assertEquals(new BigDecimal("2.0"), stats.getQuantityVarianceTotal());
        assertEquals(10L, stats.getQuantityMetricsTransactionCount());
        assertNotNull(stats.getTransactionFamilyVolumes());
        assertEquals(1, stats.getTransactionFamilyVolumes().size());
        assertEquals("PO", stats.getTransactionFamilyVolumes().get(0).getTransactionFamily());
    }
}
