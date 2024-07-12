package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchItem;
import searchengine.dto.search.SearchResponse;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;
import searchengine.repository.IndexEntityRepository;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class SearchWordsService {
    @Autowired
    private final IndexEntityRepository indexEntityRepository;
    private final LemmaOperation lemmaOperation = new LemmaOperation();


    public SearchResponse searchLemmaMethod(String query, String site, int offset, int limit) throws IOException {
        // Делю запрос на слова и делаю их леммами
        LemmaFinder lemmaFinder = LemmaFinder.getInstance();
        Map<String, Integer> lemmaCounts = lemmaFinder.collectLemmas(query);

        // Исключать из полученного списка леммы, которые встречаются на слишком большом количестве страниц
        lemmaCounts = filterFrequentLemmas(lemmaCounts);

        // Сортируем по количеству frequency Integer
        List<Map.Entry<String, Integer>> sortedLemmas = new ArrayList<>(lemmaCounts.entrySet());
        sortedLemmas.sort(Map.Entry.comparingByValue());

        // Находим pages, которые соответсвуют lemma
        List<SearchItem> searchItems = new ArrayList<>();
        for (Map.Entry<String, Integer> lemma : sortedLemmas) {
            List<IndexEntity> indexes;
            if (site!= null &&!site.isEmpty()) {
                indexes = indexEntityRepository.findAll().stream()
                        .filter(index -> index.getLemmaEntity().getLemma().equals(lemma.getKey()) && index.getPageEntity().getSiteEntity().getUrl().equals(site))
                        .toList();
            } else {
                indexes = indexEntityRepository.findAll().stream()
                        .filter(index -> index.getLemmaEntity().getLemma().equals(lemma.getKey()))
                        .toList();
            }
            for (IndexEntity index : indexes) {
                PageEntity page = index.getPageEntity();

                SearchItem searchItem = new SearchItem();
                searchItem.setSite(page.getSiteEntity().getUrl());
                searchItem.setSiteName(page.getSiteEntity().getName());
                searchItem.setUri(page.getPath());
                Document doc = Jsoup.parse(page.getContent());
                String title = doc.select("title").text();
                searchItem.setTitle(title);

                String snippet = generateSnippet(removeHtmlTags(page.getContent()), lemmaCounts);
                searchItem.setSnippet(snippet);

                //TODO: relevance methods
                TreeMap<Float, PageEntity> relevanceMap = getRelevance(indexes);

                for (Map.Entry<Float, PageEntity> entry : relevanceMap.entrySet()) {
                    float relevance = entry.getKey();
                    PageEntity pageEntity = entry.getValue();
                    SearchItem searchItemForRelevance = searchItems.stream()
                            .filter(si -> si.getUri().equals(pageEntity.getPath()))
                            .findFirst()
                            .orElse(null);
                    if (searchItemForRelevance != null) {
                        searchItem.setRelevance(relevance);
                    }
                }

                searchItems.add(searchItem);
            }
        }
        // сортируем по релевантности
        searchItems.sort((a, b) -> Double.compare(b.getRelevance(), a.getRelevance()));

        // добавляю в reponse класс
        SearchResponse response = new SearchResponse();
        response.setResult(!searchItems.isEmpty());
        response.setCount(searchItems.size());
        response.setData(searchItems);
        return response;
    }

    private Map<String, Integer> filterFrequentLemmas(Map<String, Integer> lemmaCounts) {
        int threshold = 6; // если 6 - не добавлять в список
        Map<String, Integer> filteredLemmas = new HashMap<>();
        for (Map.Entry<String, Integer> lemma : lemmaCounts.entrySet()) {
            if (lemma.getValue() < threshold) {
                filteredLemmas.put(lemma.getKey(), lemma.getValue());
            }
        }
        return filteredLemmas;
    }
    private String removeHtmlTags(String htmlContent) {
        return Jsoup.parse(htmlContent).select("body").text();
    }

    private String generateSnippet(String content, Map<String, Integer> lemmaCounts) throws IOException {
        LemmaFinder lemmaFinder = LemmaFinder.getInstance();
        List<String> matchingLemmas = lemmaCounts.keySet().stream()
                .filter(lemmaCounts::containsKey)
                .toList();

        StringBuilder snippet = new StringBuilder();
        matchingLemmas.stream()
                .map(lemma -> {
                    Set<String> lemmaSet = lemmaFinder.getLemmaSet(lemma);

                    for (String lemmaVariant : lemmaSet) {
                        int index = content.toLowerCase().indexOf(lemmaVariant.toLowerCase());
                        if (index!= -1) {
                            int startIndex = Math.max(0, index - 100);
                            int endIndex = Math.min(content.length(), index + lemmaVariant.length() + 100);

                            String snippetPart = content.substring(startIndex, endIndex);
                            String text = Jsoup.parse(snippetPart).text().replaceAll("<.*?>", "");
                            text = text.toLowerCase().replaceAll(lemmaVariant.toLowerCase(), "<b>" + lemmaVariant.toLowerCase() + "</b>");
                            return "..." + text + "...";
                        }
                    }

                    return "";
                })
                .filter(snippetPart ->!snippetPart.isEmpty())
                .forEach(snippet::append);
        return snippet.toString().trim();
    }
    private Map<PageEntity, Float> getAbsoluteRelevance(List<IndexEntity> pagesList) {
        Map<PageEntity, Float> pagesARelevance = new HashMap<>();
        for (IndexEntity index : pagesList) {
            PageEntity pageEntity = index.getPageEntity();
            if (pagesARelevance.containsKey(pageEntity)) {
                float absoluteRelevance = pagesARelevance.get(pageEntity) + index.getRank();
                pagesARelevance.put(pageEntity, absoluteRelevance);
            } else {
                pagesARelevance.put(pageEntity, index.getRank());
            }
        }
        return pagesARelevance;
    }
    private TreeMap<Float, PageEntity> getRelevance(List<IndexEntity> pagesList) {
        Map<PageEntity, Float> pagesAbsoluteRelevance = getAbsoluteRelevance(pagesList);
        float maxAbsoluteRelevance = pagesAbsoluteRelevance.values().stream().max(Float::compare).orElse(Float.MIN_VALUE);

        TreeMap<Float, PageEntity> pagesRelevance = new TreeMap<>(Comparator.reverseOrder());
        for (Map.Entry<PageEntity, Float> entry : pagesAbsoluteRelevance.entrySet()) {
            float relevance = entry.getValue() / maxAbsoluteRelevance;
            pagesRelevance.put(relevance, entry.getKey());
        }

        return pagesRelevance;
    }

//    private HashMap<Float, Integer> getAbsoluteRelevance(List<IndexEntity> pagesList) {
//        HashMap<Float, Integer> pagesARelevance = new HashMap<>();
//        for (IndexEntity index : pagesList) {
//            float absoluteRelevance = 0f;
//            for (IndexEntity idx : pagesList) {
//                if (idx.getPageEntity().getId().equals(index.getPageEntity().getId())) {
//                    absoluteRelevance += idx.getRank();
//                }
//            }
//            pagesARelevance.put(absoluteRelevance, index.getPageEntity().getId());
//        }
//        return pagesARelevance;
//    }
//
//    private TreeMap<Float, Integer> getRelevance(List<IndexEntity> pagesList) {
//        TreeMap<Float, Integer> pagesRelevance = new TreeMap<>();
//        HashMap<Float, Integer> pagesAbsoluteRelevance = getAbsoluteRelevance(pagesList);
//
//        float maxAbsoluteRelevance = Float.MIN_VALUE;
//
//        // Find the maximum absolute relevance
//        for (Map.Entry<Float, Integer> entry : pagesAbsoluteRelevance.entrySet()) {
//            if (entry.getKey() > maxAbsoluteRelevance) {
//                maxAbsoluteRelevance = entry.getKey();
//            }
//        }
//
//        // Calculate relative relevance and add to TreeMap
//        for (Map.Entry<Float, Integer> entry : pagesAbsoluteRelevance.entrySet()) {
//            float relevance = entry.getKey() / maxAbsoluteRelevance;
//            pagesRelevance.put(relevance, entry.getValue());
//        }
//
//        return pagesRelevance;
//    }

}
