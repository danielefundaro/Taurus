package com.fundaro.zodiac.taurus.service;

import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.indices.CreateIndexResponse;
import org.opensearch.client.util.ObjectBuilder;

import java.io.IOException;
import java.util.function.Function;

public interface OpenSearchService {
    boolean isOpen();

    void close() throws IOException;

    CreateIndexResponse createIndex(String indexName, TypeMapping.Builder builder) throws IOException;

    <TDocument> IndexResponse index(IndexRequest<TDocument> indexRequest) throws IOException;

    <TDocument> GetResponse<TDocument> get(Function<GetRequest.Builder, ObjectBuilder<GetRequest>> fn, Class<TDocument> documentClass) throws IOException;

    <TDocument> SearchResponse<TDocument> search(Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>> fn, Class<TDocument> documentClass) throws IOException;
}
