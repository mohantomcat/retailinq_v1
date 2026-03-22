package com.recon.api.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.ReconSearchRequest;
import com.recon.api.domain.ReconSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

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

            Query finalQuery = filters.isEmpty()
                    ? Query.of(q -> q.matchAll(m -> m))
                    : Query.of(q -> q.bool(b -> b.filter(filters)));

            log.debug("ES search: storeIds={} wkstnIds={} fromBusinessDate={} " +
                            "toBusinessDate={} reconStatus={} page={} size={}",
                    req.getStoreIds(), req.getWkstnIds(),
                    req.getFromBusinessDate(), req.getToBusinessDate(),
                    req.getReconStatus(), req.getPage(), req.getSize());

            SearchRequest esReq = SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(finalQuery)
                    .sort(so -> so
                            .field(f -> f
                                    .field("reconciledAt")
                                    .order(SortOrder.Desc)))
                    .from(req.getPage() * req.getSize())
                    .size(req.getSize()));

            SearchResponse<Map> response =
                    esClient.search(esReq, Map.class);

            List<ReconSummary> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                ReconSummary summary = objectMapper.convertValue(
                        hit.source(), ReconSummary.class);
                enrichWkstnId(summary);
                results.add(summary);
            }
            return results;

        } catch (Exception e) {
            log.error("ES search failed: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public long count(String reconStatus, List<String> storeIds,
                      String fromBusinessDate, String toBusinessDate,
                      List<String> wkstnIds,
                      String reconView) {
        try {
            List<Query> filters = new ArrayList<>();

            if (reconStatus != null && !reconStatus.isBlank()) {
                filters.add(Query.of(q -> q
                        .term(t -> t
                                .field("reconStatus.keyword")
                                .value(reconStatus))));
            }
            if (reconView != null && !reconView.isBlank()) {
                filters.add(Query.of(q -> q
                        .term(t -> t
                                .field("reconView.keyword")
                                .value(reconView))));
            }
            if (storeIds != null && !storeIds.isEmpty()) {
                filters.add(Query.of(q -> q
                        .terms(t -> t
                                .field("storeId.keyword")
                                .terms(tv -> tv.value(
                                        storeIds.stream()
                                                .map(FieldValue::of)
                                                .collect(Collectors.toList()))))));
            }
            if (wkstnIds != null && !wkstnIds.isEmpty()) {
                filters.add(Query.of(q -> q
                        .terms(t -> t
                                .field("wkstnId.keyword")
                                .terms(tv -> tv.value(
                                        wkstnIds.stream()
                                                .map(FieldValue::of)
                                                .collect(Collectors.toList()))))));
            }
            addBusinessDateFilter(filters, fromBusinessDate, toBusinessDate);

            Query query = filters.isEmpty()
                    ? Query.of(q -> q.matchAll(m -> m))
                    : Query.of(q -> q.bool(b -> b.filter(filters)));

            var countReq = co.elastic.clients.elasticsearch
                    .core.CountRequest.of(c -> c
                            .index(INDEX)
                            .query(query));

            return esClient.count(countReq).count();

        } catch (Exception e) {
            log.error("ES count failed: {}", e.getMessage(), e);
            return 0L;
        }
    }

    public long countByStatus(String status) {
        return count(status, null, null, null, null, null);
    }

    public ReconSummary findByTransactionKey(String transactionKey) {
        try {
            SearchRequest esReq = SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(q -> q
                            .term(t -> t
                                    .field("transactionKey.keyword")
                                    .value(transactionKey)))
                    .size(1));

            SearchResponse<Map> response =
                    esClient.search(esReq, Map.class);

            if (response.hits().hits().isEmpty()) return null;

            ReconSummary summary = objectMapper.convertValue(
                    response.hits().hits().get(0).source(),
                    ReconSummary.class);
            enrichWkstnId(summary);
            return summary;

        } catch (Exception e) {
            log.error("ES findByKey failed key={}: {}",
                    transactionKey, e.getMessage());
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

    public Map<String, Long> aggregateByStatus(
            List<String> storeIds,
            List<String> wkstnIds,
            String fromBusinessDate,
            String toBusinessDate,
            String reconView) {
        try {
            List<Query> filters = new ArrayList<>();
            if (reconView != null && !reconView.isBlank()) {
                filters.add(Query.of(q -> q
                        .term(t -> t
                                .field("reconView.keyword")
                                .value(reconView))));
            }
            if (storeIds != null && !storeIds.isEmpty()) {
                filters.add(Query.of(q -> q
                        .terms(t -> t
                                .field("storeId.keyword")
                                .terms(tv -> tv.value(
                                        storeIds.stream()
                                                .map(FieldValue::of)
                                                .collect(Collectors.toList()))))));
            }
            if (wkstnIds != null && !wkstnIds.isEmpty()) {
                filters.add(Query.of(q -> q
                        .terms(t -> t
                                .field("wkstnId.keyword")
                                .terms(tv -> tv.value(
                                        wkstnIds.stream()
                                                .map(FieldValue::of)
                                                .collect(Collectors.toList()))))));
            }
            addBusinessDateFilter(filters, fromBusinessDate, toBusinessDate);

            Query query = filters.isEmpty()
                    ? Query.of(q -> q.matchAll(m -> m))
                    : Query.of(q -> q.bool(b -> b.filter(filters)));

            SearchRequest esReq = SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(query)
                    .size(0)
                    .aggregations("by_status", a -> a
                            .terms(t -> t
                                    .field("reconStatus.keyword")
                                    .size(20))));

            SearchResponse<Void> response =
                    esClient.search(esReq, Void.class);

            Map<String, Long> result = new java.util.HashMap<>();
            for (var bucket : response.aggregations()
                    .get("by_status").sterms().buckets().array()) {
                result.put(bucket.key().stringValue(),
                        bucket.docCount());
            }
            return result;

        } catch (Exception e) {
            log.error("ES aggregation failed: {}", e.getMessage());
            return new java.util.HashMap<>();
        }
    }

    public Map<String, Long> aggregateByStore(
            List<String> storeIds,
            List<String> wkstnIds,
            String fromBusinessDate,
            String toBusinessDate,
            String reconView) {
        try {
            List<Query> filters = new ArrayList<>();
            if (reconView != null && !reconView.isBlank()) {
                filters.add(Query.of(q -> q
                        .term(t -> t
                                .field("reconView.keyword")
                                .value(reconView))));
            }
            if (storeIds != null && !storeIds.isEmpty()) {
                filters.add(Query.of(q -> q
                        .terms(t -> t
                                .field("storeId.keyword")
                                .terms(tv -> tv.value(
                                        storeIds.stream()
                                                .map(FieldValue::of)
                                                .collect(Collectors.toList()))))));
            }
            if (wkstnIds != null && !wkstnIds.isEmpty()) {
                filters.add(Query.of(q -> q
                        .terms(t -> t
                                .field("wkstnId.keyword")
                                .terms(tv -> tv.value(
                                        wkstnIds.stream()
                                                .map(FieldValue::of)
                                                .collect(Collectors.toList()))))));
            }
            addBusinessDateFilter(filters, fromBusinessDate, toBusinessDate);

            Query query = filters.isEmpty()
                    ? Query.of(q -> q.matchAll(m -> m))
                    : Query.of(q -> q.bool(b -> b.filter(filters)));

            SearchRequest esReq = SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(query)
                    .size(0)
                    .aggregations("by_store", a -> a
                            .terms(t -> t
                                    .field("storeId.keyword")
                                    .size(1000))));

            SearchResponse<Void> response =
                    esClient.search(esReq, Void.class);

            Map<String, Long> result = new java.util.HashMap<>();
            for (var bucket : response.aggregations()
                    .get("by_store").sterms().buckets().array()) {
                result.put(bucket.key().stringValue(),
                        bucket.docCount());
            }
            return result;

        } catch (Exception e) {
            log.error("ES store agg failed: {}", e.getMessage());
            return new java.util.HashMap<>();
        }
    }

    public List<String> aggregateDistinct(String field) {
        return aggregateDistinct(field, null);
    }

    public List<String> aggregateDistinct(String field, String reconView) {
        try {
            List<Query> filters = new ArrayList<>();
            if (reconView != null && !reconView.isBlank()) {
                filters.add(Query.of(q -> q
                        .term(t -> t
                                .field("reconView.keyword")
                                .value(reconView))));
            }

            Query query = filters.isEmpty()
                    ? Query.of(q -> q.matchAll(m -> m))
                    : Query.of(q -> q.bool(b -> b.filter(filters)));

            SearchRequest esReq = SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(query)
                    .size(0)
                    .aggregations("distinct_values", a -> a
                            .terms(t -> t
                                    .field(field)
                                    .size(1000))));

            SearchResponse<Void> response =
                    esClient.search(esReq, Void.class);

            return response.aggregations()
                    .get("distinct_values")
                    .sterms().buckets().array()
                    .stream()
                    .map(b -> b.key().stringValue())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("ES distinct agg failed field={}: {}",
                    field, e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<String> aggregateDistinctWithFilter(
            String field, String filterField,
            List<String> filterValues,
            String reconView) {
        try {
            List<Query> filters = new ArrayList<>();
            if (reconView != null && !reconView.isBlank()) {
                filters.add(Query.of(q -> q
                        .term(t -> t
                                .field("reconView.keyword")
                                .value(reconView))));
            }
            if (filterValues != null && !filterValues.isEmpty()) {
                filters.add(Query.of(q -> q
                        .terms(t -> t
                                .field(filterField)
                                .terms(tv -> tv.value(
                                        filterValues.stream()
                                                .map(FieldValue::of)
                                                .collect(Collectors.toList()))))));
            }

            Query query = filters.isEmpty()
                    ? Query.of(q -> q.matchAll(m -> m))
                    : Query.of(q -> q.bool(b -> b.filter(filters)));

            SearchRequest esReq = SearchRequest.of(s -> s
                    .index(INDEX)
                    .query(query)
                    .size(0)
                    .aggregations("distinct_values", a -> a
                            .terms(t -> t
                                    .field(field)
                                    .size(1000))));

            SearchResponse<Void> response =
                    esClient.search(esReq, Void.class);

            return response.aggregations()
                    .get("distinct_values")
                    .sterms().buckets().array()
                    .stream()
                    .map(b -> b.key().stringValue())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("ES distinct agg with filter failed: {}",
                    e.getMessage());
            return new ArrayList<>();
        }
    }

    // ── Private helpers ────────────────────────────────────

    public Map<String, Map<String, Long>> aggregateByBusinessDateAndStatus(
            List<String> storeIds,
            List<String> wkstnIds,
            String fromBusinessDate,
            String toBusinessDate,
            String reconView) {
        try {
            List<Query> filters = new ArrayList<>();
            if (reconView != null && !reconView.isBlank()) {
                filters.add(Query.of(q -> q
                        .term(t -> t
                                .field("reconView.keyword")
                                .value(reconView))));
            }
            if (storeIds != null && !storeIds.isEmpty()) {
                filters.add(Query.of(q -> q
                        .terms(t -> t
                                .field("storeId.keyword")
                                .terms(tv -> tv.value(
                                        storeIds.stream()
                                                .map(FieldValue::of)
                                                .collect(Collectors.toList()))))));
            }
            if (wkstnIds != null && !wkstnIds.isEmpty()) {
                filters.add(Query.of(q -> q
                        .terms(t -> t
                                .field("wkstnId.keyword")
                                .terms(tv -> tv.value(
                                        wkstnIds.stream()
                                                .map(FieldValue::of)
                                                .collect(Collectors.toList()))))));
            }
            addBusinessDateFilter(filters, fromBusinessDate, toBusinessDate);

            Query query = filters.isEmpty()
                    ? Query.of(q -> q.matchAll(m -> m))
                    : Query.of(q -> q.bool(b -> b.filter(filters)));

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

            var dayAgg = response.aggregations().get("by_day");
            if (dayAgg == null || dayAgg.dateHistogram() == null) {
                return result;
            }

            for (var bucket : dayAgg.dateHistogram().buckets().array()) {
                Map<String, Long> statusCounts = new HashMap<>();
                var subAgg = bucket.aggregations().get("by_status");
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

    public Map<String, Map<String, Long>> aggregateByFieldAndStatus(
            String field,
            int size,
            List<String> storeIds,
            List<String> wkstnIds,
            String fromBusinessDate,
            String toBusinessDate,
            String reconView) {
        try {
            List<Query> filters = new ArrayList<>();
            if (reconView != null && !reconView.isBlank()) {
                filters.add(Query.of(q -> q
                        .term(t -> t
                                .field("reconView.keyword")
                                .value(reconView))));
            }
            if (storeIds != null && !storeIds.isEmpty()) {
                filters.add(Query.of(q -> q
                        .terms(t -> t
                                .field("storeId.keyword")
                                .terms(tv -> tv.value(
                                        storeIds.stream()
                                                .map(FieldValue::of)
                                                .collect(Collectors.toList()))))));
            }
            if (wkstnIds != null && !wkstnIds.isEmpty()) {
                filters.add(Query.of(q -> q
                        .terms(t -> t
                                .field("wkstnId.keyword")
                                .terms(tv -> tv.value(
                                        wkstnIds.stream()
                                                .map(FieldValue::of)
                                                .collect(Collectors.toList()))))));
            }
            addBusinessDateFilter(filters, fromBusinessDate, toBusinessDate);

            Query query = filters.isEmpty()
                    ? Query.of(q -> q.matchAll(m -> m))
                    : Query.of(q -> q.bool(b -> b.filter(filters)));

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
            var primaryAgg = response.aggregations().get("primary");
            if (primaryAgg == null || primaryAgg.sterms() == null) {
                return result;
            }

            for (var bucket : primaryAgg.sterms().buckets().array()) {
                Map<String, Long> statusCounts = new HashMap<>();
                var subAgg = bucket.aggregations().get("by_status");
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
        List<Query> filters = new ArrayList<>();

        // Multi-store filter
        if (req.getStoreIds() != null && !req.getStoreIds().isEmpty()) {
            filters.add(Query.of(q -> q
                    .terms(t -> t
                            .field("storeId.keyword")
                            .terms(tv -> tv.value(
                                    req.getStoreIds().stream()
                                            .map(FieldValue::of)
                                            .collect(Collectors.toList()))))));
        }

        // Multi-register filter
        if (req.getWkstnIds() != null && !req.getWkstnIds().isEmpty()) {
            filters.add(Query.of(q -> q
                    .terms(t -> t
                            .field("wkstnId.keyword")
                            .terms(tv -> tv.value(
                                    req.getWkstnIds().stream()
                                            .map(FieldValue::of)
                                            .collect(Collectors.toList()))))));
        }

        // Status filter
        if (req.getReconStatus() != null
                && !req.getReconStatus().isBlank()) {
            filters.add(Query.of(q -> q
                    .term(t -> t
                            .field("reconStatus.keyword")
                            .value(req.getReconStatus()))));
        }

        if (req.getReconView() != null
                && !req.getReconView().isBlank()) {
            filters.add(Query.of(q -> q
                    .term(t -> t
                            .field("reconView.keyword")
                            .value(req.getReconView()))));
        }

        // Business date range filter
        addBusinessDateFilter(filters,
                req.getFromBusinessDate(),
                req.getToBusinessDate());

        // Transaction key filter
        if (req.getTransactionKey() != null
                && !req.getTransactionKey().isBlank()) {
            filters.add(Query.of(q -> q
                    .term(t -> t
                            .field("transactionKey.keyword")
                            .value(req.getTransactionKey()))));
        }

        // reconciledAt timestamp range filter
        if (req.getFromDate() != null && req.getToDate() != null) {
            filters.add(Query.of(q -> q
                    .range(r -> r
                            .field("reconciledAt")
                            .gte(JsonData.of(req.getFromDate()))
                            .lte(JsonData.of(req.getToDate())))));
        }

        return filters;
    }

    private void addBusinessDateFilter(List<Query> filters,
                                       String from, String to) {
        if (from != null && !from.isBlank()
                && to != null && !to.isBlank()) {
            // Date range
            filters.add(Query.of(q -> q
                    .range(r -> r
                            .field("businessDate")
                            .gte(JsonData.of(from))
                            .lte(JsonData.of(to)))));
        } else if (from != null && !from.isBlank()) {
            // From only
            filters.add(Query.of(q -> q
                    .range(r -> r
                            .field("businessDate")
                            .gte(JsonData.of(from)))));
        } else if (to != null && !to.isBlank()) {
            // To only
            filters.add(Query.of(q -> q
                    .range(r -> r
                            .field("businessDate")
                            .lte(JsonData.of(to)))));
        }
    }

    private void enrichWkstnId(ReconSummary summary) {
        // Always derive wkstnId from externalId for consistency
        // externalId format: 0{4-storeId}{3-wkstn}{6-seq}{8-date}
        if (summary.getExternalId() != null
                && summary.getExternalId().length() == 22) {
            try {
                String raw = summary.getExternalId().substring(5, 8);
                summary.setWkstnId(
                        String.valueOf(Integer.parseInt(raw)));
            } catch (Exception ignored) {
            }
        }
    }
}
