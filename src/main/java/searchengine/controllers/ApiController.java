package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.responses.Response;
import searchengine.dto.responses.TrueResponse;
import searchengine.services.indexing.IndexingPageService;
import searchengine.services.indexing.IndexingService;
import searchengine.services.statistics.StatisticsService;
import searchengine.services.indexing.LemmaHandlerService;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final IndexingPageService indexingPageService;



    public ApiController(StatisticsService statisticsService, IndexingService indexingService,
                         IndexingPageService indexingPageService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.indexingPageService = indexingPageService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<Response> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Response> startIndexing() {
        return ResponseEntity.ok(indexingService.startIndexing());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Response> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndexing());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<Response> indexPage(String url) {
        try {
            Response response = indexingPageService.indexPage(url);
            if (response instanceof TrueResponse) {
                return ResponseEntity.ok(response);
            } else {
                return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
            }
        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
    }
}
