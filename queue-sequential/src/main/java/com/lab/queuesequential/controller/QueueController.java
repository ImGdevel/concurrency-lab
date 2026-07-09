package com.lab.queuesequential.controller;

import com.lab.queuesequential.dto.EnterResponse;
import com.lab.queuesequential.dto.RankResponse;
import com.lab.queuesequential.dto.TokenIssueResponse;
import com.lab.queuesequential.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping("/token")
    public TokenIssueResponse issueToken() {
        return queueService.issueToken();
    }

    @GetMapping("/rank/{token}")
    public RankResponse rank(@PathVariable String token) {
        return queueService.getRank(token);
    }

    @PostMapping("/enter/{token}")
    public EnterResponse enter(@PathVariable String token) {
        return queueService.enter(token);
    }
}
