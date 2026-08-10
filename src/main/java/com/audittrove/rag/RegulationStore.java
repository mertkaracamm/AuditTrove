package com.audittrove.rag;

import java.util.List;

public interface RegulationStore {
    List<RegulationChunk> search(String query, int limit);
}
