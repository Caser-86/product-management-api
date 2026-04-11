package com.example.ecommerce.inventory.application;

import com.example.ecommerce.inventory.api.InventoryAdjustmentResponse;
import com.example.ecommerce.inventory.api.InventoryRefundResponse;
import com.example.ecommerce.inventory.api.InventoryReservationResponse;
import com.example.ecommerce.inventory.api.InventoryHistoryResponse;
import com.example.ecommerce.inventory.api.InventorySnapshotResponse;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    public InventoryReservationResponse reserve(String reservationId, String bizId, List<InventoryReservationRequest.Item> items) {
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
            return new InventoryReservationResponse(existingReservation.get().getId(), existingReservation.get().getStatus());
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
        return new InventoryReservationResponse(reservationId, "reserved");
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

    @Transactional
    public InventoryReservationResponse release(String reservationId, String bizId) {
        var reservation = inventoryReservationRepository.findById(reservationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "reservation not found"));
        if (!reservation.hasBizId(bizId)) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "reservation bizId mismatch");
        }
        if (reservation.isConfirmed() || reservation.isReleased()) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "reservation cannot be released");
        }
        var balance = inventoryBalanceRepository.findById(reservation.getSkuId())
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "inventory not found"));
        assertMerchantScope(balance.getMerchantId());
        balance.release(reservation.getQuantity());
        reservation.release();
        inventoryLedgerRepository.save(
            InventoryLedgerEntity.of(
                reservation.getSkuId(),
                balance.getMerchantId(),
                "release",
                bizId,
                reservation.getQuantity(),
                -reservation.getQuantity()
            )
        );
        refreshProjectionBySku(reservation.getSkuId());
        return new InventoryReservationResponse(reservationId, "released");
    }

    @Transactional(readOnly = true)
    public InventorySnapshotResponse snapshot(Long skuId) {
        var balance = inventoryBalanceRepository.findById(skuId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "inventory not found"));
        assertMerchantScope(balance.getMerchantId());
        return new InventorySnapshotResponse(
            skuId,
            balance.getTotalQty(),
            balance.getAvailableQty(),
            balance.getReservedQty(),
            balance.getSoldQty()
        );
    }

    @Transactional
    public InventoryAdjustmentResponse adjust(Long skuId, int delta, String reason, Long operatorId) {
        var balance = inventoryBalanceRepository.findById(skuId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "inventory not found"));
        assertMerchantScope(balance.getMerchantId());
        balance.adjust(delta);
        inventoryLedgerRepository.save(
            InventoryLedgerEntity.of(skuId, balance.getMerchantId(), "adjust", reason == null ? "manual-adjust" : reason, delta, 0)
        );
        refreshProjectionBySku(skuId);
        return new InventoryAdjustmentResponse(
            skuId,
            balance.getTotalQty(),
            balance.getAvailableQty(),
            balance.getReservedQty(),
            balance.getSoldQty(),
            reason == null ? "" : reason,
            operatorId == null ? 0L : operatorId
        );
    }

    @Transactional
    public InventoryRefundResponse refund(Long skuId, String bizId, int quantity, boolean restock, String reason, Long operatorId) {
        if (bizId == null || bizId.isBlank()) {
            throw new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "bizId is required");
        }
        var balance = inventoryBalanceRepository.findById(skuId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "inventory not found"));
        assertMerchantScope(balance.getMerchantId());
        balance.refund(quantity, restock);
        inventoryLedgerRepository.save(
            InventoryLedgerEntity.of(
                skuId,
                balance.getMerchantId(),
                restock ? "refund_restock" : "refund_no_restock",
                bizId,
                restock ? quantity : 0,
                0
            )
        );
        refreshProjectionBySku(skuId);
        return new InventoryRefundResponse(
            skuId,
            balance.getTotalQty(),
            balance.getAvailableQty(),
            balance.getReservedQty(),
            balance.getSoldQty(),
            bizId,
            restock,
            reason == null ? "" : reason,
            operatorId == null ? 0L : operatorId
        );
    }

    @Transactional(readOnly = true)
    public InventoryHistoryResponse history(Long skuId, int page, int pageSize) {
        var balance = inventoryBalanceRepository.findById(skuId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "inventory not found"));
        assertMerchantScope(balance.getMerchantId());
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        var pageable = PageRequest.of(
            safePage - 1,
            safePageSize,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        var pageResult = inventoryLedgerRepository.findBySkuId(skuId, pageable);
        var items = pageResult.getContent().stream()
            .map(ledger -> new InventoryHistoryResponse.Item(
                ledger.getBizType(),
                ledger.getBizId(),
                ledger.getDeltaAvailable(),
                ledger.getDeltaReserved(),
                ledger.getCreatedAt()
            ))
            .toList();
        return new InventoryHistoryResponse(items, safePage, safePageSize, pageResult.getTotalElements());
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
