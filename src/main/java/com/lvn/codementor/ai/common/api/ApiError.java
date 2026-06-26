package com.lvn.codementor.ai.common.api;

import java.util.List;

/** Error body inside the API envelope (doc 07 §2). */
public record ApiError(String code, String message, List<String> details) {
}
