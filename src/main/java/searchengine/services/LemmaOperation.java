import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.ForkSiteParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;

public class LemmaOperation {
    public static void main(String[] args) throws IOException {
//        String textExample = "Ну, повторное появление леопарда в Осетии позволяет предположить, " +
//                "что леопард постоянно обитает в некоторых районах Северного " +
//                "Кавказа.";
//        HashMap<String, Integer> d = lemmaCounter(textExample);
//        for (Map.Entry<String, Integer> s : d.entrySet()) {
//            System.out.println(s);
//        }
        String url = "https://sendel.ru";
        TreeSet<String> urlForkJoinParser = new TreeSet<>(new ForkJoinPool().
                invoke(new ForkSiteParser(url)));
        for (String pageUrl : urlForkJoinParser) {
            Document doc = Jsoup.connect(pageUrl).get();
            String content = doc.body().html();
            HashMap<String, Integer> d = lemmaCounter(removeHtmlTags(content));
            for (Map.Entry<String, Integer> s : d.entrySet()) {
                System.out.println(s);
            }
        }

//        Document doc = Jsoup.connect("http://sendel.ru").
//                userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6").get();
//        Elements element = doc.select("a");
//        for (Element e : element) {
//            String absUrl = e.absUrl("href");
//
//            Document document = Jsoup.connect(absUrl).get();
//            System.out.println(absUrl + "\n");
//            HashMap<String, Integer> d = lemmaCounter(document.text());
//            for (Map.Entry<String, Integer> s : d.entrySet()) {
//                System.out.println(s);
//            }
//        }
    }
    public static String splitTextIntoWords(String text) {

        StringBuilder myText = new StringBuilder();
        String regex = "[^А-Яа-я]";
        text = text.replaceAll(regex, " ");


        String[] words = text.split("\s+");

        if (text.isBlank()) {
            return "";
        }

        for (String word : words) {

            myText.append(word).append("\n");
        }
        return myText.toString().strip();
    }

    public static HashMap<String, Integer> lemmaCounter (String string) throws IOException {
        RussianLuceneMorphology luceneMorph = new RussianLuceneMorphology();
        HashMap<String, Integer> lemmaCounts = new HashMap<>();

        String[] lemmaString = splitTextIntoWords(string).toLowerCase().split("\n");
        for (String word : lemmaString) {
            List<String> wordBaseForms = luceneMorph.getMorphInfo(word);

            boolean isValid = false;
            for (String baseForm : wordBaseForms) {
                String[] parts = baseForm.split("\\|");

                String pos = parts[1];

                if (!pos.matches("n СОЮЗ") && !pos.matches("o МЕЖД") && !pos.matches("l ПРЕДЛ") && !pos.matches("p ЧАСТ")) {
                    isValid = true;
                    break;
                }
            }

            if (isValid) {
                String lemma = wordBaseForms.get(0).split("\\|")[0];
                lemmaCounts.put(lemma, lemmaCounts.getOrDefault(lemma, 0) + 1);
            }
        }
        return lemmaCounts;
    }
    public static String removeHtmlTags(String htmlContent) {
        String regex = "<.*?>";
        return htmlContent.replaceAll(regex, "");
    }
}
