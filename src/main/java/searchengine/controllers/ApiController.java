package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.IndexStatus;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexEntityRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.SiteIndexingService;
import searchengine.services.StatisticsService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexEntityRepository indexRepository;

    private final SiteIndexingService siteIndexingService;


    public ApiController(StatisticsService statisticsService, SiteRepository siteRepository,
                         PageRepository pageRepository, LemmaRepository lemmaRepository,
                         IndexEntityRepository indexRepository, SiteIndexingService siteIndexingService) {
        this.statisticsService = statisticsService;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.siteIndexingService = siteIndexingService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity startIndexing() {
        try {
            siteIndexingService.startIndexing();
            return ResponseEntity.ok(Map.of("result", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("result", false, "error", "Индексация уже запущена"));
        }
    }
    @GetMapping("/stopIndexing")
    public ResponseEntity stopIndexing() {
        try {
            siteIndexingService.stopIndexing();
            return ResponseEntity.ok(Map.of("result", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("result", false, "error", "Индексация уже запущена"));
        }
    }
}
