package searchengine.dto.search;

import lombok.*;

import java.util.List;

@Data
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SearchData {
    private boolean result;
    private int count;
    private List<SearchItem> data;
}
