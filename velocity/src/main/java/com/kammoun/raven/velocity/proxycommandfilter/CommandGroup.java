package com.kammoun.raven.velocity.proxycommandfilter;

import java.util.List;
import java.util.Set;

record CommandGroup(String name, String permission, List<String> inherits, Set<String> commands) {
}
