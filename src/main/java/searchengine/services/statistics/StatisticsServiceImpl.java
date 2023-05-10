package searchengine.services.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.SiteEntity;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.StatisticsService;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SitesList sitesList;


    @Override
    public StatisticsResponse getStatistics() {
        StatisticsResponse response = new StatisticsResponse();
        List<Site> sites = sitesList.getSites();
        StatisticsData statisticsData = new StatisticsData();
        ArrayList<DetailedStatisticsItem> statisticsItems = new ArrayList<>();
        int pages = 0;
        int lemmas = 0;
        for (Site site : sites) {
            SiteEntity siteEntity = siteRepository.findSiteEntityByUrl(site.getUrl());
            if (siteEntity == null) continue;
            DetailedStatisticsItem dSI = new DetailedStatisticsItem();
            dSI.setName(siteEntity.getName());
            dSI.setUrl(siteEntity.getUrl());
            dSI.setStatus(String.valueOf(siteEntity.getStatus()));
            dSI.setStatusTime(Timestamp.valueOf(siteEntity.getStatusTime()).getTime());
            if (siteEntity.getLastError() != null) dSI.setError(siteEntity.getLastError());
            int pageCount = pageRepository.countAllPageEntityBySite(siteEntity);
            dSI.setPages(pageCount);
            pages += pageCount;
            int lemmaCount = lemmaRepository.countAllLemmaEntityBySite(siteEntity);
            dSI.setLemmas(lemmaCount);
            lemmas += lemmaCount;
            statisticsItems.add(dSI);
        }
        statisticsData.setDetailed(statisticsItems);
        TotalStatistics totalStatistics = new TotalStatistics();
        totalStatistics.setSites(statisticsData.getDetailed().size());
        totalStatistics.setPages(pages);
        totalStatistics.setLemmas(lemmas);
        totalStatistics.setIndexing(totalStatistics.getPages() > 0);
        statisticsData.setTotal(totalStatistics);
        response.setStatistics(statisticsData);
        response.setResult(true);
        return response;
    }
}
