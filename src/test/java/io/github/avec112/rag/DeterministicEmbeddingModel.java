package io.github.avec112.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

/**
 * Offline stand-in for a real embedding model: a hashed bag-of-words vector, L2-normalized.
 * Similarity therefore tracks lexical overlap, which is deterministic and enough to verify that
 * ingestion, metadata and retrieval are wired correctly. It says nothing about semantic quality.
 */
class DeterministicEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 256;

    @Override
    public float[] embed(Document document) {
        return embed(Objects.requireNonNullElse(document.getText(), ""));
    }

    @Override
    public float[] embed(String text) {
        var vector = new float[DIMENSIONS];
        for (var token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!token.isEmpty()) {
                vector[Math.floorMod(token.hashCode(), DIMENSIONS)] += 1f;
            }
        }
        var norm = 0d;
        for (var value : vector) {
            norm += value * value;
        }
        if (norm == 0d) {
            vector[0] = 1f;
            return vector;
        }
        var length = (float) Math.sqrt(norm);
        for (var i = 0; i < vector.length; i++) {
            vector[i] /= length;
        }
        return vector;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        var instructions = request.getInstructions();
        var embeddings = new ArrayList<Embedding>(instructions.size());
        for (var i = 0; i < instructions.size(); i++) {
            embeddings.add(new Embedding(embed(instructions.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }
}
