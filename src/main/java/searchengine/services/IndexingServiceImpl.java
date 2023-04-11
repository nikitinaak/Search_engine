package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Jsoup;
import searchengine.config.Site;
import searchengine.config.SitesList;
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
import java.util.ArrayList;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private static Logger logger = LogManager.getLogger(IndexingServiceImpl.class);
    private static Marker RESPONSE_MARKER = MarkerManager.getMarker("RESPONSE");

    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private PageRepository pageRepository;

    private final SitesList sitesList;
    private  final Jsoup jsoupSettings;
    private ForkJoinPool forkJoinPool = new ForkJoinPool();
    private Response response;
    private volatile boolean firstStart = true;
    private volatile boolean stopped = false;
    private boolean indexingInProcess;
    private ArrayList<PageEntity> pageEntities = new ArrayList<>();

    @Override
    public Response startIndexing() {
        if (indexingInProcess) {
            response = setResponse(false, "Индексация уже запущена");
            return response;
        }
        indexingInProcess = true;
        deleteAndCleanTables();
        long start = System.currentTimeMillis();
        if (forkJoinPool.isShutdown()) {
            forkJoinPool = new ForkJoinPool();
        }
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
            forkJoinPool.shutdownNow();
            response = setResponse(true, "");
            return response;
        }
        response = setResponse(false, "Индексация не запущена");
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
            forkJoinPool.invoke(new RecursiveParser(rootLink,
                    firstStart, stopped, jsoupSettings, siteEntity, pageRepository));
            if (stopped) {
                updateSiteEntity(siteEntity, Status.FAILED, "Индексация остановлена пользователем");
                return setResponse(true, "");
            }
            updateSiteEntity(siteEntity, Status.INDEXED, "");
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