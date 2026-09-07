package com.litmood.interfaces.controller;

import com.litmood.application.service.AdminReportService;
import com.litmood.domain.model.Report;
import com.litmood.interfaces.dto.AdminDtos.AdminReportPage;
import com.litmood.interfaces.dto.AdminDtos.AdminReportResponse;
import com.litmood.interfaces.dto.AdminDtos.ReportResolutionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영 화면 (#28).
 *
 * <p>접근 통제는 {@code SecurityConfig} 의 {@code /api/v1/admin/**} 한 줄이 전부다 —
 * 운영자가 아니면 여기까지 오지 못하고, 403 이 아니라 404 로 응답한다(존재를 감춘다).
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "운영 — 신고 처리")
public class AdminController {

    private final AdminReportService adminReportService;

    public AdminController(AdminReportService adminReportService) {
        this.adminReportService = adminReportService;
    }

    @GetMapping("/reports")
    @Operation(
            summary = "신고 큐",
            description =
                    "최근 접수 순으로 돌려준다. status 를 비우면 상태를 가리지 않는다. "
                            + "각 항목에는 대상의 이름과, 같은 대상에 쌓인 신고 건수가 함께 실린다.")
    public AdminReportPage reports(
            @RequestParam(required = false) Report.ReportStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return adminReportService.queue(status, cursor, limit);
    }

    @PatchMapping("/reports/{id}")
    @Operation(summary = "신고 처리", description = "PENDING 인 신고만 처리할 수 있다. 이미 처리된 건은 409 다.")
    public AdminReportResponse resolve(
            @PathVariable Long id, @Valid @RequestBody ReportResolutionRequest request) {
        return adminReportService.resolve(id, request.status());
    }
}
