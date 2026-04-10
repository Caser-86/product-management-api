package com.example.ecommerce.inventory.application;

import com.example.ecommerce.inventory.domain.InventoryBalanceRepository;
import com.example.ecommerce.inventory.domain.InventoryLedgerEntity;
import com.example.ecommerce.inventory.domain.InventoryLedgerRepository;
import com.example.ecommerce.inventory.domain.InventoryReservationEntity;
import com.example.ecommerce.inventory.domain.InventoryReservationRepository;
import com.example.ecommerce.inventory.api.InventoryReservationRequest;
import com.example.ecommerce.product.domain.ProductSkuRepository;
import com.example.ecommerce.search.application.ProductSearchProjector;
import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;
import com.example.ecommerce.shared.auth.AuthContext;
import com.example.ecommerce.shared.auth.AuthContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class InventoryService {

    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final InventoryLedgerRepository inventoryLedgerRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final ProductSkuRepository productSkuRepository;
    private final ProductSearchProjector productSearchProjector;

    public InventoryService(
        InventoryBalanceRepository inventoryBalanceRepository,
        InventoryLedgerRepository inventoryLedgerRepository,
        InventoryReservationRepository inventoryReservationRepository,
        ProductSkuRepository productSkuRepository,
        ProductSearchProjector productSearchProjector
    ) {
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.inventoryLedgerRepository = inventoryLedgerRepository;
        this.inventoryReservationRepository = inventoryReservationRepository;
        this.productSkuRepository = productSkuRepository;
        this.productSearchProjector = productSearchProjector;
    }

    @Transactional
    public String reserve(String reservationId, String bizId, List<InventoryReservationRequest.Item> items) {
        if (reservationId == null || reservationId.isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "idempotencyKey is required");
        }
        if (bizId == null || bizId.isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "bizId is required");
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "reservation items are required");
        }
        if (items.size() != 1) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "single-item reservation only");
        }

        var existingReservation = inventoryReservationRepository.findById(reservationId)
            .or(() -> inventoryReservationRepository.findByBizId(bizId));
        if (existingReservation.isPresent()) {
            return existingReservation.get().getId();
        }

        InventoryReservationRequest.Item item = items.get(0);
        var balance = inventoryBalanceRepository.findById(item.skuId())
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "inventory not found"));
        assertMerchantScope(balance.getMerchantId());
        balance.reserve(item.quantity());
        inventoryReservationRepository.save(InventoryReservationEntity.reserved(reservationId, bizId, item.skuId(), item.quantity()));
        inventoryLedgerRepository.save(
            InventoryLedgerEntity.of(item.skuId(), balance.getMerchantId(), "reserve", bizId, -item.quantity(), item.quantity())
        );
        refreshProjectionBySku(item.skuId());
        return reservationId;
    }

    @Transactional
    public void confirm(String reservationId, String bizId) {
        var reservation = inventoryReservationRepository.findById(reservationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "reservation not found"));
        if (!reservation.hasBizId(bizId)) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "reservation bizId mismatch");
        }
        if (reservation.isConfirmed()) {
            return;
        }
        var balance = inventoryBalanceRepository.findById(reservation.getSkuId())
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "inventory not found"));
        assertMerchantScope(balance.getMerchantId());
        balance.confirm(reservation.getQuantity());
        reservation.confirm();
        inventoryLedgerRepository.save(
            InventoryLedgerEntity.of(
                reservation.getSkuId(),
                balance.getMerchantId(),
                "confirm",
                bizId,
                0,
                -reservation.getQuantity()
            )
        );
        refreshProjectionBySku(reservation.getSkuId());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> snapshot(Long skuId) {
        var balance = inventoryBalanceRepository.findById(skuId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "inventory not found"));
        assertMerchantScope(balance.getMerchantId());
        return Map.of(
            "skuId", skuId,
            "totalQty", balance.getTotalQty(),
            "availableQty", balance.getAvailableQty(),
            "reservedQty", balance.getReservedQty(),
            "soldQty", balance.getSoldQty()
        );
    }

    @Transactional
    public Map<String, Object> adjust(Long skuId, int delta, String reason, Long operatorId) {
        var balance = inventoryBalanceRepository.findById(skuId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "inventory not found"));
        assertMerchantScope(balance.getMerchantId());
        balance.adjust(delta);
        inventoryLedgerRepository.save(
            InventoryLedgerEntity.of(skuId, balance.getMerchantId(), "adjust", reason == null ? "manual-adjust" : reason, delta, 0)
        );
        refreshProjectionBySku(skuId);
        return Map.of(
            "skuId", skuId,
            "totalQty", balance.getTotalQty(),
            "availableQty", balance.getAvailableQty(),
            "reservedQty", balance.getReservedQty(),
            "soldQty", balance.getSoldQty(),
            "reason", reason == null ? "" : reason,
            "operatorId", operatorId == null ? 0L : operatorId
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> history(Long skuId) {
        var balance = inventoryBalanceRepository.findById(skuId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "inventory not found"));
        assertMerchantScope(balance.getMerchantId());
        List<Map<String, Object>> items = inventoryLedgerRepository.findBySkuIdOrderByIdDesc(skuId).stream()
            .map(ledger -> Map.<String, Object>of(
                "bizType", ledger.getBizType(),
                "bizId", ledger.getBizId(),
                "deltaAvailable", ledger.getDeltaAvailable(),
                "deltaReserved", ledger.getDeltaReserved(),
                "createdAt", ledger.getCreatedAt() == null ? "" : ledger.getCreatedAt().toString()
            ))
            .toList();
        return Map.of("items", items);
    }

    private void assertMerchantScope(Long resourceMerchantId) {
        AuthContext auth = AuthContextHolder.getRequired();
        if (!auth.isPlatformAdmin() && !auth.merchantId().equals(resourceMerchantId)) {
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
