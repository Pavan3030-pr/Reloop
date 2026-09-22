package app.reloop.dto.pickup;

import java.util.List;

/**
 * The filter values that are actually present in the open pool right now.
 *
 * <p>Derived from the pending requests rather than from the full waste-category catalogue, so a
 * collector is never offered a filter that cannot match anything. Both lists are aggregates over
 * redacted data only — a city name and a material code, never a resident's details.
 */
public record OpenPoolFiltersDto(
        List<String> cities,
        List<String> materialCodes
) {}
