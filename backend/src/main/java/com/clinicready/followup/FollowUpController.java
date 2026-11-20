package com.clinicready.followup;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clinicready.followup.dto.DecisionRequest;
import com.clinicready.followup.dto.FollowUpTaskResponse;
import com.clinicready.followup.dto.MessageDraftResponse;
import com.clinicready.followup.dto.TextRequest;

/**
 * Follow-up tasks and the approval API. Separate from the agent-run API: no agent tool reaches these
 * endpoints. Permissions are applied in {@link FollowUpService}.
 */
@RestController
@RequestMapping("/api/v1/follow-up-tasks")
class FollowUpController {

    private final FollowUpService followUps;

    FollowUpController(FollowUpService followUps) {
        this.followUps = followUps;
    }

    @GetMapping
    List<FollowUpTaskResponse> list(@RequestParam(required = false) UUID appointmentId,
            @RequestParam(required = false) Set<FollowUpStatus> status) {
        List<FollowUpTask> found = appointmentId != null ? followUps.forAppointment(appointmentId)
                : followUps.list(status);
        return found.stream().map(t -> FollowUpTaskResponse.from(t, List.of())).toList();
    }

    @GetMapping("/{id}")
    FollowUpTaskResponse get(@PathVariable UUID id) {
        return FollowUpTaskResponse.from(followUps.view(id));
    }

    @GetMapping("/{id}/draft")
    MessageDraftResponse draft(@PathVariable UUID id) {
        return MessageDraftResponse.from(followUps.draft(id));
    }

    @PutMapping("/{id}/draft")
    MessageDraftResponse editDraft(@PathVariable UUID id, @RequestBody TextRequest body) {
        return MessageDraftResponse.from(followUps.editDraft(id, body.body()));
    }

    @PostMapping("/{id}/approve")
    FollowUpTaskResponse approve(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest body) {
        return FollowUpTaskResponse.from(followUps.approve(id, body == null ? null : body.comment()));
    }

    @PostMapping("/{id}/reject")
    FollowUpTaskResponse reject(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest body) {
        return FollowUpTaskResponse.from(followUps.reject(id, body == null ? null : body.comment()));
    }

    @PostMapping("/{id}/delivered")
    FollowUpTaskResponse delivered(@PathVariable UUID id) {
        return FollowUpTaskResponse.from(followUps.markDelivered(id));
    }

    @PostMapping("/{id}/resolve")
    FollowUpTaskResponse resolve(@PathVariable UUID id) {
        return FollowUpTaskResponse.from(followUps.resolve(id));
    }
}
