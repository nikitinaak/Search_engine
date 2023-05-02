package searchengine.services.indexing;

import searchengine.dto.responses.Response;

import java.io.IOException;

public interface IndexingPageService {
    Response indexPage(String url) throws IOException;
}
