package natalia.dymnikova.test;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Created by dyma on 06.05.16.
 */
@Lazy
@Component
public class ComparatorForTests implements Comparator<List<Map<String, Long>>>{
    @Override
    public int compare(final List<Map<String, Long>> o1, final List<Map<String, Long>> o2) {
        return Long.compare(
                o1.stream().flatMap(map -> map.entrySet().stream().map(Map.Entry::getValue)).mapToLong(l->l).sum(),
                o2.stream().flatMap(map -> map.entrySet().stream().map(Map.Entry::getValue)).mapToLong(l->l).sum()
        );
    }
}
