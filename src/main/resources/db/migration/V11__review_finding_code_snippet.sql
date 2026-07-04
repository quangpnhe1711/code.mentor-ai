-- Store the offending line with each finding so reviewers see the exact code.
-- Already secret-masked upstream; the hardcoded-secret rule additionally redacts the matched value.
-- Nullable: AI-provider findings do not carry a snippet.
alter table review_findings add column code_snippet text;
