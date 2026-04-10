package com.example.ecommerce.search.api;

import com.example.ecommerce.shared.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class StorefrontProductController {

    @GetMapping("/products")
    public ApiResponse<StorefrontSearchResponse> search(@RequestParam String keyword) {
        return ApiResponse.success(
            new StorefrontSearchResponse(
                List.of(new StorefrontSearchResponse.Item(1001L, keyword + "卫衣", 99.0, 159.0, "in_stock")),
                1,
                20,
                1
            )
        );
    }
}
