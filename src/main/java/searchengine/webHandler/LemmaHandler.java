package searchengine.webHandler;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.lucene.morphology.LuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.LemmaConfig;
import searchengine.model.*;
import searchengine.repositories.LemmaRepository;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LemmaHandler {
    private static Logger logger = LogManager.getLogger(LemmaHandler.class);
    private static Marker INDEXING_PAGE_MARKER = MarkerManager.getMarker("INDEXING_PAGE");

    private static final String WORD_TYPE_REGEX = "\\W\\w&&[^а-яА-Я\\s]";
    private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ"};

    private final LemmaRepository lemmaRepository;

    public Map<LemmaEntity, Integer> indexingPage(PageEntity page) {
        logger.info(INDEXING_PAGE_MARKER, "Индексирует старницу: " + page.getPath());
        Map<LemmaEntity, Integer> mapLemmasAndRank = new HashMap<>();
        SiteEntity siteEntity = page.getSite();
        Map<String, Integer> lemmasMap = collectLemmas(page);
        for (Map.Entry<String, Integer> mapEntry : lemmasMap.entrySet()) {
            Optional<LemmaEntity> lemmaEntity =
                    lemmaRepository.findByLemmaAndSiteId(mapEntry.getKey(), siteEntity.getSiteId());
            if (lemmaEntity.isPresent()) {
                lemmaEntity.get().setFrequency(lemmaEntity.get().getFrequency() + 1);
            } else {
                LemmaEntity lemma = new LemmaEntity(siteEntity, mapEntry.getKey(), 1);
                mapLemmasAndRank.put(lemma, mapEntry.getValue());
            }
        }
        return mapLemmasAndRank;
    }

    private HashMap<String, Integer> collectLemmas(PageEntity pageEntity) {
        String content = cleanFromHTMLTags(pageEntity.getContent());
        HashMap<String, Integer> lemmas = new HashMap<>();
        String[] words = content.toLowerCase(Locale.ROOT).replaceAll("([^а-яё\\s])", " ")
                .trim().split("\\s+");
        try {
            LuceneMorphology luceneMorphology = new LemmaConfig().luceneMorphology();
            for (String word : words) {
                if (word.isBlank()) continue;
                List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
                if (isIncorrectWordForm(wordBaseForms) || anyWordBaseBelongToParticle(wordBaseForms))
                    continue;
                List<String> normalForms = luceneMorphology.getNormalForms(word);
                if (normalForms.isEmpty()) continue;
                String normalForm = normalForms.get(0);
                int rank = lemmas.containsKey(normalForm) ? lemmas.get(normalForm) + 1 : 1;
                lemmas.put(normalForm, rank);
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return lemmas;
    }
    private boolean isIncorrectWordForm(List<String> wordInfo) {
        for (String morphInfo : wordInfo) {
            if (morphInfo.matches(WORD_TYPE_REGEX)) return true;
        }
        return false;
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

    private String cleanFromHTMLTags(String content) {
        StringBuilder builder = new StringBuilder();
        Document doc = Jsoup.parse(content);
        builder.append(doc.body().text());
        return builder.toString();
    }
}
