package searchengine.services;

import lombok.AllArgsConstructor;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.DTOClasses.IndexDTO;
import searchengine.DTOClasses.LemmaDTO;
import searchengine.DTOClasses.PageDTO;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexEntityRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;

import static org.hibernate.tool.schema.SchemaToolingLogging.LOGGER;

@RequiredArgsConstructor
public class InteractiveClass {
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexEntityRepository indexEntityRepository;
    private final ForkJoinPool forkJoinPool = new ForkJoinPool();
    private final LemmaOperation lemmaOperation = new LemmaOperation();
    private final DTOTransferService dtoTransferService = new DTOTransferService();

    private void deleteExistingPagesAndIndexes(SiteEntity siteEntity, String url) {
        List<PageEntity> existingPageEntities = pageRepository.findAll().stream()
                .filter(pe -> Objects.equals(pe.getPath(), url) || Objects.equals(pe.getSiteEntity(), siteEntity))
                .toList();

        for (PageEntity existingPageEntity : existingPageEntities) {
            List<IndexEntity> indexEntities = indexEntityRepository.findAll().stream()
                    .filter(ie -> ie.getPageEntity().equals(existingPageEntity))
                    .toList();
            indexEntities.forEach(indexEntityRepository::delete);
            pageRepository.delete(existingPageEntity);
        }
        pageRepository.flush();

        List<LemmaEntity> lemmaEntities = lemmaRepository.findAll().stream()
                .filter(le -> le.getSiteEntity().equals(siteEntity))
                .toList();
        lemmaEntities.forEach(lemmaRepository::delete);
    }
    private void getPageLemmaIndexSiteMethod(SiteEntity siteEntity, String url) throws IOException {
        String mainPageUrl = url.replaceAll("www.", "");
        ForkSiteParser parser = new ForkSiteParser(mainPageUrl);
        TreeSet<String> urlForkJoinParser = new TreeSet<>(forkJoinPool.invoke(parser));

        for (String pageUrl : urlForkJoinParser) {
            if (pageUrl.equals(siteEntity.getUrl())) {
                continue;
            }
            Document doc = Jsoup.connect(pageUrl).get();
            String content = doc.body().html();
            try {
                PageDTO pageDTO = new PageDTO();
                pageDTO.setPath(pageUrl.replaceAll(mainPageUrl, ""));
                pageDTO.setCode(doc.connection().response().statusCode());
                pageDTO.setContent(content);
                pageDTO.setSiteEntityId(siteEntity.getId());
                PageEntity pageEntity = dtoTransferService.mapToPageEntity(pageDTO, siteEntity);
                pageRepository.save(pageEntity);

                String text = lemmaOperation.removeHtmlTags(content);
                HashMap<String, Integer> lemmaCounts = lemmaOperation.lemmaCounter(text);

                lemmaCounts.forEach((lemma, frequency) -> {
                    LemmaDTO lemmaDTO = new LemmaDTO();
                    lemmaDTO.setLemma(lemma);
                    lemmaDTO.setFrequency(frequency);
                    lemmaDTO.setSiteEntityId(siteEntity.getId());
                    LemmaEntity lemmaEntity = dtoTransferService.mapToLemmaEntity(lemmaDTO, siteEntity);
                    lemmaRepository.save(lemmaEntity);

                    IndexDTO indexDTO = new IndexDTO();
                    indexDTO.setRank((float) frequency);
                    indexDTO.setLemmaEntityId(lemmaEntity.getId());
                    indexDTO.setPageEntityId(pageEntity.getId());
                    IndexEntity indexEntity = dtoTransferService.mapToIndexEntity(indexDTO, lemmaEntity, pageEntity);
                    indexEntityRepository.save(indexEntity);
                });
            } catch (Exception e) {
                LOGGER.info("Ошибка: " + e);
            }
        }
    }
}
