package com.example.ecommerce.inventory.application;

import com.example.ecommerce.inventory.domain.InventoryBalanceRepository;
import com.example.ecommerce.inventory.domain.InventoryReservationEntity;
import com.example.ecommerce.inventory.domain.InventoryReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public String reserve(Long skuId, int quantity, String bizId) {
        var balance = inventoryBalanceRepository.findById(skuId)
            .orElseThrow(() -> new IllegalArgumentException("inventory not found"));
        if (balance.getAvailableQty() < quantity) {
            throw new IllegalArgumentException("inventory insufficient");
        }
        balance.reserve(quantity);
        inventoryReservationRepository.save(InventoryReservationEntity.reserved(bizId, bizId, skuId, quantity));
        return bizId;
    }

    @Transactional
    public void confirm(String reservationId) {
        var reservation = inventoryReservationRepository.findById(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("reservation not found"));
        var balance = inventoryBalanceRepository.findById(reservation.getSkuId())
            .orElseThrow(() -> new IllegalArgumentException("inventory not found"));
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
