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
import searchengine.model.*;
import searchengine.webHandler.JsoupConnector;
import searchengine.webHandler.LemmaHandler;
import searchengine.webHandler.Link;
import searchengine.webHandler.RecursiveParser;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.IndexingService;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
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
    private final LemmaHandler lemmaHandler;

    private final SitesList sitesList;
    private final JsoupSetting jsoupSettings;
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
        cleanDB();
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

    @Override
    public Response indexPage(String url) throws IOException {
        Link link = new Link(url);
        String host = link.getHost();
        Response response;
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(host);
        if (siteEntity == null) {
            String siteName = "";
            for (Site site : sitesList.getSites()) {
                if (site.getUrl().equals(host)) {
                    siteName = site.getName();
                }
            }
            if (siteName.isEmpty()) {
                response = new FalseResponse(false, "Данная страница находится за пределами сайтов, " +
                        "указанных в конфигурационном файле");
                logger.info(RESPONSE_MARKER, response);
                return response;
            }
            siteEntity = new SiteEntity(host, siteName, Status.INDEXING,
                    LocalDateTime.now());
            siteRepository.save(siteEntity);
        }
        JsoupConnector jsoupConnector = new JsoupConnector(url, jsoupSettings);
        Optional<PageEntity> optionalPageEntity =
                pageRepository.findByPathAndSiteId(jsoupConnector.getPathByUrl(),
                        siteEntity.getSiteId());
        PageEntity pageEntity;
        try {
            if (optionalPageEntity.isPresent()) {
                pageEntity = optionalPageEntity.get();
                cleanDBFromPageAndLemmas(pageEntity);
            } else {
                pageEntity = new PageEntity(siteEntity, jsoupConnector.getPathByUrl(),
                        jsoupConnector.getStatusConnectionCode(), jsoupConnector.getContent());
                pageRepository.save(pageEntity);
            }
            if (isSiteStatusCodeOk(pageEntity)) {
                lemmaHandler.indexingPage(pageEntity);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        response = new TrueResponse(true);
        logger.info(RESPONSE_MARKER);
        return response;
    }

    private void cleanDBFromPageAndLemmas(PageEntity pageEntity) {
        List<IndexEntity> indexEntityList =
                indexRepository.findAllIndexEntityByPage(pageEntity);
        if (indexEntityList.isEmpty()) {
            pageRepository.delete(pageEntity);
            return;
        }
        List<LemmaEntity> lemmaEntityList = new ArrayList<>();
        for (IndexEntity indexEntity : indexEntityList) {
            lemmaEntityList.add(indexEntity.getLemma());
        }
        indexRepository.deleteIndexEntityByPage(pageEntity);
        lemmaRepository.deleteAll(lemmaEntityList);
        pageRepository.delete(pageEntity);
    }

    private void cleanDB() {
        List<SiteEntity> sites = siteRepository.findAll();
        if (sites.isEmpty()) {
            return;
        }
        for (SiteEntity site : sites) {
            indexRepository.deleteAll();
            lemmaRepository.deleteAllLemmaEntityBySite(site);
            while (pageRepository.countAllPageEntityBySite(site) != 0) {
                pageRepository.deleteAll(pageRepository.findFirst1000PageEntityBySite(site));
            }
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
                        jsoupSettings, siteEntity, pageRepository, indexRepository,
                        lemmaRepository, lemmaHandler);
                RecursiveParser.setStopped(false);
                forkJoinPool.invoke(recursiveParser);
                if (stopped) {
                    updateSiteEntity(siteEntity, Status.FAILED, "Индексация прервана пользователем");
                    return;
                }
                if (pageRepository.countAllPageEntityBySite(siteEntity) == 0) {
                    updateSiteEntity(siteEntity, Status.FAILED,
                            "Главная страница сайта недоступна");
                } else {
                    updateSiteEntity(siteEntity, Status.INDEXED, "");
                }
            } catch (Exception e) {
                updateSiteEntity(siteEntity, Status.FAILED, e.getMessage());
                indexingInProcess = false;
            }
            logger.info(RESPONSE_MARKER,
                    "Сайт проиндексирован за " + (System.currentTimeMillis() - start));
        }
        setResponse(true, "");
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