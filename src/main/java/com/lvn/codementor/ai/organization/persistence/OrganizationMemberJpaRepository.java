package com.lvn.codementor.ai.organization.persistence;
import com.lvn.codementor.ai.organization.domain.OrganizationMember;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMemberJpaRepository extends JpaRepository<OrganizationMember, UUID> {

    Optional<OrganizationMember> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    List<OrganizationMember> findByUserId(UUID userId);

    List<OrganizationMember> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);
}
