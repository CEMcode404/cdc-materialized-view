package com.example.cdcmaterializedview.controller;

import com.example.cdcmaterializedview.service.SummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/customers/{id}/summary-slow")
    public Map<String, Object> summarySlow(@PathVariable Long id) {
        return summaryService.getSummarySlow(id);
    }
}