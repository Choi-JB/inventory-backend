package com.example.inventory.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.inventory.service.ManualIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import org.springframework.security.access.prepost.PreAuthorize;

@Tag(name = "매뉴얼 재적재 (ADMIN)", description = "ADMIN 전용. 매뉴얼 재적재")
@RestController
@RequestMapping("/api/admin/manuals")
public class ManualAdminController {
    private final ManualIngestionService manualIngestionService;

    public ManualAdminController(ManualIngestionService manualIngestionService) {
        this.manualIngestionService = manualIngestionService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "매뉴얼 재적재", description = "ADMIN 전용. 매뉴얼 재적재")
    @PostMapping("/reindex")
    public ResponseEntity<Integer> reindex() throws IOException {
        return ResponseEntity.ok(manualIngestionService.reindex());
    }
}
