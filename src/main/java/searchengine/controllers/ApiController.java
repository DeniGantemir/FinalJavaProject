package searchengine.controllers;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;

import searchengine.services.SearchWordsService;
import searchengine.services.SiteIndexingService;
import searchengine.services.StatisticsService;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final SiteIndexingService siteIndexingService;
    private final SearchWordsService searchWordsService;


    public ApiController(StatisticsService statisticsService,
                         SiteIndexingService siteIndexingService,
                         SearchWordsService searchWordsService) {
        this.statisticsService = statisticsService;
        this.siteIndexingService = siteIndexingService;
        this.searchWordsService = searchWordsService;
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
    @PostMapping("/indexPage")
    public ResponseEntity indexPage(@RequestParam("url") String url) {
        try {
            siteIndexingService.indexPageMethod(url);
            return ResponseEntity.ok(Map.of("result", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("result", false, "error", "Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле"));
        }
    }
//    @PostMapping("/search")
//    public ResponseEntity<SearchResponse> searchWords(@RequestParam("query") String query, @RequestParam("site") String site,
//                                                      @RequestParam("offset") String offset, @RequestParam("query") String limit) {
//        try {
//            return null;
//        } catch (Exception e) {
//            return null;
//        }
//    }
    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestParam("query") String query,
                                                 @RequestParam(value = "site", required = false) String site,
                                                 @RequestParam(value = "offset", defaultValue = "0") int offset,
                                                 @RequestParam(value = "limit", defaultValue = "2") int limit) throws IOException {
        if (query.isEmpty()) {
            return ResponseEntity.badRequest().body(new SearchResponse(false, 0, null));
        }

        SearchResponse response = searchWordsService.searchLemmaMethod(query, site, offset, limit);
        return ResponseEntity.ok(response);
    }
}
