package com.example.ecommerce.inventory.application;

import com.example.ecommerce.inventory.domain.InventoryBalanceRepository;
import com.example.ecommerce.inventory.domain.InventoryReservationEntity;
import com.example.ecommerce.inventory.domain.InventoryReservationRepository;
import com.example.ecommerce.inventory.api.InventoryReservationRequest;
import com.example.ecommerce.shared.api.BusinessException;
import com.example.ecommerce.shared.api.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class InventoryService {

    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final InventoryReservationRepository inventoryReservationRepository;

    public InventoryService(
        InventoryBalanceRepository inventoryBalanceRepository,
        InventoryReservationRepository inventoryReservationRepository
    ) {
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.inventoryReservationRepository = inventoryReservationRepository;
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
        balance.reserve(item.quantity());
        inventoryReservationRepository.save(InventoryReservationEntity.reserved(reservationId, bizId, item.skuId(), item.quantity()));
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
        balance.confirm(reservation.getQuantity());
        reservation.confirm();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> snapshot(Long skuId) {
        var balance = inventoryBalanceRepository.findById(skuId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_VALIDATION_FAILED, "inventory not found"));
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
        balance.adjust(delta);
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
}
