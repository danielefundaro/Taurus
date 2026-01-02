package com.fundaro.zodiac.taurus.config.changelog.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fundaro.zodiac.taurus.config.changelog.bean.ChangelogFile;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public interface ChangelogService {
    void createChangeLogIndex() throws IOException;

    void createIndex(ChangelogFile mapIndex, String filename) throws IOException, NoSuchAlgorithmException;

    void loadData(ChangelogFile loadData, String filename) throws IOException, NoSuchAlgorithmException;

    <T> T getMaps(String resourceName, TypeReference<T> typeReference) throws IOException;
}
