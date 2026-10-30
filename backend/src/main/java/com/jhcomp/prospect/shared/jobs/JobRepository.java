package com.jhcomp.prospect.shared.jobs;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Jobs. A tomada para execucao e por SQL, em {@link JobQueue}. */
interface JobRepository extends JpaRepository<JobEntity, UUID> {
}
