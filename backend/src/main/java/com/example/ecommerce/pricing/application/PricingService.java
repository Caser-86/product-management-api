package com.example.ecommerce.pricing.application;

import com.example.ecommerce.pricing.api.PriceUpdateRequest;
import com.example.ecommerce.pricing.domain.PriceCurrentEntity;
import com.example.ecommerce.pricing.domain.PriceCurrentRepository;
import com.example.ecommerce.pricing.domain.PriceHistoryEntity;
import com.example.ecommerce.pricing.domain.PriceHistoryRepository;
import com.example.ecommerce.product.domain.ProductSkuRepository;
import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class PricingService {

    private final PriceCurrentRepository priceCurrentRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final ProductSkuRepository productSkuRepository;

    public PricingService(
        PriceCurrentRepository priceCurrentRepository,
        PriceHistoryRepository priceHistoryRepository,
        ProductSkuRepository productSkuRepository
    ) {
        this.priceCurrentRepository = priceCurrentRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.productSkuRepository = productSkuRepository;
    }

    @Transactional
    public void updatePrice(Long skuId, PriceUpdateRequest request) {
        if (request.salePrice().compareTo(request.listPrice()) > 0) {
            throw new BusinessException(ErrorCode.PRICE_SALE_GREATER_THAN_LIST, "sale price cannot exceed list price");
        }
        var sku = productSkuRepository.findById(skuId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "sku not found"));
        var currentPrice = priceCurrentRepository.findById(skuId).orElse(null);
        priceCurrentRepository.save(
            PriceCurrentEntity.of(
                skuId,
                sku.getMerchantId(),
                request.listPrice(),
                request.salePrice()
            )
        );
        priceHistoryRepository.save(
            PriceHistoryEntity.manual(
                skuId,
                sku.getMerchantId(),
                currentPrice == null ? "{}" : PriceHistoryEntity.priceJson(currentPrice.getListPrice(), currentPrice.getSalePrice()),
                request.listPrice(),
                request.salePrice(),
                request.reason(),
                request.operatorId()
            )
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> history(Long skuId) {
        List<Map<String, Object>> items = priceHistoryRepository.findBySkuIdOrderByIdDesc(skuId).stream()
            .map(history -> Map.<String, Object>of(
                "changeType", history.getChangeType(),
                "oldPrice", normalizeJson(history.getOldPriceJson()),
                "newPrice", normalizeJson(history.getNewPriceJson()),
                "reason", history.getReason() == null ? "" : history.getReason(),
                "operatorId", history.getOperatorId() == null ? 0L : history.getOperatorId(),
                "createdAt", history.getCreatedAt() == null ? "" : history.getCreatedAt().toString()
            ))
            .toList();
        return Map.of("items", items);
    }

    public void applyScheduledPrice(Long scheduleId) {
        if (scheduleId == null || scheduleId <= 0) {
            throw new IllegalArgumentException("invalid schedule id");
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
}
