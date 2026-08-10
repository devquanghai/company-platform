package com.company.platform.queue.autoconfigure.properties;

import com.company.platform.queue.domain.policy.TopologyDeclarationMode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TopologyProperties {
    private TopologyDeclarationMode mode = TopologyDeclarationMode.VALIDATE_ONLY;
}
