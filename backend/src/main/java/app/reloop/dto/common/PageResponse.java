package app.reloop.dto.common;

import org.springframework.data.domain.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The stable JSON contract for every paged endpoint.
 *
 * <p>Returning Spring Data's {@code Page}/{@code PageImpl} straight from a controller makes the
 * serialized shape depend on the Spring Data version: Jackson writes whatever fields the current
 * {@code PageImpl} happens to expose (including the internal {@code pageable} and {@code sort}
 * objects), and Spring itself logs a warning that the structure is not guaranteed stable. This record
 * pins the fields the client can rely on — the same names the web client has always used — so a
 * framework upgrade can never silently reshape an API response.
 *
 * <p>{@code content} and {@code totalElements} are deliberately kept at the top level (not nested
 * under a {@code page} object) to preserve the existing contract, including the paged
 * {@code entries} inside the recycling-history response.
 */
public record PageResponse<T>(
        List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    /** Maps the content while preserving every pagination field. */
    public <R> PageResponse<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = new ArrayList<>(content.size());
        for (T item : content) {
            mapped.add(mapper.apply(item));
        }
        return new PageResponse<>(mapped, number, size, totalElements, totalPages, first, last);
    }
}
