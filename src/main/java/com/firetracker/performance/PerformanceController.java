package com.firetracker.performance;

import com.firetracker.performance.dto.PerformanceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Portfolio performance: money-weighted return (XIRR), CAGR, total invested, unrealized P/L. */
@RestController
@RequestMapping("/api/portfolio")
public class PerformanceController {

    private final PerformanceService service;

    public PerformanceController(PerformanceService service) {
        this.service = service;
    }

    @GetMapping("/performance")
    public PerformanceResponse performance() {
        return service.performance();
    }
}
