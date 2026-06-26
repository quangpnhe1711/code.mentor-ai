package com.lvn.codementor.ai.auth.application;

import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the currently authenticated platform user from the security context. Application code
 * (controllers/services) depends on this port rather than touching Spring Security directly.
 */
@Component
public class CurrentUser {

    /**
     * @return the authenticated platform user id
     * @throws AppException with {@link ErrorCode#UNAUTHENTICATED} if there is no authenticated user
     */
    public UUID requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.userId();
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED, "Authentication required");
    }
}
