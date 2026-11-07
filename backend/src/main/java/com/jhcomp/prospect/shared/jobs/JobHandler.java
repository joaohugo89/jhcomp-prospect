package com.jhcomp.prospect.shared.jobs;

/**
 * The executor of one job type.
 *
 * <p>It runs outside an HTTP request: there is no session and no
 * {@code OrganizationContext}. The organisation comes from the job itself,
 * written from the session of whoever requested it, and never from the
 * payload.
 *
 * <p>It must not hold a transaction open during a network call. An exception
 * thrown makes the job be retried, up to the attempt limit.
 */
public interface JobHandler {

    String jobType();

    void handle(JobEntity job);

    /**
     * Called when the job fails for good, so the resource attached to it does
     * not wait forever.
     */
    void onGiveUp(JobEntity job);
}
