package com.company.platform.core.mapper;

import java.util.List;

public interface PlatformMapper<S, T> {

    T toTarget(S source);

    S toSource(T target);

    List<T> toTargets(List<S> sources);

    List<S> toSources(List<T> targets);
}
