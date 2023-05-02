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
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IndexingPageServiceImpl implements IndexingPageService{
    private static Logger logger = LogManager.getLogger(IndexingPageServiceImpl.class);
    private static Marker RESPONSE_MARKER = MarkerManager.getMarker("RESPONSE");

    private final SitesList sitesList;
    private final JsoupSetting jsoupSetting;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaHandlerService lemmaHandlerService;


    @Override
    public Response indexPage(String url) throws IOException {
        Link link = new Link(url);
        String host = link.getHost();
        Response response;
        if (isSiteNotAvailable(host)) {
            response = new FalseResponse(false, "Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле");
            logger.info(RESPONSE_MARKER, response);
            return response;
        }
        SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(host);
        if (siteEntity == null) {
            siteEntity = new SiteEntity(host, getSitesName(host), Status.INDEXING,
                    LocalDateTime.now());
            siteRepository.save(siteEntity);
        }
        JsoupConnector jsoupConnector = new JsoupConnector(url, jsoupSetting);
        PageEntity pageEntity = pageRepository.findByPathAndSiteId(jsoupConnector.getPathByUrl(),
                siteEntity.getSiteId());
        if (pageEntity != null) {
            cleanDBFromPageAndLemmas(pageEntity);
        } else {
            pageEntity = new PageEntity(siteEntity, jsoupConnector.getPathByUrl(),
                    jsoupConnector.getStatusConnectionCode(), jsoupConnector.getContent());
            pageRepository.save(pageEntity);
        }
        lemmaHandlerService.indexingPage(pageEntity);
        response = new TrueResponse(true);
        logger.info(RESPONSE_MARKER);
        return response;
    }

    private void cleanDBFromPageAndLemmas(PageEntity pageEntity) {
        List<IndexEntity> indexEntityList =
                indexRepository.findAllIndexEntityByPageId(pageEntity.getPageId());
        if (indexEntityList.isEmpty()) {
            pageRepository.delete(pageEntity);
            return;
        }
        for (IndexEntity indexEntity : indexEntityList) {
            lemmaRepository.delete(indexEntity.getLemma());
        }
        indexRepository.deleteByPage(pageEntity.getPageId());
        pageRepository.delete(pageEntity);
    }

    private boolean isSiteNotAvailable(String host) {
        return sitesList.getSites().stream().noneMatch(e -> e.getUrl().equals(host));
    }

    private String getSitesName(String host) {
        for (Site site : sitesList.getSites()) {
            if (site.getUrl().equals(host)) {
                return site.getName();
            }
        }
        return "";
    }
}
