package com.example.ecommerce.pricing.application;

import com.example.ecommerce.pricing.api.PriceHistoryResponse;
import com.example.ecommerce.pricing.api.PriceScheduleRequest;
import com.example.ecommerce.pricing.api.PriceScheduleResponse;
import com.example.ecommerce.pricing.api.PriceUpdateRequest;
import com.example.ecommerce.pricing.domain.PriceCurrentEntity;
import com.example.ecommerce.pricing.domain.PriceCurrentRepository;
import com.example.ecommerce.pricing.domain.PriceHistoryEntity;
import com.example.ecommerce.pricing.domain.PriceHistoryRepository;
import com.example.ecommerce.pricing.domain.PriceScheduleEntity;
import com.example.ecommerce.pricing.domain.PriceScheduleRepository;
import com.example.ecommerce.product.domain.ProductSkuRepository;
import com.example.ecommerce.search.application.ProductSearchProjector;
import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;
import com.example.ecommerce.shared.auth.AuthContext;
import com.example.ecommerce.shared.auth.AuthContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PricingService {

    private final PriceCurrentRepository priceCurrentRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PriceScheduleRepository priceScheduleRepository;
    private final ProductSkuRepository productSkuRepository;
    private final ProductSearchProjector productSearchProjector;
    private final ObjectMapper objectMapper;

    public PricingService(
        PriceCurrentRepository priceCurrentRepository,
        PriceHistoryRepository priceHistoryRepository,
        PriceScheduleRepository priceScheduleRepository,
        ProductSkuRepository productSkuRepository,
        ProductSearchProjector productSearchProjector,
        ObjectMapper objectMapper
    ) {
        this.priceCurrentRepository = priceCurrentRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.priceScheduleRepository = priceScheduleRepository;
        this.productSkuRepository = productSkuRepository;
        this.productSearchProjector = productSearchProjector;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void updatePrice(Long skuId, PriceUpdateRequest request) {
        assertSkuScope(skuId);
        writePrice(
            skuId,
            request.listPrice(),
            request.salePrice(),
            request.reason(),
            request.operatorId(),
            "manual"
        );
    }

    @Transactional
    public PriceScheduleResponse createSchedule(Long skuId, PriceScheduleRequest request) {
        var sku = productSkuRepository.findById(skuId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "sku not found"));
        assertMerchantScope(sku.getMerchantId());
        validatePrice(request.listPrice(), request.salePrice());
        String payload = schedulePayload(request);
        PriceScheduleEntity schedule = priceScheduleRepository.save(
            PriceScheduleEntity.pending(skuId, sku.getMerchantId(), payload, request.effectiveAt(), request.expireAt())
        );
        return new PriceScheduleResponse(schedule.getId(), schedule.getStatus());
    }

    @Transactional(readOnly = true)
    public PriceHistoryResponse history(Long skuId, int page, int pageSize) {
        assertSkuScope(skuId);
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        var pageable = PageRequest.of(
            safePage - 1,
            safePageSize,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        var pageResult = priceHistoryRepository.findBySkuId(skuId, pageable);
        var items = pageResult.getContent().stream()
            .map(history -> new PriceHistoryResponse.Item(
                history.getChangeType(),
                parsePriceSnapshot(history.getOldPriceJson()),
                parsePriceSnapshot(history.getNewPriceJson()),
                history.getReason(),
                history.getOperatorId(),
                history.getCreatedAt()
            ))
            .toList();
        return new PriceHistoryResponse(items, safePage, safePageSize, pageResult.getTotalElements());
    }

    @Transactional
    public void applyScheduledPrice(Long scheduleId) {
        if (scheduleId == null || scheduleId <= 0) {
            throw new IllegalArgumentException("invalid schedule id");
        }
        PriceScheduleEntity schedule = priceScheduleRepository.findById(scheduleId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRICE_SCHEDULE_CONFLICT, "price schedule not found"));
        assertMerchantScope(schedule.getMerchantId());
        applyScheduledPriceInternal(schedule);
    }

    @Transactional
    public int applyDueSchedules(int limit) {
        if (limit <= 0) {
            return 0;
        }
        List<PriceScheduleEntity> dueSchedules = priceScheduleRepository.findByStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtAsc(
            "pending",
            LocalDateTime.now(),
            PageRequest.of(0, limit)
        );
        int applied = 0;
        for (PriceScheduleEntity schedule : dueSchedules) {
            applyScheduledPriceInternal(schedule);
            applied++;
        }
        return applied;
    }

    private void applyScheduledPriceInternal(PriceScheduleEntity schedule) {
        if (!schedule.isPending()) {
            return;
        }
        if (schedule.getEffectiveAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.PRICE_SCHEDULE_CONFLICT, "price schedule is not due");
        }

        JsonNode payload = parseJson(schedule.getTargetPriceJson());
        writePrice(
            schedule.getSkuId(),
            payload.path("listPrice").decimalValue(),
            payload.path("salePrice").decimalValue(),
            payload.path("reason").asText(""),
            payload.hasNonNull("operatorId") ? payload.path("operatorId").asLong() : null,
            "scheduled"
        );
        schedule.markApplied();
    }

    private void writePrice(
        Long skuId,
        BigDecimal listPrice,
        BigDecimal salePrice,
        String reason,
        Long operatorId,
        String changeType
    ) {
        validatePrice(listPrice, salePrice);
        var sku = productSkuRepository.findById(skuId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "sku not found"));
        var currentPrice = priceCurrentRepository.findById(skuId).orElse(null);
        String oldPriceJson = currentPrice == null
            ? "{}"
            : PriceHistoryEntity.priceJson(currentPrice.getListPrice(), currentPrice.getSalePrice());

        if (currentPrice == null) {
            priceCurrentRepository.save(PriceCurrentEntity.of(skuId, sku.getMerchantId(), listPrice, salePrice));
        } else {
            currentPrice.updatePrices(listPrice, salePrice);
        }

        PriceHistoryEntity history = "scheduled".equals(changeType)
            ? PriceHistoryEntity.scheduled(
                skuId,
                sku.getMerchantId(),
                oldPriceJson,
                listPrice,
                salePrice,
                reason,
                operatorId
            )
            : PriceHistoryEntity.manual(
                skuId,
                sku.getMerchantId(),
                oldPriceJson,
                listPrice,
                salePrice,
                reason,
                operatorId
            );
        priceHistoryRepository.save(history);
        refreshProjectionBySku(skuId);
    }

    private PriceHistoryResponse.PriceSnapshot parsePriceSnapshot(String rawJson) {
        JsonNode node = parseJson(rawJson);
        if (!node.hasNonNull("listPrice") && !node.hasNonNull("salePrice")) {
            return null;
        }
        return new PriceHistoryResponse.PriceSnapshot(
            node.path("listPrice").decimalValue(),
            node.path("salePrice").decimalValue()
        );
    }

    private void validatePrice(BigDecimal listPrice, BigDecimal salePrice) {
        if (salePrice.compareTo(listPrice) > 0) {
            throw new BusinessException(ErrorCode.PRICE_SALE_GREATER_THAN_LIST, "sale price cannot exceed list price");
        }
    }

    private String normalizeJson(String value) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"");
        }
        return value;
    }

    private String schedulePayload(PriceScheduleRequest request) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "listPrice", request.listPrice(),
                "salePrice", request.salePrice(),
                "reason", request.reason() == null ? "" : request.reason(),
                "operatorId", request.operatorId()
            ));
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.PRICE_SCHEDULE_CONFLICT, "failed to serialize price schedule");
        }
    }

    private JsonNode parseJson(String rawJson) {
        try {
            return objectMapper.readTree(normalizeJson(rawJson));
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.PRICE_SCHEDULE_CONFLICT, "invalid price schedule payload");
        }
    }

    private void assertSkuScope(Long skuId) {
        Long merchantId = productSkuRepository.findById(skuId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "sku not found"))
            .getMerchantId();
        assertMerchantScope(merchantId);
    }

    private void assertMerchantScope(Long merchantId) {
        AuthContext auth = AuthContextHolder.getRequired();
        if (!auth.isPlatformAdmin() && !auth.merchantId().equals(merchantId)) {
            throw new BusinessException(ErrorCode.AUTH_MERCHANT_SCOPE_DENIED, "merchant scope denied");
        }
    }

    private void refreshProjectionBySku(Long skuId) {
        Long productId = productSkuRepository.findById(skuId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "sku not found"))
            .getSpu()
            .getId();
        productSearchProjector.refresh(productId);
    }
}
