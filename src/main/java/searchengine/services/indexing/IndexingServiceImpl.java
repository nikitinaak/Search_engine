package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.springframework.stereotype.Service;
import searchengine.config.JsoupSetting;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.responses.FalseResponse;
import searchengine.dto.responses.Response;
import searchengine.dto.responses.TrueResponse;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private static Logger logger = LogManager.getLogger(IndexingServiceImpl.class);
    private static Marker RESPONSE_MARKER = MarkerManager.getMarker("RESPONSE");

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaHandlerService lemmaHandlerService;

    private final SitesList sitesList;
    private  final JsoupSetting jsoupSettings;
    private ForkJoinPool forkJoinPool = new ForkJoinPool();
    private volatile boolean stopped = false;
    private Response response;
    private volatile boolean firstStart = true;
    private boolean indexingInProcess;

    @Override
    public Response startIndexing() {
        if (indexingInProcess) {
            setResponse(false, "Индексация уже запущена");
            return response;
        }
        indexingInProcess = true;
        deleteAndCleanTables();
        webCrawling();
        firstStart = false;
        indexingInProcess = false;
        return response;
    }

    @Override
    public Response stopIndexing() {
        if (indexingInProcess) {
            RecursiveParser.setStopped(true);
            stopped = true;
            setResponse(true, "");
            return response;
        }
        setResponse(false, "Индексация не запущена");
        return response;
    }

    private void deleteAndCleanTables() {
        List<SiteEntity> sites = siteRepository.findAll();
        if (sites.isEmpty()) {
            return;
        }
        for (SiteEntity site : sites) {
            lemmaRepository.deleteBySiteId(site.getSiteId());
            pageRepository.deletePagesBySiteId(site.getSiteId());
        }
        siteRepository.deleteAll(sites);
        indexRepository.resetId();
        lemmaRepository.resetId();
        pageRepository.resetId();
        siteRepository.resetId();
    }

    private void webCrawling() {
        for (Site site : sitesList.getSites()) {
            long start = System.currentTimeMillis();
            SiteEntity siteEntity = new SiteEntity(site.getUrl(), site.getName(), Status.INDEXING,
                    LocalDateTime.now());
            siteRepository.save(siteEntity);
            Link rootLink = new Link(site.getUrl());
            try {
                RecursiveParser recursiveParser = new RecursiveParser(rootLink, firstStart,
                        siteEntity, pageRepository, jsoupSettings);
                RecursiveParser.setStopped(false);
                forkJoinPool.invoke(recursiveParser);
                if (stopped) return;
                updateSiteEntity(siteEntity, Status.INDEXED, "");
                indexingPages(siteEntity);
            } catch (Exception e) {
                updateSiteEntity(siteEntity, Status.FAILED, e.getMessage());
                indexingInProcess = false;
            }
            logger.info(RESPONSE_MARKER,
                    "Сайт проиндексирован за " + (System.currentTimeMillis() - start));
        }
        setResponse(true, "");
    }

    private void indexingPages(SiteEntity siteEntity) throws IOException {
        List<PageEntity> pages = pageRepository.findAllPagesBySiteId(siteEntity.getSiteId());
        if (pages.isEmpty()) {
            return;
        }
        for (PageEntity page : pages) {
            if (isSiteStatusCodeOk(page)) {
                lemmaHandlerService.indexingPage(page);
            }
        }
    }

    private boolean isSiteStatusCodeOk(PageEntity page) {
        return !String.valueOf(page.getCode()).startsWith("4") || !String.valueOf(page.getCode()).startsWith("5");
    }

    private void updateSiteEntity(SiteEntity siteEntity, Status status, String lastError) {
        siteEntity.setStatus(status);
        siteEntity.setStatusTime(LocalDateTime.now());
        if (lastError != null || !lastError.isEmpty()) {
            siteEntity.setLastError(lastError);
        }
        siteRepository.save(siteEntity);
    }

    private void setResponse(boolean result, String error) {
        if (result) {
            response = new TrueResponse(true);
        } else {
            response = new FalseResponse(false, error);
        }
        logger.info(RESPONSE_MARKER, response.toString());
    }
}