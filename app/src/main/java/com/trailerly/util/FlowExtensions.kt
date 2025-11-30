package com.trailerly.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce

/**
 * Extension function that debounces a Flow, delaying emissions until the specified time
 * has passed without new values. Useful for search-as-you-type functionality to prevent
 * excessive API calls while the user is typing.
 *
 * @param timeoutMillis The delay in milliseconds before emitting the latest value.
 * @return A Flow that emits values after the debounce timeout.
 */
fun <T> Flow<T>.debounceMillis(timeoutMillis: Long): Flow<T> = debounce(timeoutMillis)
