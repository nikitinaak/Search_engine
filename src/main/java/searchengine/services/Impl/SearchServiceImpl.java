package searchengine.services.Impl;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.lucene.morphology.LuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import searchengine.config.LemmaConfig;
import searchengine.dto.responses.FalseResponse;
import searchengine.dto.responses.Response;
import searchengine.dto.searching.SearchData;
import searchengine.dto.searching.SearchResponse;

import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.*;
import searchengine.services.SearchService;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private static Logger logger = LogManager.getLogger(SearchServiceImpl.class);
    private static Marker SEARCHING_MARKER = MarkerManager.getMarker("SEARCH");
    private static Marker RESPONSE_MARKER = MarkerManager.getMarker("RESPONSE");

    private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ"};

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private static final int FREQUENCY_VALUE = 3000;
    private int resultCount;
    private static LuceneMorphology luceneMorphology;

    @Override
    public Response search(String query, String site, int offset, int limit) throws IOException {
        resultCount = 0;
        if (query == null || query.isEmpty()) {
            return setFalseResponse();
        }
        logger.info(SEARCHING_MARKER, "Поисковый запрос: " + query);
        List<SearchData> searchDataList;
        if (site != null) {
            Optional<SiteEntity> optionalSiteEntity = siteRepository.findSiteEntityByUrl(site);
            logger.info(SEARCHING_MARKER, "Поиск по сайту - " + optionalSiteEntity.get().getName());
            int siteId = optionalSiteEntity.get().getSiteId();
            searchDataList = getSearchDataListForOneSite(siteId, query,  offset, limit);
        } else {
            logger.info(SEARCHING_MARKER, "Поиск по всем сайтам");
            searchDataList = getSearchDataListForAllSite(query, offset, limit);
        }
        return setTrueResponse(searchDataList);
    }

    private List<SearchData> getSearchDataListForOneSite(int siteId, String query, int offset, int limit) throws IOException {
        Map<Integer, String> pageIdAndLemmaMap = new HashMap<>();
        Set<Integer> pagesIdWithLemmaSet = new HashSet<>();
        List<Map.Entry<String, Integer>> listMapEntry = getListLemmasAndFrequency(query, siteId);
        if (listMapEntry.isEmpty()) {
            return new ArrayList<>();
        }
        for (Map.Entry<String, Integer> lemmaAndFrequency : listMapEntry) {
            int lemmaId = lemmaRepository.findLemmaIdByLemmaAndSiteId(lemmaAndFrequency.getKey(), siteId).get();
            List<Integer> listPages = indexRepository.findAllPageIdByLemmaId(lemmaId);
            pagesIdWithLemmaSet.addAll(listPages);
            for (Integer pageId : listPages) {
                pageIdAndLemmaMap.put(pageId, lemmaAndFrequency.getKey());
            }
        }
        Map<Integer, Float> pageAndRelevanceMap = getPageAndAbsRelevanceMap(pagesIdWithLemmaSet, listMapEntry, siteId);
        List<Integer> sortedListPageIdByRelevance = sortPageByRelevance(pageAndRelevanceMap);
        return collectSearchData(sortedListPageIdByRelevance, pageIdAndLemmaMap, pageAndRelevanceMap, offset, limit, false);
    }

    private List<SearchData> getSearchDataListForAllSite(String query, int offset, int limit) throws IOException {
        Map<Integer, String> pageIdAndLemmaMap = new HashMap<>();
        Set<Integer> pagesIdWithLemmaSet = new HashSet<>();
        List<Map.Entry<String, Integer>> listMapEntry = getListLemmasAndFrequency(query, null);
        if (listMapEntry.isEmpty()) {
            return new ArrayList<>();
        }
        for (Map.Entry<String, Integer> lemmaAndFrequency : listMapEntry) {
            List<Integer> lemmaIds = lemmaRepository.findAllLemmaIdByLemma(lemmaAndFrequency.getKey());
            List<Integer> listPages = new ArrayList<>();
            for (Integer lemmaId : lemmaIds) {
                listPages.addAll(indexRepository.findAllPageIdByLemmaId(lemmaId));
            }
            pagesIdWithLemmaSet.addAll(listPages);
            for (Integer padeId : listPages) {
                pageIdAndLemmaMap.put(padeId, lemmaAndFrequency.getKey());
            }
        }
        Map<Integer, Float> pageAndRelevanceMap = getPageAndRelRltRelevance(pagesIdWithLemmaSet, listMapEntry);
        List<Integer> sortedListPageIdByRelevance = sortPageByRelevance(pageAndRelevanceMap);
        return collectSearchData(sortedListPageIdByRelevance, pageIdAndLemmaMap, pageAndRelevanceMap, offset, limit, true);
    }

    private List<Map.Entry<String, Integer>> getListLemmasAndFrequency(String query, Integer siteId) throws IOException {
        Map<String, Integer> mapLemmaAndFrequency = new HashMap<>();
        if (luceneMorphology == null) {
            luceneMorphology = new LemmaConfig().luceneMorphology();
        }
        String[] queryArray = query.toLowerCase(Locale.ROOT).split("[^а-яё0-9]+");
        for (String keyword : queryArray) {
            List<String> wordBaseForms = luceneMorphology.getMorphInfo(keyword);
            if (anyWordBaseBelongToParticle(wordBaseForms)) continue;
            List<String> normalForms = luceneMorphology.getNormalForms(keyword);
            if (normalForms.isEmpty()) continue;
            String normalForm = normalForms.get(0);
            if (siteId != null) {
                Optional<Integer> lemmaFrequency = lemmaRepository.findFrequencyByLemmaAndSiteId(normalForm, siteId);
                lemmaFrequency.ifPresent(frequency -> mapLemmaAndFrequency.put(normalForm, frequency));
            } else {
                List<LemmaEntity> lemmaEntities = lemmaRepository.findAllByLemma(normalForm);
                lemmaEntities.forEach(lemmaEntity -> mapLemmaAndFrequency.put(normalForm, lemmaEntity.getFrequency()));
            }
        }
        List<Map.Entry<String, Integer>> listLemmasAndFrequency = new ArrayList<>(mapLemmaAndFrequency.entrySet().stream()
                .sorted(Map.Entry.comparingByValue()).toList());
        listLemmasAndFrequency.removeIf(e -> e.getValue() > FREQUENCY_VALUE);
        return listLemmasAndFrequency;
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(this::hasParticleProperty);
    }

    private boolean hasParticleProperty(String wordBase) {
        for (String property : particlesNames) {
            if (wordBase.toUpperCase().contains(property)) return true;
        }
        return false;
    }

    private Map<Integer, Float> getPageAndAbsRelevanceMap(Set<Integer> pageIdWithLemmaSet,
                                                          List<Map.Entry<String, Integer>> listLemmasAndFrequency,
                                                          int siteId) {
        Map<Integer, Float> pageAndAbsRelevanceMap = new HashMap<>();
        float maxAbsRelevance = 0.0f;
        for (Integer pageId : pageIdWithLemmaSet) {
            float absRelevance = getAbsoluteRelevance(listLemmasAndFrequency, siteId, pageId);
            if (absRelevance > maxAbsRelevance) maxAbsRelevance = absRelevance;
            pageAndAbsRelevanceMap.put(pageId, absRelevance);
        }
        float finalMaxAbsRelevance = maxAbsRelevance;
        pageAndAbsRelevanceMap.entrySet().forEach(e -> e.setValue(e.getValue() / finalMaxAbsRelevance));
        return pageAndAbsRelevanceMap;
    }

    private float getAbsoluteRelevance(List<Map.Entry<String, Integer>> listLemmasAndFrequency,
                                       int siteId, int pageId) {
        float absRelevance = 0.0f;
        for (Map.Entry<String, Integer> entryMap : listLemmasAndFrequency) {
            int lemmaId = lemmaRepository.findLemmaIdByLemmaAndSiteId(entryMap.getKey(), siteId).get();
            Optional<Float> rank = indexRepository.findRankByPageIdAndLemmaId(pageId, lemmaId);
            if (rank.isPresent()) absRelevance += rank.get();
        }
        return absRelevance;
    }

    private Map<Integer, Float> getPageAndRelRltRelevance(Set<Integer> pageIdWithLemmaSet,
                                                          List<Map.Entry<String, Integer>> listLemmasAndFrequency) {
        Map<Integer, Float> pageIdAndRltRelevanceMap = new HashMap<>();
        for (Integer pageId : pageIdWithLemmaSet) {
            float relativeRelevance = getRelativeRelevance(listLemmasAndFrequency, pageId);
            pageIdAndRltRelevanceMap.put(pageId, relativeRelevance);
        }
        return pageIdAndRltRelevanceMap;
    }

    private float getRelativeRelevance(List<Map.Entry<String, Integer>> listLemmasAndFrequency, int pageId) {
        float relativeRelevance = 0.0f;
        for (Map.Entry<String, Integer> entryMap : listLemmasAndFrequency) {
            List<Integer> lemmaIds = lemmaRepository.findAllLemmaIdByLemma(entryMap.getKey());
            for (Integer lemmaId : lemmaIds) {
                Optional<Float> rank = indexRepository.findRankByPageIdAndLemmaId(pageId, lemmaId);
                if (rank.isPresent()) relativeRelevance += rank.get();
            }
        }
        return relativeRelevance;
    }

    private List<Integer> sortPageByRelevance(Map<Integer, Float> pageIdAndRelevanceMap) {
        Comparator<Map.Entry<Integer, Float>> comparator = Map.Entry.comparingByValue();
        return pageIdAndRelevanceMap.entrySet().stream().sorted(comparator.reversed())
                .map(Map.Entry::getKey).toList();
    }

    private List<SearchData> collectSearchData(List<Integer> sortedListPageIdByRelevance, Map<Integer, String> pageIdAndLemmaMap,
                                               Map<Integer, Float> pageAndRelevanceMap, int offset, int limit, boolean searchForAllSites) {
        List<SearchData> searchDataList = new ArrayList<>();
        Pageable pageable = PageRequest.of(offset / 10, limit);
        resultCount = sortedListPageIdByRelevance.size();
        List<PageEntity> pageEntityList = pageRepository.findAllById(sortedListPageIdByRelevance, pageable);
        pageEntityList.forEach(pageEntity -> {
            SearchData searchData = createSearchData(pageEntity, pageIdAndLemmaMap.get(pageEntity.getPageId()),
                    pageAndRelevanceMap.get(pageEntity.getPageId()));
            searchDataList.add(searchData);
        });
        if (searchForAllSites) {
            float maxRelevance = searchDataList.stream().sorted().findFirst().get().getRelevance();
            searchDataList.forEach(searchData -> searchData.setRelevance(searchData.getRelevance() / maxRelevance));
        }
        return searchDataList;
    }

    private SearchData createSearchData(PageEntity pageEntity, String lemma, float relevance) {
        SiteEntity siteEntity = pageEntity.getSite();
        String title = Jsoup.parse(pageEntity.getContent()).title();
        String snippet = getSnippet(lemma, pageEntity);
        return new SearchData(siteEntity.getUrl(), siteEntity.getName(), pageEntity.getPath(), title,
                snippet, relevance);
    }

    private String getSnippet(String lemma, PageEntity pageEntity) {
        Document doc = Jsoup.parse(pageEntity.getContent());
        String content = Jsoup.parse(doc.html()).text().toLowerCase(Locale.ROOT);
        String keyword = getKeyword(content, lemma);
        int lemmaLength = keyword.length();
        int keyLemma = content.indexOf(keyword);
        int size = content.length();
        if (size <= keyLemma + 200) {
            size = size - keyLemma;
        } else {
            size = 200;
        }
        int startSnippet = keyLemma - size / 2;
        if (startSnippet < 0) startSnippet = 0;
        int endSnippet = keyLemma + size / 2;
        if (endSnippet < keyLemma + lemmaLength) endSnippet = content.length();
        StringBuilder builder = new StringBuilder();
        String snippetStartPart = content.substring(startSnippet, keyLemma);
        String key = content.substring(keyLemma, keyLemma + lemmaLength);
        String snippetEndPart = content.substring(keyLemma + lemmaLength, endSnippet);
        builder.append(snippetStartPart).append("<b>").append(key).append("</b>").append(snippetEndPart);
        return builder.toString();
    }

    private String getKeyword(String content, String lemma) {
        if (luceneMorphology == null) {
            try {
                luceneMorphology = new LemmaConfig().luceneMorphology();
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        }
        String[] words = content.split("[^а-яё]");
        for (String word : words) {
            if (word.isEmpty()) continue;
            List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
            if (anyWordBaseBelongToParticle(wordBaseForms)) continue;
            List<String> normalForms = luceneMorphology.getNormalForms(word);
            if (normalForms.isEmpty()) continue;
            String normalWord = normalForms.get(0);
            if (normalWord.equals(lemma)) {
                return word;
            }
        }
        return lemma;
    }

    private Response setFalseResponse() {
        FalseResponse response = new FalseResponse(false, "Пустой поисковый запрос");
        logger.info(RESPONSE_MARKER, response.toString());
        return response;
    }

    private Response setTrueResponse(List<SearchData> searchDataList) {
        SearchResponse response = new SearchResponse();
        response.setResult(true);
        response.setCount(resultCount);
        response.setData(searchDataList);
        logger.info(SEARCHING_MARKER, "Найдено результатов: " + response.getCount());
        logger.info(RESPONSE_MARKER, response.toString());
        return response;
    }
}
