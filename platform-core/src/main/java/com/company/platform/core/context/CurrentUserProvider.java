package com.company.platform.core.context;

import java.util.Optional;

public interface CurrentUserProvider {
    Optional<CurrentUser> currentUser();
}
