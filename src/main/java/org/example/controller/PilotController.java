package org.example.controller;

import org.example.service.rules.ExtractionPilotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Triggers/summarizes the keyword-rules pilot. No UI - the full rows are in the extraction_comparison table. */
@RestController
@RequestMapping("/api/pilot/rules")
public class PilotController {

    private final ExtractionPilotService pilot;

    public PilotController(ExtractionPilotService pilot) {
        this.pilot = pilot;
    }

    /** Runs the rules over every stored job and returns per-field agreement with Claude's stored fields. */
    @PostMapping("/backtest")
    public ExtractionPilotService.Report backtest() {
        return pilot.backtest();
    }

    /** Rows that disagreed for one field in the latest backtest, e.g. {@code ?field=stacks}. */
    @GetMapping("/disagreements")
    public List<ExtractionPilotService.Disagreement> disagreements(@RequestParam String field) {
        return pilot.latestBacktestDisagreements(field);
    }

    /** {@code source=BACKTEST} (latest run, default) or {@code SHADOW} (every live intake so far). */
    @GetMapping("/summary")
    public List<ExtractionPilotService.FieldSummary> summary(@RequestParam(defaultValue = "BACKTEST") String source) {
        return "SHADOW".equalsIgnoreCase(source) ? pilot.shadowSummary() : pilot.latestBacktestSummary();
    }
}
