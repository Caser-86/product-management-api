package com.example.ecommerce.pricing.application;

import com.example.ecommerce.pricing.api.PriceUpdateRequest;
import com.example.ecommerce.pricing.domain.PriceCurrentEntity;
import com.example.ecommerce.pricing.domain.PriceCurrentRepository;
import com.example.ecommerce.pricing.domain.PriceHistoryEntity;
import com.example.ecommerce.pricing.domain.PriceHistoryRepository;
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

    public PricingService(PriceCurrentRepository priceCurrentRepository, PriceHistoryRepository priceHistoryRepository) {
        this.priceCurrentRepository = priceCurrentRepository;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    @Transactional
    public void updatePrice(Long skuId, PriceUpdateRequest request) {
        if (request.salePrice() > request.listPrice()) {
            throw new BusinessException(ErrorCode.PRICE_SALE_GREATER_THAN_LIST, "sale price cannot exceed list price");
        }
        priceCurrentRepository.save(
            PriceCurrentEntity.of(
                skuId,
                2001L,
                BigDecimal.valueOf(request.listPrice()),
                BigDecimal.valueOf(request.salePrice())
            )
        );
        priceHistoryRepository.save(PriceHistoryEntity.manual(skuId, request.listPrice(), request.salePrice(), request.reason(), request.operatorId()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> history(Long skuId) {
        List<Map<String, Object>> items = priceHistoryRepository.findBySkuIdOrderByIdDesc(skuId).stream()
            .map(history -> Map.<String, Object>of("reason", history.getReason()))
            .toList();
        return Map.of("items", items);
    }

    public void applyScheduledPrice(Long scheduleId) {
        if (scheduleId == null || scheduleId <= 0) {
            throw new IllegalArgumentException("invalid schedule id");
        }
    }
}
