package com.lvn.codementor.ai.review.application;

import com.lvn.codementor.ai.codeanalysis.domain.CodeAnalysisFile;
import java.util.List;

public interface ReviewAnalyzerClient {

    ReviewAnalyzerResult analyze(List<CodeAnalysisFile> files);
}
