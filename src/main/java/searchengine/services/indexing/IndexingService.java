package searchengine.services.indexing;

import searchengine.dto.responses.Response;

public interface IndexingService {
    Response startIndexing();
    Response stopIndexing();
}
