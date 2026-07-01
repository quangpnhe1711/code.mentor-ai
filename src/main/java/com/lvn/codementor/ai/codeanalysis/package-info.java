/**
 * codeanalysis module — builds a sanitized, credential-free review input from a READY repository
 * snapshot (pre-AI phase). It reads safe text files, skips binary/oversized/sensitive/disallowed
 * files, masks detected secrets, computes a stable input hash, and stores metadata only. No AI
 * provider, worker, webhook, RAG, or frontend is involved yet (see docs/12-roadmap.md).
 */
package com.lvn.codementor.ai.codeanalysis;
