package com.fundaro.zodiac.taurus.config.changelog.service;

import com.fundaro.zodiac.taurus.config.changelog.bean.ChangelogFile;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public interface ChangelogService {
    void createChangeLogIndex() throws IOException;

    void extractAllResources(String resourceName, String tenantCode) throws IOException, NoSuchAlgorithmException;

    void createIndex(ChangelogFile mapIndex, String filename) throws IOException, NoSuchAlgorithmException;

    void updateIndex(ChangelogFile changelogFile, String filename) throws IOException, NoSuchAlgorithmException;

    void loadData(ChangelogFile loadData, String filename) throws IOException, NoSuchAlgorithmException;

    void deleteIndex(ChangelogFile changelogFile, String filename) throws IOException, NoSuchAlgorithmException;
}
