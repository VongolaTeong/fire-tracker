package com.firetracker.portfolio;

import com.firetracker.portfolio.dto.HoldingResponse;
import com.firetracker.portfolio.dto.PortfolioValueResponse;
import com.firetracker.portfolio.dto.ValuePoint;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only portfolio snapshot endpoints: current holdings and their SGD market value. */
@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioService service;

    public PortfolioController(PortfolioService service) {
        this.service = service;
    }

    @GetMapping("/holdings")
    public List<HoldingResponse> holdings() {
        return service.holdings();
    }

    @GetMapping("/value")
    public PortfolioValueResponse value() {
        return service.value();
    }

    /** Portfolio SGD value at each month-end from the first transaction through today. */
    @GetMapping("/value-history")
    public List<ValuePoint> valueHistory() {
        return service.valueHistory();
    }
}
