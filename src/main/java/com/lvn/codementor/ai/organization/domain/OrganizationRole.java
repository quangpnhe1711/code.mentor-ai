package com.lvn.codementor.ai.organization.domain;

/** Organization membership role (doc 02 Roles). The role→permission matrix is TBD (doc 09 §2). */
public enum OrganizationRole {
    OWNER,
    ADMIN,
    REVIEWER,
    DEVELOPER,
    VIEWER
}
