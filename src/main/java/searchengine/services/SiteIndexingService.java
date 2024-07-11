package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.repository.IndexEntityRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

import static org.hibernate.tool.schema.SchemaToolingLogging.LOGGER;

@Service
@RequiredArgsConstructor
public class SiteIndexingService {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexEntityRepository indexEntityRepository;

    private final ForkJoinPool forkJoinPool = new ForkJoinPool();
    private volatile boolean IsStopIndexing = false;
    private final LemmaOperation lemmaOperation = new LemmaOperation();
    private final PageLemmaIndexClass pageLemmaIndexClass;

    public void startIndexing() {
        if (IsStopIndexing) {
            return;
        }
        LOGGER.info("Запускается индексация сайтов");
        if (siteRepository != null ||
                pageRepository != null ||
                lemmaRepository != null ||
                indexEntityRepository != null) {
            // Удаляем все имеющиеся данные по сайту
            indexEntityRepository.deleteAll();
            lemmaRepository.deleteAll();
            pageRepository.deleteAll();
            siteRepository.deleteAll();
        }
        for (Site site : sitesList.getSites()) {
            forkJoinPool.submit(new RecursiveSiteIndexingService(site, lemmaOperation,
                    siteRepository, pageRepository,
                    lemmaRepository, indexEntityRepository, pageLemmaIndexClass));
        }
    }

    public void stopIndexing() {
        LOGGER.info("Запущен процесс остановки индексация сайтов");
        try {
            IsStopIndexing = true;
            if (forkJoinPool!= null) {
                forkJoinPool.shutdownNow();
            }
            // Обновляем статус сайтов
            List<SiteEntity> siteEntities = siteRepository.findAll();
            for (SiteEntity siteEntity : siteEntities) {
                if (siteEntity.getStatus() == IndexStatus.INDEXING) {

                    siteEntity.setStatus(IndexStatus.FAILED);
                    siteEntity.setLastError("Индексация остановлена пользователем");
                    siteRepository.saveAndFlush(siteEntity);
                }
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Ошибка остановки индексации", e);
        }
        LOGGER.info("Индексация успешно остановлена");
    }


    public void indexPageMethod(String url) throws IOException {
        SiteEntity siteEntity = siteRepository.findAll().stream()
                .filter(se -> Objects.equals(se.getUrl(), url))
                .findFirst()
                .orElseThrow();

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

        pageLemmaIndexClass.getPageLemmaIndexSiteMethod(siteEntity, url);
        LOGGER.info("Индексация сайта " + url + "завершена");
    }
}
