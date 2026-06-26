package com.lvn.codementor.ai.organization.application;

import com.lvn.codementor.ai.organization.persistence.OrganizationMemberJpaRepository;
import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Organization authorization baseline (doc 09 §2). For Foundation only <em>membership</em> is
 * enforced; the full role→permission matrix is still <strong>TBD</strong>, so role-specific
 * permissions are not implemented yet.
 */
@Service
public class OrganizationAccessService {

    private final OrganizationMemberJpaRepository members;

    public OrganizationAccessService(OrganizationMemberJpaRepository members) {
        this.members = members;
    }

    /**
     * Ensure the user is a member of the organization.
     *
     * @throws AppException with {@link ErrorCode#FORBIDDEN} if the user is not a member
     */
    public void requireMember(UUID userId, UUID organizationId) {
        members.findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new AppException(
                        ErrorCode.FORBIDDEN, "You are not a member of this organization"));
    }
}
