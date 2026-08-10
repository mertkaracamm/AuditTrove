package com.audittrove.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RegulationRetriever {
    private final List<RegulationChunk> corpus;
    private final RegulationStore regulationStore;
    private final int resultLimit;

    public RegulationRetriever(ObjectMapper objectMapper,
                               RegulationStore regulationStore,
                               @Value("${audittrove.rag.result-limit:8}") int resultLimit) throws IOException {
        this.regulationStore = regulationStore;
        this.resultLimit = resultLimit;
        try (var input = new ClassPathResource("regulations/tr/financial-regulations.json").getInputStream()) {
            this.corpus = objectMapper.readValue(input, new TypeReference<>() {});
        }
    }

    public List<RegulationChunk> retrieve(String documentText) {
        String normalizedDocument = normalize(documentText);
        Set<String> documentTerms = terms(normalizedDocument);
        String searchQuery = documentTerms.stream().limit(80).collect(Collectors.joining(" "));
        List<RegulationChunk> databaseMatches = regulationStore.search(searchQuery, resultLimit);
        if (!databaseMatches.isEmpty()) {
            return databaseMatches;
        }
        List<ScoredChunk> ranked = corpus.stream()
                .filter(chunk -> chunk.activeOn(java.time.LocalDate.now()))
                .map(chunk -> new ScoredChunk(chunk, score(chunk, normalizedDocument, documentTerms)))
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .toList();

        List<RegulationChunk> matches = ranked.stream()
                .filter(item -> item.score() > 0)
                .limit(resultLimit)
                .map(ScoredChunk::chunk)
                .toList();
        return matches.isEmpty() ? corpus.stream().limit(resultLimit).toList() : matches;
    }

    private double score(RegulationChunk chunk, String normalizedDocument, Set<String> documentTerms) {
        long keywordMatches = Arrays.stream(chunk.keywords() == null ? new String[0] : chunk.keywords())
                .map(this::normalize)
                .filter(keyword -> containsKeyword(normalizedDocument, documentTerms, keyword))
                .count();
        long textMatches = terms(chunk.title() + " " + chunk.text()).stream()
                .filter(documentTerms::contains)
                .count();
        return keywordMatches * 4.0 + textMatches;
    }

    private boolean containsKeyword(String normalizedDocument, Set<String> documentTerms, String keyword) {
        return keyword.indexOf(' ') >= 0
                ? normalizedDocument.contains(keyword)
                : documentTerms.contains(keyword);
    }

    private Set<String> terms(String value) {
        return Arrays.stream(normalize(value).split("[^a-z0-9çğıöşü]+"))
                .filter(term -> term.length() > 2)
                .collect(Collectors.toSet());
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.forLanguageTag("tr"));
    }

    private record ScoredChunk(RegulationChunk chunk, double score) {
    }
}
