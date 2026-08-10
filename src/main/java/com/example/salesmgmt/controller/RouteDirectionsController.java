package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.RouteCode;
import com.example.salesmgmt.domain.RouteDirectionsResponse;
import com.example.salesmgmt.domain.RouteOptimizationResponse;
import com.example.salesmgmt.service.NaverDirectionsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routes")
public class RouteDirectionsController {

    private final NaverDirectionsService service;
    private final com.example.salesmgmt.service.RouteOptimizationService optimizationService;

    public RouteDirectionsController(
            NaverDirectionsService service,
            com.example.salesmgmt.service.RouteOptimizationService optimizationService
    ) {
        this.service = service;
        this.optimizationService = optimizationService;
    }

    @GetMapping("/directions")
    public RouteDirectionsResponse directions(
            @RequestParam RouteCode route
    ) {
        return service.findRoute(route);
    }
    @GetMapping("/optimize")
    public RouteOptimizationResponse optimize(
            @RequestParam RouteCode route
    ) {
        return optimizationService.optimize(route);
    }

}
