package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchItem;
import searchengine.dto.search.SearchResponse;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;
import searchengine.repository.IndexEntityRepository;

import java.io.IOException;
import java.util.*;


@Service
@RequiredArgsConstructor
public class SearchWordsService {
    @Autowired
    private final IndexEntityRepository indexEntityRepository;
    private final LemmaOperation lemmaOperation = new LemmaOperation();


    public SearchResponse searchLemmaMethod(String query, String site, int offset, int limit) throws IOException {
        // Делю запрос на слова и делаю их леммами
        HashMap<String, Integer> lemmaCounts = lemmaOperation.lemmaCounter(query);

        // Исключать из полученного списка леммы, которые встречаются на слишком большом количестве страниц
        lemmaCounts = filterFrequentLemmas(lemmaCounts);

        // Сортируем по количеству frequency Integer
        List<Map.Entry<String, Integer>> sortedLemmas = new ArrayList<>(lemmaCounts.entrySet());
        sortedLemmas.sort(Map.Entry.comparingByValue());

        // Находим pages, которые соответсвуют lemma
        List<SearchItem> searchItems = new ArrayList<>();
        for (Map.Entry<String, Integer> lemma : sortedLemmas) {
            List<IndexEntity> indexes = indexEntityRepository.findAll().stream()
                    .filter(index -> index.getLemmaEntity().getLemma().equals(lemma.getKey()))
                    .toList();
            for (IndexEntity index : indexes) {
                PageEntity page = index.getPageEntity();

                SearchItem searchItem = new SearchItem();
                searchItem.setSite(page.getSiteEntity().getUrl());
                searchItem.setSiteName(page.getSiteEntity().getName());
                searchItem.setUri(page.getPath());
                Document doc = Jsoup.parse(page.getContent());
                Element titleElement = doc.select("h2.article-title").first();
                String title = titleElement.text();
                searchItem.setTitle(title);

                String snippet = generateSnippet(lemmaOperation.removeHtmlTags(page.getContent()), query);
                searchItem.setSnippet(snippet);

                //TODO: relevance methods
                searchItem.setRelevance(calculateAbsoluteRelevance(indexes));


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
    private float calculateAbsoluteRelevance(List<IndexEntity> indexes) {
        float absoluteRelevance = 0f;
        for (IndexEntity index : indexes) {
            absoluteRelevance += index.getRank();
        }
        return absoluteRelevance;
    }

//    private HashMap<Float, Integer> getAbsoluteRelevance(List<IndexEntity> pagesList) {
//        HashMap<Float, Integer> pagesARelevance = new HashMap<>();
//        if (!pagesList.isEmpty()) {
//            int pageId = pagesList.get(0).getPageEntity().getId();
//            float absoluteRelevance = 0f;
//            for (IndexEntity index : pagesList) {
//                if (index.getPageEntity().getId() == pageId) {
//                    absoluteRelevance += index.getRank();
//                }
//            }
//            pagesARelevance.put(absoluteRelevance, pagesList.get(0).getId());
//            pagesList.remove(0);
//            getAbsoluteRelevance(pagesList);
//        }
//        return pagesARelevance;
//    }
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

    private HashMap<String, Integer> filterFrequentLemmas(HashMap<String, Integer> lemmaCounts) {
        int threshold = 6; // если 6 - не добавлять в список
        HashMap<String, Integer> filteredLemmas = new HashMap<>();
        for (Map.Entry<String, Integer> lemma : lemmaCounts.entrySet()) {
            if (lemma.getValue() < threshold) {
                filteredLemmas.put(lemma.getKey(), lemma.getValue());
            }
        }
        return filteredLemmas;
    }
    private String generateSnippet(String content, String query) {
        // Создаем список индексов, где найден запрос
        List<Integer> correctIndices = new ArrayList<>();
        int index = content.toLowerCase().indexOf(query.toLowerCase());
        while (index!= -1) {
            correctIndices.add(index);
            index = content.toLowerCase().indexOf(query.toLowerCase(), index + 1);
        }
        // Создаем StringBuilder для формирования сниппета
        StringBuilder snippet = new StringBuilder();
        for (Integer correctIndex : correctIndices) {
            // Определяем начальный и конечный индексы для вырезки части текста
            int startIndex = Math.max(0, correctIndex - 100);
            int endIndex = Math.min(content.length(), correctIndex + query.length() + 100);

            String snippetPart = content.substring(startIndex, endIndex);
            String text = Jsoup.parse(snippetPart).text().replaceAll("<.*?>", "");
            text = text.toLowerCase().replaceAll(query.toLowerCase(), "<b>" + query.toLowerCase() + "</b>");
            snippet.append("...").append(text).append("...");
        }
        return snippet.toString().trim();
    }
}
