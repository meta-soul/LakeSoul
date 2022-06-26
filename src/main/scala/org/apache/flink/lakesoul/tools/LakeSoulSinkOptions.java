package org.apache.flink.lakesoul.tools;

import org.apache.flink.configuration.ConfigOption;
import org.apache.flink.configuration.ConfigOptions;

import static org.apache.flink.configuration.ConfigOptions.key;

public class LakeSoulSinkOptions {

    public static final String RECORD_KEY_NAME = "recordKey";

    public static final String APPEND_COMMIT_TYPE = "AppendCommit";

    public static final String FILE_OPTION_ADD = "add";

    public static final String CDC_CHANGE_COLUMN = "lakesoul_cdc_change_column";

    public static final String FILE_EXIST_COLUMN_KEY = "fileExistColumn";

    public static final String FILE_IN_PROGRESS_PART_PREFIX = ".part";

    public static final Long DEFAULT_BUCKET_ROLLING_SIZE = 2000L;

    public static final Long DEFAULT_BUCKET_ROLLING_TIME = 2000000L;


    public static final ConfigOption<String> KEY_FIELD = ConfigOptions
            .key("recordKey")
            .stringType()
            .defaultValue("0")
            .withDescription("Record key ");

    public static final ConfigOption<String> PARTITION_FIELD = ConfigOptions
            .key("partitions")
            .stringType()
            .defaultValue("null")
            .withDescription("partitionKey ");

    public static final ConfigOption<String> TABLE_NAME = ConfigOptions
            .key("table_name")
            .stringType()
            .defaultValue("")
            .withDescription("table name ");

    public static final ConfigOption<String> FILE_EXIST_COLUMN = ConfigOptions
            .key(FILE_EXIST_COLUMN_KEY)
            .stringType()
            .defaultValue("null")
            .withDescription("file exist column  ");

    public static final ConfigOption<String> CATALOG_PATH = ConfigOptions.
            key("path")
            .stringType()
            .noDefaultValue()
            .withDescription("The path of a directory");


}




