package com.example.ticket_service.ticket.repository;

import com.example.ticket_service.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    /** Active workload: tickets assigned to this staff member not yet closed/cancelled. */
    @Query("select count(t) from Ticket t where t.assignedTo = :staffId and t.status not in :terminalStatuses")
    long countActiveByAssignedTo(@Param("staffId") UUID staffId, @Param("terminalStatuses") List<String> terminalStatuses);

    /** Priority experience: how many tickets at this exact priority has this staff member already resolved. */
    @Query("select count(t) from Ticket t where t.assignedTo = :staffId and t.priority = :priority and t.status in :resolvedStatuses")
    long countResolvedByAssignedToAndPriority(@Param("staffId") UUID staffId, @Param("priority") String priority,
                                               @Param("resolvedStatuses") List<String> resolvedStatuses);

    /** 6.1 SlaWarningJob: open tickets whose SLA due date falls within the warning window, not yet warned. */
    @Query("select t from Ticket t where t.status not in :terminalStatuses and t.slaDueAt is not null "
        + "and t.slaWarningSent = false and t.slaDueAt <= :warningCutoff")
    List<Ticket> findApproachingSlaBreach(@Param("terminalStatuses") List<String> terminalStatuses,
                                           @Param("warningCutoff") java.time.Instant warningCutoff);

    /** 6.1 SlaBreachJob: open tickets already past their SLA due date, not yet marked breached. */
    @Query("select t from Ticket t where t.status not in :terminalStatuses and t.slaDueAt is not null "
        + "and t.slaBreached = false and t.slaDueAt < :now")
    List<Ticket> findNewlyBreached(@Param("terminalStatuses") List<String> terminalStatuses,
                                    @Param("now") java.time.Instant now);

    /** 6.1 dashboard: breach counts by category, for IT_MANAGER's SLA breach summary. */
    @Query("select t.categoryId, count(t) from Ticket t where t.slaBreached = true group by t.categoryId")
    List<Object[]> countBreachesByCategory();

    /** 6.1 dashboard: breach counts by assigned staff member. */
    @Query("select t.assignedTo, count(t) from Ticket t where t.slaBreached = true and t.assignedTo is not null group by t.assignedTo")
    List<Object[]> countBreachesByStaff();
}
