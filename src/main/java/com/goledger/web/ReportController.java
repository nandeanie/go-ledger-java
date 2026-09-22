package com.goledger.web;

import com.goledger.domain.ApiKeyScope;
import com.goledger.security.AuthContext;
import com.goledger.service.ReportService;
import com.goledger.web.dto.CurrencyTotal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/trial-balance")
    public List<CurrencyTotal> trialBalance() {
        AuthContext.current().requireScope(ApiKeyScope.READ);
        return reportService.trialBalance(AuthContext.current().tenantId());
    }
}
