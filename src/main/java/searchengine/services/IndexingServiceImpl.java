package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.springframework.stereotype.Service;
import searchengine.config.Jsoup;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.JsoupConnector;
import searchengine.dto.indexing.Link;
import searchengine.dto.indexing.RecursiveParser;
import searchengine.dto.responses.FalseResponse;
import searchengine.dto.responses.Response;
import searchengine.dto.responses.TrueResponse;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;


import java.time.LocalDateTime;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private static Logger logger = LogManager.getLogger(IndexingServiceImpl.class);
    private static Marker RESPONSE_MARKER = MarkerManager.getMarker("RESPONSE");

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    private final SitesList sitesList;
    private  final Jsoup jsoupSettings;
    private ForkJoinPool forkJoinPool = new ForkJoinPool();
    private Response response;
    private volatile boolean firstStart = true;
    private volatile boolean stopped = false;
    private boolean indexingInProcess;

    @Override
    public Response startIndexing() {
        if (indexingInProcess) {
            response = setResponse(false, "Индексация уже запущена");
            return response;
        }
        indexingInProcess = true;
        deleteAndCleanTables();
        long start = System.currentTimeMillis();
        webCrawling();
        firstStart = false;
        indexingInProcess = false;
        System.out.println("Индексация заняла: " + ((System.currentTimeMillis() - start)/1000) +
                " " +
                "секунд");
        return response;
    }

    @Override
    public Response stopIndexing() {
        if (indexingInProcess) {
            stopped = true;
            response = setResponse(true, "");
            return response;
        }
        response = setResponse(false, "Индексация не запущена");
        return response;
    }

    @Override
    public Response indexPage(String url) {
        try {
            indexingInProcess = true;
            JsoupConnector connector = new JsoupConnector(url, jsoupSettings);
            SiteEntity site = siteRepository.findSiteEntityByUrl(connector.getHost());
            PageEntity page = pageRepository.findByPathAndSiteId(connector.getPathByUrl(),
                    site.getSiteId());
            if (page == null) {
                page = new PageEntity(site, connector.getPathByUrl(),
                        connector.getStatusConnectionCode(), connector.getContent());
                pageRepository.save(page);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            setResponse(false, e.getMessage());
            indexingInProcess = false;
            return response;
        }
        return response;
    }

    private void deleteAndCleanTables() {
        pageRepository.deleteAll();
        siteRepository.deleteAll();
        siteRepository.resetId();
        pageRepository.resetId();
    }

    private Response webCrawling() {
        for (Site site : sitesList.getSites()) {
            SiteEntity siteEntity = new SiteEntity(site.getUrl(), site.getName(), Status.INDEXING
                    , LocalDateTime.now());
            siteRepository.save(siteEntity);
            Link rootLink = new Link(site.getUrl());
            try {
                forkJoinPool.invoke(new RecursiveParser(rootLink,
                        firstStart, stopped, jsoupSettings, siteEntity, pageRepository));
                updateSiteEntity(siteEntity, Status.INDEXED, "");
            } catch (RejectedExecutionException e) {
                updateSiteEntity(siteEntity, Status.FAILED, "Индексация остановлена пользователем");
                indexingInProcess = false;
                break;
            }
        }
        return setResponse(true, "");
    }

    private void updateSiteEntity(SiteEntity siteEntity, Status status, String lastError) {
        siteEntity.setStatus(status);
        siteEntity.setStatusTime(LocalDateTime.now());
        if (!lastError.isEmpty()) {
            siteEntity.setLastError("Индексация остановлена пользователем");
        }
        siteRepository.save(siteEntity);
    }

    private Response setResponse(boolean result, String error) {
        Response response;
        if (result) {
            response = new TrueResponse(true);
        } else {
            response = new FalseResponse(false, error);
        }
        logger.info(RESPONSE_MARKER, response.toString());
        return response;
    }
}