package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.ForkSiteParser;
import searchengine.config.Site;
import searchengine.config.SitesList;

import searchengine.model.IndexStatus;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import searchengine.repository.IndexEntityRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

import static org.hibernate.tool.schema.SchemaToolingLogging.LOGGER;

@Service
@RequiredArgsConstructor
public class SiteIndexingService {
    @Autowired
    private final SitesList sitesList;
    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final LemmaRepository lemmaRepository;
    @Autowired
    private final IndexEntityRepository indexEntityRepository;

    private final ForkJoinPool forkJoinPool = new ForkJoinPool();
    private volatile boolean stopIndexing = false;

    public void startIndexing() {
        if (stopIndexing) {
            return;
        }
        if (siteRepository != null || pageRepository != null ||
                lemmaRepository != null || indexEntityRepository != null) {
            // Удаляем все имеющиеся данные по этому сайту
            indexEntityRepository.deleteAll();
            lemmaRepository.deleteAll();
            pageRepository.deleteAll();
            siteRepository.deleteAll();
        }
        for (Site site : sitesList.getSites()) {
            try {
                // Создаем новую запись в таблице site со статусом INDEXING
                SiteEntity siteEntity = new SiteEntity();
                siteEntity.setUrl(site.getUrl());
                siteEntity.setName(site.getName());
                siteEntity.setStatus(IndexStatus.INDEXING);
                siteEntity.setStatusTime(LocalDateTime.now());
                siteRepository.save(siteEntity);

                // Обходим все страницы, начиная с главной
                String mainPageUrl = site.getUrl().replaceAll("www.", "");
                ForkSiteParser parser = new ForkSiteParser(mainPageUrl);
//                TreeSet<String> urlForkJoinParser = new TreeSet<>(new ForkJoinPool().invoke(parser));
                TreeSet<String> urlForkJoinParser = new TreeSet<>(forkJoinPool.invoke(parser));
                ForkJoinTask<Set<String>> task = forkJoinPool.submit(parser);

                System.out.println("Индексируется сайт " + site.getName());

                for (String pageUrl : urlForkJoinParser) {
                    if (stopIndexing) {
                        break;
                    }
                    if (pageUrl.equals(siteEntity.getUrl())) {
                        continue;
                    }

                    Document doc = Jsoup.connect(pageUrl).get();
                    String content = doc.body().html();

                    try {
                        // Создаем новую запись в таблице page
                        PageEntity pageEntity = new PageEntity();
                        pageEntity.setSiteEntity(siteEntity);
                        pageEntity.setPath(pageUrl.replaceAll(mainPageUrl, ""));
                        pageEntity.setCode(doc.connection().response().statusCode());
                        pageEntity.setContent(content);
                        pageRepository.save(pageEntity);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (!stopIndexing && task.isDone()) {
                    System.out.println("Индексирование сайта " + site.getName() + " завершена");

                    // Меняем статус на INDEXED
                    siteEntity.setStatus(IndexStatus.INDEXED);
                    siteRepository.save(siteEntity);
                }
            } catch (HttpStatusException h) {
                h.printStackTrace();

            } catch (Exception e) {
                System.out.println("ОШИБКА ИНДЕКСАЦИИ");
                // Меняем статус на FAILED и вносим информацию о произошедшей ошибке
                SiteEntity siteEntity = siteRepository.findAll().stream().
                        filter(se -> se.getUrl().equals(site.getUrl())).
                        findFirst().orElse(null);
                siteEntity.setStatus(IndexStatus.FAILED);
                siteEntity.setLastError(e.getMessage());
                siteRepository.save(siteEntity);
            }
        }
    }
    public void stopIndexing() {
        LOGGER.info("Запущен процесс остановки индексация сайтов");
        try {
            stopIndexing = true;
            if (forkJoinPool != null) {
                forkJoinPool.shutdownNow();
            }
            // Обновляем статус сайтов, как и в вашем коде
            List<SiteEntity> siteEntities = siteRepository.findAll();
            for (SiteEntity siteEntity : siteEntities) {
                if (siteEntity.getStatus() == IndexStatus.INDEXING) {
                    siteEntity.setStatus(IndexStatus.FAILED);
                    siteEntity.setLastError("Индексация остановлена пользователем");
                    siteRepository.save(siteEntity);
                }
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Error stopping site indexing", e);
        }
        LOGGER.info("Индексация успешно остановлена");
    }
}
