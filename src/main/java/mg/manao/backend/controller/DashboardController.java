package mg.manao.backend.controller;

import lombok.RequiredArgsConstructor;
import mg.manao.backend.dto.DashboardStatsDTO;
import mg.manao.backend.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN uniquement (voir SecurityConfig) — pages/Dashboard.jsx. */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public DashboardStatsDTO stats() {
        return dashboardService.getStats();
    }
}
