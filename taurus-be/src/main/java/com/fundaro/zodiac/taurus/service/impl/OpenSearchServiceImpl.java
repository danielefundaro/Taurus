package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.indices.CreateIndexResponse;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.endpoints.BooleanResponse;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.opensearch.client.util.ObjectBuilder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.function.Function;

@Service
public class OpenSearchServiceImpl implements OpenSearchService {
    private final ApplicationProperties applicationProperties;

    private OpenSearchClient openSearchClient;

    public OpenSearchServiceImpl(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
        this.open();
    }

    @Override
    public CreateIndexResponse createIndex(String indexName, TypeMapping.Builder mappingBuilder) throws IOException {
        CreateIndexResponse response;
        BooleanResponse booleanResponse = openSearchClient.indices().exists(builder -> builder.index(indexName));

        if (!booleanResponse.value()) {
            response = openSearchClient.indices().create(builder -> builder.index(indexName).mappings(mappingBuilder.build()));
        } else {
            response = new CreateIndexResponse.Builder()
                .index(indexName)
                .acknowledged(true)
                .shardsAcknowledged(true)
                .build();
        }

        return response;
    }

    @Override
    public <TDocument> IndexResponse index(IndexRequest<TDocument> indexRequest) throws IOException {
        return openSearchClient.index(indexRequest);
    }

    @Override
    public <TDocument> GetResponse<TDocument> get(Function<GetRequest.Builder, ObjectBuilder<GetRequest>> fn, Class<TDocument> documentClass) throws IOException {
        return openSearchClient.get(fn.apply(new GetRequest.Builder()).build(), documentClass);
    }

    @Override
    public <TDocument> SearchResponse<TDocument> search(Function<SearchRequest.Builder, ObjectBuilder<SearchRequest>> fn, Class<TDocument> documentClass) throws IOException {
        return openSearchClient.search(fn.apply(new SearchRequest.Builder()).build(), documentClass);
    }

    private void open() {
        // Initialize openSearch client
        HttpHost host = new HttpHost(applicationProperties.getOpenSearch().getHost(), applicationProperties.getOpenSearch().getPort(), applicationProperties.getOpenSearch().getSchema());
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(host), new UsernamePasswordCredentials(applicationProperties.getOpenSearch().getUsername(), applicationProperties.getOpenSearch().getPassword()));

        // Initialize the client with SSL and TLS enabled
        RestClient restClient = RestClient.builder(host)
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)).build();

        OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        openSearchClient = new OpenSearchClient(transport);
    }
}
