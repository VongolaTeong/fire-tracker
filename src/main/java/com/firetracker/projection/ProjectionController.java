package com.firetracker.projection;

import com.firetracker.projection.dto.ProjectionResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Monte Carlo FIRE projection: a p10/p50/p90 fan from today out to the requested target date. */
@RestController
@RequestMapping("/api/portfolio")
public class ProjectionController {

    private final ProjectionService service;

    public ProjectionController(ProjectionService service) {
        this.service = service;
    }

    @GetMapping("/projection")
    public ProjectionResponse projection(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {
        return service.project(targetDate);
    }
}
