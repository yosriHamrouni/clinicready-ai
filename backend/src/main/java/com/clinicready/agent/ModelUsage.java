package com.clinicready.agent;

import java.time.Duration;

/** Tokens spent by one model call. Counts are 0 when the provider did not report them. */
public record ModelUsage(String model, int promptTokens, int completionTokens, int totalTokens, Duration latency) {
}
