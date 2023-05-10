package searchengine.services;

import searchengine.dto.responses.Response;

import java.io.IOException;

public interface IndexingService {
    Response startIndexing();
    Response stopIndexing();
    Response indexPage(String url) throws IOException;
}
