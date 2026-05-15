package com.erc20.platform.service.risk;

import com.erc20.platform.domain.entity.WithdrawRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class RiskControlService {

    private final List<RiskRule> rules;

    public RiskControlService(List<RiskRule> rules) {
        List<RiskRule> sorted = new ArrayList<RiskRule>(rules);
        Collections.sort(sorted, new Comparator<RiskRule>() {
            @Override
            public int compare(RiskRule a, RiskRule b) {
                return Integer.compare(a.order(), b.order());
            }
        });
        this.rules = sorted;
    }

    public RiskResult checkWithdraw(WithdrawRecord record) {
        List<String> manualReviewReasons = new ArrayList<String>();

        for (RiskRule rule : rules) {
            RiskResult result = rule.check(record);
            if (result.getStatus() == RiskStatus.REJECT) {
                log.warn("Withdrawal rejected by {}: {}", rule.getClass().getSimpleName(), result.getReason());
                return result;
            }
            if (result.getStatus() == RiskStatus.NEED_MANUAL_REVIEW) {
                manualReviewReasons.add(result.getReason());
            }
        }

        if (!manualReviewReasons.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < manualReviewReasons.size(); i++) {
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(manualReviewReasons.get(i));
            }
            return RiskResult.manualReview(sb.toString());
        }

        return RiskResult.pass();
    }
}
