package com.lab.couponfcfs.controller;

import com.lab.couponfcfs.dto.IssueRequest;
import com.lab.couponfcfs.dto.IssueResponse;
import com.lab.couponfcfs.dto.StockResponse;
import com.lab.couponfcfs.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/{couponId}/init")
    public StockResponse init(@PathVariable String couponId, @RequestParam long stock) {
        couponService.init(couponId, stock);
        return couponService.getStock(couponId);
    }

    @GetMapping("/{couponId}/stock")
    public StockResponse stock(@PathVariable String couponId) {
        return couponService.getStock(couponId);
    }

    @PostMapping("/{couponId}/issue-naive")
    public IssueResponse issueNaive(@PathVariable String couponId, @RequestBody IssueRequest request) {
        return couponService.issueNaive(couponId, request.userId());
    }

    @PostMapping("/{couponId}/issue")
    public IssueResponse issue(@PathVariable String couponId, @RequestBody IssueRequest request) {
        return couponService.issue(couponId, request.userId());
    }
}
