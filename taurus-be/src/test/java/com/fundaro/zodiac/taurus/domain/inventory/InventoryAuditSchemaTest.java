package com.fundaro.zodiac.taurus.domain.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class InventoryAuditSchemaTest {

    private static final Set<String> REQUIRED_AUDIT_COLUMNS = Set.of(
        "deleted",
        "insert_date",
        "insert_by",
        "edit_date",
        "edit_by"
    );

    @Test
    void everyInventoryTableMustContainAllAuditColumns() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        try (InputStream input = getClass().getResourceAsStream("/config/liquibase/changelog/20260818090000_add_inventory.xml")) {
            assertThat(input).isNotNull();
            NodeList tables = factory.newDocumentBuilder().parse(input).getElementsByTagNameNS("*", "createTable");

            for (int tableIndex = 0; tableIndex < tables.getLength(); tableIndex++) {
                Element table = (Element) tables.item(tableIndex);
                String tableName = table.getAttribute("tableName");
                if (!tableName.startsWith("inventory_")) continue;

                Set<String> columnNames = new HashSet<>();
                NodeList columns = table.getElementsByTagNameNS("*", "column");
                for (int columnIndex = 0; columnIndex < columns.getLength(); columnIndex++) {
                    columnNames.add(((Element) columns.item(columnIndex)).getAttribute("name"));
                }

                assertThat(columnNames)
                    .as("audit columns of table %s", tableName)
                    .containsAll(REQUIRED_AUDIT_COLUMNS);
            }
        }
    }
}
