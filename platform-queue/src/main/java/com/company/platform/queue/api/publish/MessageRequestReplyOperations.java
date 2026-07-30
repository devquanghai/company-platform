package com.company.platform.queue.api.publish;

import java.time.Duration;

public interface MessageRequestReplyOperations {
    <REQ, RES> RES request(
        String destination, REQ request, Class<RES> responseType, Duration timeout);
}
