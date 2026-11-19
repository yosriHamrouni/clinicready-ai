package com.clinicready.agent;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clinicready.agent.dto.AgentRunResponse;
import com.clinicready.agent.dto.NewAgentRun;

/**
 * Agent runs. Separate from the follow-up approval API on purpose: nothing here, and no agent tool, can
 * approve or execute a follow-up. Permissions are applied in {@link AgentRunService}.
 */
@RestController
@RequestMapping("/api/v1/agent-runs")
class AgentRunController {

    private final AgentRunService runs;

    AgentRunController(AgentRunService runs) {
        this.runs = runs;
    }

    @PostMapping
    ResponseEntity<AgentRunResponse.Started> start(@RequestBody NewAgentRun body) {
        AgentRun run = runs.start(body.request());
        return ResponseEntity.accepted().location(URI.create("/api/v1/agent-runs/" + run.getId()))
                .body(new AgentRunResponse.Started(run.getId(), run.getStatus()));
    }

    @GetMapping("/{id}")
    AgentRunResponse get(@PathVariable UUID id) {
        return AgentRunResponse.from(runs.get(id));
    }
}
