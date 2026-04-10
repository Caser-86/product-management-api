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
    public int soldQty(Long skuId) {
        return inventoryBalanceRepository.findById(skuId)
            .orElseThrow(() -> new IllegalArgumentException("inventory not found"))
            .getSoldQty();
    }
}
