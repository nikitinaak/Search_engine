package searchengine.services.indexing;

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
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;

import javax.persistence.NonUniqueResultException;
import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LemmaHandlerServiceImpl implements LemmaHandlerService {
    private static Logger logger = LogManager.getLogger(LemmaHandlerServiceImpl.class);
    private static Marker marker = MarkerManager.getMarker("INDEXING_PAGE");

    private static final String WORD_TYPE_REGEX = "\\W\\w&&[^а-яА-Я\\s]";
    private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    @Override
    public void indexingPage(PageEntity page) {
        if (isStatusCodeWrong(page.getCode())) {
            return;
        }
        logger.info(marker, "Индексирует старницу: " + page.getPath());
        SiteEntity siteEntity = page.getSite();
        try {
            Map<String, Integer> lemmasMap = collectLemmas(page);
        List<LemmaEntity> lemmasList = new ArrayList<>();
        List<IndexEntity> indexesList = new ArrayList<>();
        for (Map.Entry<String, Integer> mapEntry : lemmasMap.entrySet()) {
            LemmaEntity lemmaEntity = lemmaRepository.findByLemmaAndSiteId(mapEntry.getKey(),
                        siteEntity.getSiteId());
            if (lemmaEntity != null) {
                lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
            } else {
                lemmaEntity = new LemmaEntity(siteEntity, mapEntry.getKey(), 1);
                lemmaRepository.save(lemmaEntity);
            }
            IndexEntity indexEntity = new IndexEntity(page, lemmaEntity, mapEntry.getValue());
            indexRepository.save(indexEntity);
        }
        }  catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    private HashMap<String, Integer> collectLemmas(PageEntity pageEntity) throws IOException {
        LuceneMorphology luceneMorphology = new LemmaConfig().luceneMorphology();
        String content = cleanFromHTMLTags(pageEntity.getContent());
        HashMap<String, Integer> lemmas = new HashMap<>();
        String[] words = content.toLowerCase(Locale.ROOT).replaceAll("([^а-яё\\s])", " ")
                .trim().split("\\s+");
        for (String word : words) {
            if (word.isBlank()) continue;
            List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
            if (isIncorrectWordForm(wordBaseForms) || anyWordBaseBelongToParticle(wordBaseForms)) continue;
            List<String> normalForms = luceneMorphology.getNormalForms(word);
            if (normalForms.isEmpty()) continue;
            String normalForm = normalForms.get(0);
            int rank = lemmas.containsKey(normalForm) ? lemmas.get(normalForm) + 1 : 1;
            lemmas.put(normalForm, rank);
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

    private boolean isStatusCodeWrong(int code) {
        return String.valueOf(code).startsWith("4") || String.valueOf(code).startsWith("5");
    }

    private String cleanFromHTMLTags(String content) {
        StringBuilder builder = new StringBuilder();
        Document doc = Jsoup.parse(content);
        builder.append(doc.body().text());
        return builder.toString();
    }
}
