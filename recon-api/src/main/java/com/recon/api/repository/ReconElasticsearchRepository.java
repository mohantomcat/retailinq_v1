package com.recon.api.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.QuantityTotalsDto;
import com.recon.api.domain.ReconSearchRequest;
import com.recon.api.domain.ReconSummary;
import com.recon.api.domain.TransactionFamilyVolumeDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ReconElasticsearchRepository {

    private static final String INDEX = "recon-transactions";

    private final ElasticsearchClient esClient;
    private final ObjectMapper objectMapper;

    public List<ReconSummary> search(ReconSearchRequest req) {
        try {
            List<Query> filters = buildFilters(req);
            Query finalQuery = toFilterQuery(filters);

            log.debug("ES search: storeIds={} wkstnIds={} transactionTypes={} transactionFamilies={} fromBusinessDate={} toBusinessDate={} reconStatus={} page={} size={}",
                    req.getStoreIds(),
                    req.getWkstnIds(),
                    req.getTransactionTypes(),
                    req.getTransactionFamilies(),
                    req.getFromBusinessDate(),
                    req.getToBusinessDate(),
                    req.getReconStatus(),
                    req.getPage(),
                    req.getSize());

            SearchRequest esReq = SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(finalQuery)
                    .sort(so -> so
                            .field(f -> f
                                    .field("reconciledAt")
                                    .order(SortOrder.Desc)))
                    .from(req.getPage() * req.getSize())
                    .size(req.getSize()));

            SearchResponse<Map> response = esClient.search(esReq, Map.class);

            List<ReconSummary> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                ReconSummary summary = objectMapper.convertValue(hit.source(), ReconSummary.class);
                enrichWkstnId(summary);
                results.add(summary);
            }
            return results;
        } catch (Exception e) {
            log.error("ES search failed: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public long count(String reconStatus,
                      List<String> storeIds,
                      String fromBusinessDate,
                      String toBusinessDate,
                      List<String> wkstnIds,
                      List<String> transactionTypes,
                      String reconView) {
        return count(reconStatus, storeIds, fromBusinessDate, toBusinessDate, wkstnIds, transactionTypes, null, reconView);
    }

    public long count(String reconStatus,
                      List<String> storeIds,
                      String fromBusinessDate,
                      String toBusinessDate,
                      List<String> wkstnIds,
                      List<String> transactionTypes,
                      List<String> transactionFamilies,
                      String reconView) {
        try {
            List<Query> filters = buildScopedFilters(
                    storeIds,
                    wkstnIds,
                    transactionTypes,
                    transactionFamilies,
                    fromBusinessDate,
                    toBusinessDate,
                    reconView);

            if (reconStatus != null && !reconStatus.isBlank()) {
                filters.add(Query.of(q -> q
                        .term(t -> t
                                .field("reconStatus.keyword")
                                .value(reconStatus))));
            }

            Query query = toFilterQuery(filters);
            var countReq = co.elastic.clients.elasticsearch.core.CountRequest.of(c -> c
                    .index(INDEX)
                    .query(query));

            return esClient.count(countReq).count();
        } catch (Exception e) {
            log.error("ES count failed: {}", e.getMessage(), e);
            return 0L;
        }
    }

    public long countByStatus(String status) {
        return count(status, null, null, null, null, null, null, null);
    }

    public long count(ReconSearchRequest req) {
        try {
            Query query = toFilterQuery(buildFilters(req));
            var countReq = co.elastic.clients.elasticsearch.core.CountRequest.of(c -> c
                    .index(INDEX)
                    .query(query));
            return esClient.count(countReq).count();
        } catch (Exception e) {
            log.error("ES count failed: {}", e.getMessage(), e);
            return 0L;
        }
    }

    public ReconSummary findByTransactionKey(String transactionKey, String reconView) {
        return findByTransactionKey(transactionKey, reconView, null);
    }

    public ReconSummary findByTransactionKey(String transactionKey,
                                            String reconView,
                                            List<String> reconViews) {
        try {
            SearchRequest esReq = SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(q -> q
                            .bool(b -> {
                                b.filter(f -> f
                                        .term(t -> t
                                                .field("transactionKey.keyword")
                                                .value(transactionKey)));
                                if (reconView != null && !reconView.isBlank()) {
                                    b.filter(f -> f
                                            .term(t -> t
                                                    .field("reconView.keyword")
                                                    .value(reconView)));
                                } else if (reconViews != null && !reconViews.isEmpty()) {
                                    b.filter(termsQuery("reconView.keyword", reconViews));
                                }
                                return b;
                            }))
                    .sort(so -> so
                            .field(f -> f
                                    .field("reconciledAt")
                                    .order(SortOrder.Desc)))
                    .size(1));

            SearchResponse<Map> response = esClient.search(esReq, Map.class);
            if (response.hits().hits().isEmpty()) {
                return null;
            }

            ReconSummary summary = objectMapper.convertValue(response.hits().hits().get(0).source(), ReconSummary.class);
            enrichWkstnId(summary);
            return summary;
        } catch (Exception e) {
            log.error("ES findByKey failed key={}: {}", transactionKey, e.getMessage());
            return null;
        }
    }

    public List<ReconSummary> findByTransactionKeys(List<String> transactionKeys) {
        if (transactionKeys == null || transactionKeys.isEmpty()) {
            return List.of();
        }
        try {
            SearchRequest esReq = SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(q -> q
                            .terms(t -> t
                                    .field("transactionKey.keyword")
                                    .terms(tv -> tv.value(
                                            transactionKeys.stream()
                                                    .filter(value -> value != null && !value.isBlank())
                                                    .distinct()
                                                    .map(FieldValue::of)
                                                    .collect(Collectors.toList())))))
                    .size(Math.max(transactionKeys.size(), 100)));

            SearchResponse<Map> response = esClient.search(esReq, Map.class);
            List<ReconSummary> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                ReconSummary summary = objectMapper.convertValue(hit.source(), ReconSummary.class);
                enrichWkstnId(summary);
                results.add(summary);
            }
            return results;
        } catch (Exception e) {
            log.error("ES findByKeys failed count={}: {}", transactionKeys.size(), e.getMessage(), e);
            return List.of();
        }
    }

    public Map<String, Long> aggregateByStatus(List<String> storeIds,
                                               List<String> wkstnIds,
                                               List<String> transactionTypes,
                                               String fromBusinessDate,
                                               String toBusinessDate,
                                               String reconView) {
        return aggregateByStatus(storeIds, wkstnIds, transactionTypes, null, fromBusinessDate, toBusinessDate, reconView);
    }

    public Map<String, Long> aggregateByStatus(List<String> storeIds,
                                               List<String> wkstnIds,
                                               List<String> transactionTypes,
                                               List<String> transactionFamilies,
                                               String fromBusinessDate,
                                               String toBusinessDate,
                                               String reconView) {
        try {
            Query query = toFilterQuery(buildScopedFilters(
                    storeIds,
                    wkstnIds,
                    transactionTypes,
                    transactionFamilies,
                    fromBusinessDate,
                    toBusinessDate,
                    reconView));

            SearchRequest esReq = SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(query)
                    .size(0)
                    .aggregations("by_status", a -> a
                            .terms(t -> t
                                    .field("reconStatus.keyword")
                                    .size(20))));

            SearchResponse<Void> response = esClient.search(esReq, Void.class);
            Map<String, Long> result = new HashMap<>();
            Aggregate aggregate = response.aggregations().get("by_status");
            if (aggregate == null || aggregate.sterms() == null) {
                return result;
            }

            for (var bucket : aggregate.sterms().buckets().array()) {
                result.put(bucket.key().stringValue(), bucket.docCount());
            }
            return result;
        } catch (Exception e) {
            log.error("ES aggregation failed: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    public Map<String, Long> aggregateByStore(List<String> storeIds,
                                              List<String> wkstnIds,
                                              List<String> transactionTypes,
                                              String fromBusinessDate,
                                              String toBusinessDate,
                                              String reconView) {
        return aggregateByStore(storeIds, wkstnIds, transactionTypes, null, fromBusinessDate, toBusinessDate, reconView);
    }

    public Map<String, Long> aggregateByStore(List<String> storeIds,
                                              List<String> wkstnIds,
                                              List<String> transactionTypes,
                                              List<String> transactionFamilies,
                                              String fromBusinessDate,
                                              String toBusinessDate,
                                              String reconView) {
        try {
            Query query = toFilterQuery(buildScopedFilters(
                    storeIds,
                    wkstnIds,
                    transactionTypes,
                    transactionFamilies,
                    fromBusinessDate,
                    toBusinessDate,
                    reconView));

            SearchRequest esReq = SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(query)
                    .size(0)
                    .aggregations("by_store", a -> a
                            .terms(t -> t
                                    .field("storeId.keyword")
                                    .size(1000))));

            SearchResponse<Void> response = esClient.search(esReq, Void.class);
            Map<String, Long> result = new HashMap<>();
            Aggregate aggregate = response.aggregations().get("by_store");
            if (aggregate == null || aggregate.sterms() == null) {
                return result;
            }

            for (var bucket : aggregate.sterms().buckets().array()) {
                result.put(bucket.key().stringValue(), bucket.docCount());
            }
            return result;
        } catch (Exception e) {
            log.error("ES store agg failed: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    public Map<String, Long> aggregateByTransactionFamily(List<String> storeIds,
                                                          List<String> wkstnIds,
                                                          List<String> transactionTypes,
                                                          List<String> transactionFamilies,
                                                          String fromBusinessDate,
                                                          String toBusinessDate,
                                                          String reconView) {
        try {
            Query query = toFilterQuery(buildScopedFilters(
                    storeIds,
                    wkstnIds,
                    transactionTypes,
                    transactionFamilies,
                    fromBusinessDate,
                    toBusinessDate,
                    reconView));

            SearchRequest esReq = SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(query)
                    .size(0)
                    .aggregations("by_family", a -> a
                            .terms(t -> t
                                    .field("transactionFamily.keyword")
                                    .size(20))));

            SearchResponse<Void> response = esClient.search(esReq, Void.class);
            Map<String, Long> result = new HashMap<>();
            Aggregate aggregate = response.aggregations().get("by_family");
            if (aggregate == null || aggregate.sterms() == null) {
                return result;
            }

            for (var bucket : aggregate.sterms().buckets().array()) {
                String family = bucket.key().stringValue();
                if (family != null && !family.isBlank()) {
                    result.put(family, bucket.docCount());
                }
            }
            return result;
        } catch (Exception e) {
            log.error("ES transaction-family aggregation failed: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    public List<TransactionFamilyVolumeDto> aggregateTransactionFamilyVolumes(List<String> storeIds,
                                                                              List<String> wkstnIds,
                                                                              List<String> transactionTypes,
                                                                              List<String> transactionFamilies,
                                                                              String fromBusinessDate,
                                                                              String toBusinessDate,
                                                                              String reconView) {
        try {
            Query query = toFilterQuery(buildScopedFilters(
                    storeIds,
                    wkstnIds,
                    transactionTypes,
                    transactionFamilies,
                    fromBusinessDate,
                    toBusinessDate,
                    reconView));

            SearchRequest esReq = SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(query)
                    .size(0)
                    .aggregations("by_family", a -> a
                            .terms(t -> t
                                    .field("transactionFamily.keyword")
                                    .size(20))
                            .aggregations("source_quantity_total", sub -> sub
                                    .sum(sum -> sum.field("sourceTotalQuantity")))
                            .aggregations("target_quantity_total", sub -> sub
                                    .sum(sum -> sum.field("targetTotalQuantity")))
                            .aggregations("quantity_variance_total", sub -> sub
                                    .sum(sum -> sum.field("quantityVariance")))
                            .aggregations("quantity_metrics_docs", sub -> sub
                                    .filter(f -> f
                                            .term(t -> t
                                                    .field("quantityMetricsAvailable")
                                                    .value(true))))
                            .aggregations("value_metrics_docs", sub -> sub
                                    .filter(f -> f
                                            .term(t -> t
                                                    .field("valueMetricsAvailable")
                                                    .value(true))))));

            SearchResponse<Void> response = esClient.search(esReq, Void.class);
            List<TransactionFamilyVolumeDto> results = new ArrayList<>();
            Aggregate aggregate = response.aggregations().get("by_family");
            if (aggregate == null || aggregate.sterms() == null) {
                return results;
            }

            for (var bucket : aggregate.sterms().buckets().array()) {
                String family = bucket.key().stringValue();
                if (family == null || family.isBlank()) {
                    continue;
                }
                results.add(TransactionFamilyVolumeDto.builder()
                        .transactionFamily(family)
                        .transactionCount(bucket.docCount())
                        .quantityMetricsTransactionCount(aggregateDocCount(bucket.aggregations(), "quantity_metrics_docs"))
                        .sourceQuantityTotal(aggregateBigDecimal(bucket.aggregations(), "source_quantity_total"))
                        .targetQuantityTotal(aggregateBigDecimal(bucket.aggregations(), "target_quantity_total"))
                        .quantityVarianceTotal(aggregateBigDecimal(bucket.aggregations(), "quantity_variance_total"))
                        .valueMetricsAvailable(aggregateDocCount(bucket.aggregations(), "value_metrics_docs") > 0)
                        .build());
            }
            return results;
        } catch (Exception e) {
            log.error("ES transaction-family volume aggregation failed: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public QuantityTotalsDto aggregateQuantityTotals(List<String> storeIds,
                                                     List<String> wkstnIds,
                                                     List<String> transactionTypes,
                                                     List<String> transactionFamilies,
                                                     String fromBusinessDate,
                                                     String toBusinessDate,
                                                     String reconView) {
        try {
            Query query = toFilterQuery(buildScopedFilters(
                    storeIds,
                    wkstnIds,
                    transactionTypes,
                    transactionFamilies,
                    fromBusinessDate,
                    toBusinessDate,
                    reconView));

            SearchRequest esReq = SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(query)
                    .size(0)
                    .aggregations("source_quantity_total", a -> a
                            .sum(sum -> sum.field("sourceTotalQuantity")))
                    .aggregations("target_quantity_total", a -> a
                            .sum(sum -> sum.field("targetTotalQuantity")))
                    .aggregations("quantity_variance_total", a -> a
                            .sum(sum -> sum.field("quantityVariance")))
                    .aggregations("quantity_metrics_docs", a -> a
                            .filter(f -> f
                                    .term(t -> t
                                            .field("quantityMetricsAvailable")
                                            .value(true)))));

            SearchResponse<Void> response = esClient.search(esReq, Void.class);
            return QuantityTotalsDto.builder()
                    .sourceQuantityTotal(aggregateBigDecimal(response.aggregations(), "source_quantity_total"))
                    .targetQuantityTotal(aggregateBigDecimal(response.aggregations(), "target_quantity_total"))
                    .quantityVarianceTotal(aggregateBigDecimal(response.aggregations(), "quantity_variance_total"))
                    .quantityMetricsTransactionCount(aggregateDocCount(response.aggregations(), "quantity_metrics_docs"))
                    .build();
        } catch (Exception e) {
            log.error("ES quantity totals aggregation failed: {}", e.getMessage(), e);
            return QuantityTotalsDto.builder()
                    .sourceQuantityTotal(BigDecimal.ZERO)
                    .targetQuantityTotal(BigDecimal.ZERO)
                    .quantityVarianceTotal(BigDecimal.ZERO)
                    .quantityMetricsTransactionCount(0L)
                    .build();
        }
    }

    public List<String> aggregateDistinct(String field) {
        return aggregateDistinct(field, null);
    }

    public List<String> aggregateDistinct(String field, String reconView) {
        return aggregateDistinct(field, reconView, null);
    }

    public List<String> aggregateDistinct(String field, String reconView, List<String> reconViews) {
        try {
            List<Query> filters = new ArrayList<>();
            addReconViewScopeFilter(filters, reconView, reconViews);

            SearchRequest esReq = SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(toFilterQuery(filters))
                    .size(0)
                    .aggregations("distinct_values", a -> a
                            .terms(t -> t
                                    .field(field)
                                    .size(1000))));

            SearchResponse<Void> response = esClient.search(esReq, Void.class);
            Aggregate aggregate = response.aggregations().get("distinct_values");
            if (aggregate == null || aggregate.sterms() == null) {
                return new ArrayList<>();
            }

            return aggregate.sterms().buckets().array().stream()
                    .map(b -> b.key().stringValue())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("ES distinct agg failed field={}: {}", field, e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<String> aggregateDistinctWithFilter(String field,
                                                    String filterField,
                                                    List<String> filterValues,
                                                    String reconView) {
        return aggregateDistinctWithFilter(field, filterField, filterValues, reconView, null);
    }

    public List<String> aggregateDistinctWithFilter(String field,
                                                    String filterField,
                                                    List<String> filterValues,
                                                    String reconView,
                                                    List<String> reconViews) {
        try {
            List<Query> filters = new ArrayList<>();
            addReconViewScopeFilter(filters, reconView, reconViews);
            if (filterValues != null && !filterValues.isEmpty()) {
                filters.add(termsQuery(filterField, filterValues));
            }

            SearchRequest esReq = SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(toFilterQuery(filters))
                    .size(0)
                    .aggregations("distinct_values", a -> a
                            .terms(t -> t
                                    .field(field)
                                    .size(1000))));

            SearchResponse<Void> response = esClient.search(esReq, Void.class);
            Aggregate aggregate = response.aggregations().get("distinct_values");
            if (aggregate == null || aggregate.sterms() == null) {
                return new ArrayList<>();
            }

            return aggregate.sterms().buckets().array().stream()
                    .map(b -> b.key().stringValue())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("ES distinct agg with filter failed: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public Map<String, Map<String, Long>> aggregateByBusinessDateAndStatus(List<String> storeIds,
                                                                           List<String> wkstnIds,
                                                                           List<String> transactionTypes,
                                                                           String fromBusinessDate,
                                                                           String toBusinessDate,
                                                                           String reconView) {
        return aggregateByBusinessDateAndStatus(storeIds, wkstnIds, transactionTypes, null, fromBusinessDate, toBusinessDate, reconView);
    }

    public Map<String, Map<String, Long>> aggregateByBusinessDateAndStatus(List<String> storeIds,
                                                                           List<String> wkstnIds,
                                                                           List<String> transactionTypes,
                                                                           List<String> transactionFamilies,
                                                                           String fromBusinessDate,
                                                                           String toBusinessDate,
                                                                           String reconView) {
        try {
            Query query = toFilterQuery(buildScopedFilters(
                    storeIds,
                    wkstnIds,
                    transactionTypes,
                    transactionFamilies,
                    fromBusinessDate,
                    toBusinessDate,
                    reconView));

            SearchRequest esReq = SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(query)
                    .size(0)
                    .aggregations("by_day", a -> a
                            .dateHistogram(h -> h
                                    .field("businessDate")
                                    .calendarInterval(co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval.Day)
                                    .format("yyyy-MM-dd"))
                            .aggregations("by_status", sub -> sub
                                    .terms(t -> t
                                            .field("reconStatus.keyword")
                                            .size(20)))));

            SearchResponse<Void> response = esClient.search(esReq, Void.class);
            Map<String, Map<String, Long>> result = new HashMap<>();

            Aggregate dayAgg = response.aggregations().get("by_day");
            if (dayAgg == null || dayAgg.dateHistogram() == null) {
                return result;
            }

            for (var bucket : dayAgg.dateHistogram().buckets().array()) {
                Map<String, Long> statusCounts = new HashMap<>();
                Aggregate subAgg = bucket.aggregations().get("by_status");
                if (subAgg != null && subAgg.sterms() != null) {
                    for (var statusBucket : subAgg.sterms().buckets().array()) {
                        statusCounts.put(statusBucket.key().stringValue(), statusBucket.docCount());
                    }
                }
                result.put(bucket.keyAsString(), statusCounts);
            }

            return result;
        } catch (Exception e) {
            log.error("ES business-date/status aggregation failed: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    public Map<String, Map<String, Long>> aggregateByFieldAndStatus(String field,
                                                                    int size,
                                                                    List<String> storeIds,
                                                                    List<String> wkstnIds,
                                                                    List<String> transactionTypes,
                                                                    String fromBusinessDate,
                                                                    String toBusinessDate,
                                                                    String reconView) {
        return aggregateByFieldAndStatus(field, size, storeIds, wkstnIds, transactionTypes, null, fromBusinessDate, toBusinessDate, reconView);
    }

    public Map<String, Map<String, Long>> aggregateByFieldAndStatus(String field,
                                                                    int size,
                                                                    List<String> storeIds,
                                                                    List<String> wkstnIds,
                                                                    List<String> transactionTypes,
                                                                    List<String> transactionFamilies,
                                                                    String fromBusinessDate,
                                                                    String toBusinessDate,
                                                                    String reconView) {
        try {
            Query query = toFilterQuery(buildScopedFilters(
                    storeIds,
                    wkstnIds,
                    transactionTypes,
                    transactionFamilies,
                    fromBusinessDate,
                    toBusinessDate,
                    reconView));

            SearchRequest esReq = SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(query)
                    .size(0)
                    .aggregations("primary", a -> a
                            .terms(t -> t
                                    .field(field.endsWith(".keyword") ? field : field + ".keyword")
                                    .size(size))
                            .aggregations("by_status", sub -> sub
                                    .terms(t -> t
                                            .field("reconStatus.keyword")
                                            .size(20)))));

            SearchResponse<Void> response = esClient.search(esReq, Void.class);
            Map<String, Map<String, Long>> result = new HashMap<>();
            Aggregate primaryAgg = response.aggregations().get("primary");
            if (primaryAgg == null || primaryAgg.sterms() == null) {
                return result;
            }

            for (var bucket : primaryAgg.sterms().buckets().array()) {
                Map<String, Long> statusCounts = new HashMap<>();
                Aggregate subAgg = bucket.aggregations().get("by_status");
                if (subAgg != null && subAgg.sterms() != null) {
                    for (var statusBucket : subAgg.sterms().buckets().array()) {
                        statusCounts.put(statusBucket.key().stringValue(), statusBucket.docCount());
                    }
                }
                result.put(bucket.key().stringValue(), statusCounts);
            }

            return result;
        } catch (Exception e) {
            log.error("ES field/status aggregation failed field={}: {}", field, e.getMessage(), e);
            return new HashMap<>();
        }
    }

    private List<Query> buildFilters(ReconSearchRequest req) {
        List<Query> filters = buildScopedFilters(
                req.getStoreIds(),
                req.getWkstnIds(),
                req.getTransactionTypes(),
                req.getTransactionFamilies(),
                req.getFromBusinessDate(),
                req.getToBusinessDate(),
                req.getReconView(),
                req.getReconViews());

        if (req.getReconStatus() != null && !req.getReconStatus().isBlank()) {
            filters.add(Query.of(q -> q
                    .term(t -> t
                            .field("reconStatus.keyword")
                            .value(req.getReconStatus()))));
        } else if (req.getReconStatuses() != null && !req.getReconStatuses().isEmpty()) {
            filters.add(termsQuery("reconStatus.keyword", req.getReconStatuses()));
        }

        if (req.getTransactionKey() != null && !req.getTransactionKey().isBlank()) {
            filters.add(Query.of(q -> q
                    .term(t -> t
                            .field("transactionKey.keyword")
                            .value(req.getTransactionKey()))));
        }

        if (req.getExternalId() != null && !req.getExternalId().isBlank()) {
            filters.add(Query.of(q -> q
                    .term(t -> t
                            .field("externalId.keyword")
                            .value(req.getExternalId()))));
        }

        if (req.getFromDate() != null && req.getToDate() != null) {
            filters.add(Query.of(q -> q
                    .range(r -> r
                            .field("reconciledAt")
                            .gte(JsonData.of(req.getFromDate()))
                            .lte(JsonData.of(req.getToDate())))));
        }

        return filters;
    }

    private List<Query> buildScopedFilters(List<String> storeIds,
                                           List<String> wkstnIds,
                                           List<String> transactionTypes,
                                           List<String> transactionFamilies,
                                           String fromBusinessDate,
                                           String toBusinessDate,
                                           String reconView) {
        return buildScopedFilters(
                storeIds,
                wkstnIds,
                transactionTypes,
                transactionFamilies,
                fromBusinessDate,
                toBusinessDate,
                reconView,
                null);
    }

    private List<Query> buildScopedFilters(List<String> storeIds,
                                           List<String> wkstnIds,
                                           List<String> transactionTypes,
                                           List<String> transactionFamilies,
                                           String fromBusinessDate,
                                           String toBusinessDate,
                                           String reconView,
                                           List<String> reconViews) {
        List<Query> filters = new ArrayList<>();
        addReconViewScopeFilter(filters, reconView, reconViews);
        addTermsFilter(filters, "storeId.keyword", storeIds);
        addTermsFilter(filters, "wkstnId.keyword", wkstnIds);
        addTermsFilter(filters, "transactionType.keyword", transactionTypes);
        addTermsFilter(filters, "transactionFamily.keyword", transactionFamilies);
        addBusinessDateFilter(filters, fromBusinessDate, toBusinessDate);
        return filters;
    }

    private void addReconViewFilter(List<Query> filters, String reconView) {
        addReconViewScopeFilter(filters, reconView, null);
    }

    private void addReconViewScopeFilter(List<Query> filters, String reconView, List<String> reconViews) {
        if (reconView != null && !reconView.isBlank()) {
            filters.add(Query.of(q -> q
                    .term(t -> t
                            .field("reconView.keyword")
                            .value(reconView))));
            return;
        }
        if (reconViews != null && !reconViews.isEmpty()) {
            filters.add(termsQuery("reconView.keyword", reconViews));
        }
    }

    private void addTermsFilter(List<Query> filters, String field, List<String> values) {
        if (values == null || values.stream().noneMatch(value -> value != null && !value.isBlank())) {
            return;
        }
        filters.add(termsQuery(field, values));
    }

    private Query termsQuery(String field, List<String> values) {
        return Query.of(q -> q
                .terms(t -> t
                        .field(field)
                        .terms(tv -> tv.value(
                                values.stream()
                                        .filter(value -> value != null && !value.isBlank())
                                        .map(FieldValue::of)
                                        .collect(Collectors.toList())))));
    }

    private Query toFilterQuery(List<Query> filters) {
        return filters.isEmpty()
                ? Query.of(q -> q.matchAll(m -> m))
                : Query.of(q -> q.bool(b -> b.filter(filters)));
    }

    private void addBusinessDateFilter(List<Query> filters, String from, String to) {
        if (from != null && !from.isBlank() && to != null && !to.isBlank()) {
            filters.add(Query.of(q -> q
                    .range(r -> r
                            .field("businessDate")
                            .gte(JsonData.of(from))
                            .lte(JsonData.of(to)))));
        } else if (from != null && !from.isBlank()) {
            filters.add(Query.of(q -> q
                    .range(r -> r
                            .field("businessDate")
                            .gte(JsonData.of(from)))));
        } else if (to != null && !to.isBlank()) {
            filters.add(Query.of(q -> q
                    .range(r -> r
                            .field("businessDate")
                            .lte(JsonData.of(to)))));
        }
    }

    private BigDecimal aggregateBigDecimal(Map<String, Aggregate> aggregations, String name) {
        Aggregate aggregate = aggregations.get(name);
        if (aggregate == null || aggregate.sum() == null) {
            return BigDecimal.ZERO;
        }
        double value = aggregate.sum().value();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value);
    }

    private long aggregateDocCount(Map<String, Aggregate> aggregations, String name) {
        Aggregate aggregate = aggregations.get(name);
        if (aggregate == null || aggregate.filter() == null) {
            return 0L;
        }
        return aggregate.filter().docCount();
    }

    private void enrichWkstnId(ReconSummary summary) {
        if (summary.getExternalId() != null && summary.getExternalId().length() == 22) {
            try {
                String raw = summary.getExternalId().substring(5, 8);
                summary.setWkstnId(String.valueOf(Integer.parseInt(raw)));
            } catch (Exception ignored) {
            }
        }
    }
}
