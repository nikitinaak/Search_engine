package searchengine.services.indexing;

import searchengine.model.PageEntity;

import java.io.IOException;

public interface LemmaHandlerService {
    void indexingPage(PageEntity page);
}
