package com.clinicready.agent;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.common.InvalidRequestException;
import com.clinicready.common.NotFoundException;
import com.clinicready.security.AuthorizationService;
import com.clinicready.security.CurrentUserService;
import com.clinicready.security.Permission;
import com.clinicready.security.Role;

/**
 * Starts agent runs and reads them back. A run is stored before it starts, then executed on the agent
 * executor as its initiator. Readable by users of the clinic with {@link Permission#AGENT_RUN}; a
 * practitioner reads only the runs they started. Anything else is reported as not found.
 */
@Service
public class AgentRunService {

    static final int MAX_REQUEST_LENGTH = 500;

    private static final Logger log = LoggerFactory.getLogger(AgentRunService.class);

    /** A run with its tool calls in order. */
    public record RunView(AgentRun run, List<AgentAction> actions) {
    }

    private final AgentRunRepository runs;
    private final AgentActionRepository actions;
    private final AgentOrchestrator orchestrator;
    private final TaskExecutor executor;
    private final AiProperties properties;
    private final CurrentUserService currentUser;
    private final AuthorizationService authorization;

    AgentRunService(AgentRunRepository runs, AgentActionRepository actions, AgentOrchestrator orchestrator,
            @Qualifier(AiConfig.AGENT_EXECUTOR) TaskExecutor executor, AiProperties properties,
            CurrentUserService currentUser, AuthorizationService authorization) {
        this.runs = runs;
        this.actions = actions;
        this.orchestrator = orchestrator;
        this.executor = executor;
        this.properties = properties;
        this.currentUser = currentUser;
        this.authorization = authorization;
    }

    /**
     * Stores a new run and queues it. Returns at once; poll {@link #get} for the outcome.
     *
     * @throws InvalidRequestException empty or too long request
     */
    public AgentRun start(String request) {
        authorization.require(Permission.AGENT_RUN);
        String text = request == null ? "" : request.strip();
        if (text.isEmpty() || text.length() > MAX_REQUEST_LENGTH) {
            throw new InvalidRequestException("The request must be 1 to " + MAX_REQUEST_LENGTH + " characters");
        }
        AgentRun run = runs.save(new AgentRun(currentUser.clinicId(), currentUser.userId(), text));
        // The worker acts as the initiator: same clinic, role and practitioner scope as this request.
        SecurityContext initiator = SecurityContextHolder.createEmptyContext();
        initiator.setAuthentication(SecurityContextHolder.getContext().getAuthentication());
        AgentOrchestrator.Limits limits = new AgentOrchestrator.Limits(properties.maxToolCalls(),
                properties.runTimeout());
        UUID id = run.getId();
        try {
            executor.execute(() -> {
                SecurityContextHolder.setContext(initiator);
                try {
                    orchestrator.execute(id, limits);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
        } catch (TaskRejectedException e) {
            run.finish(AgentRun.Status.FAILED, "Too many runs in progress; try again shortly", null);
            run = runs.save(run);
        }
        log.info("Agent run {} queued", id);
        return run;
    }

    /** @throws NotFoundException run not in the clinic, or another user's run for a practitioner */
    @Transactional(readOnly = true)
    public RunView get(UUID id) {
        authorization.require(Permission.AGENT_RUN);
        AgentRun run = runs.findByIdAndClinicId(id, currentUser.clinicId())
                .filter(r -> currentUser.role() != Role.PRACTITIONER || r.getInitiatorId().equals(currentUser.userId()))
                .orElseThrow(() -> new NotFoundException("Agent run"));
        return new RunView(run, actions.findAllByRunIdOrderBySeq(run.getId()));
    }

    /** Runs cut off by a restart would stay RUNNING forever; end them with a clear reason instead. */
    @EventListener(ApplicationReadyEvent.class)
    void failInterruptedRuns() {
        for (AgentRun run : runs.findAllByStatusIn(List.of(AgentRun.Status.QUEUED, AgentRun.Status.RUNNING))) {
            run.finish(AgentRun.Status.FAILED, "Interrupted by a server restart", null);
            runs.save(run);
            log.warn("Agent run {} marked FAILED after a restart", run.getId());
        }
    }
}
