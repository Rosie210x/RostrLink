package com.rostrlink.controller;

import com.rostrlink.service.DashboardAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminDashboardController {

    private final DashboardAdminService dashboardAdminService;

    @GetMapping({"/dashboard"})
    public String viewDashboard(Model model) {
         dashboardData = dashboardAdminService.getAdminDashboardData();
        model.addAttribute("dashboardData", dashboardData);
        return "admin/admin-darhboard";
    }
}









