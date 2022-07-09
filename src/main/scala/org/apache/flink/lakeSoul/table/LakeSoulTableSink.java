/*
 *
 * Copyright [2022] [DMetaSoul Team]
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package org.apache.flink.lakeSoul.table;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.flink.lakeSoul.metaData.DataFileMetaData;
import org.apache.flink.lakeSoul.sink.LakeSoulFileSink;
import org.apache.flink.lakeSoul.sink.LakeSoulSink;
import org.apache.flink.lakeSoul.sink.fileSystem.FlinkBucketAssigner;
import org.apache.flink.lakeSoul.sink.fileSystem.LakeSoulBucketsBuilder;
import org.apache.flink.lakeSoul.sink.fileSystem.LakeSoulRollingPolicyImpl;
import org.apache.flink.lakeSoul.sink.fileSystem.bulkFormat.ProjectionBulkFactory;
import org.apache.flink.lakeSoul.sink.partition.BucketPartitioner;
import org.apache.flink.lakeSoul.sink.partition.LakeSoulCdcPartitionComputer;
import org.apache.flink.lakeSoul.tools.FlinkUtil;
import org.apache.flink.table.connector.ChangelogMode;
import org.apache.flink.table.connector.format.EncodingFormat;
import org.apache.flink.table.connector.sink.DataStreamSinkProvider;
import org.apache.flink.table.connector.sink.DynamicTableSink;
import org.apache.flink.table.connector.sink.abilities.SupportsOverwrite;
import org.apache.flink.table.connector.sink.abilities.SupportsPartitioning;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.DataType;
import org.apache.flink.lakeSoul.tools.LakeSoulKeyGen;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.api.common.serialization.BulkWriter;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.ReadableConfig;
import org.apache.flink.core.fs.Path;
import org.apache.flink.table.catalog.ResolvedSchema;
import org.apache.flink.table.api.TableException;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.types.RowKind;

import static org.apache.flink.lakeSoul.tools.LakeSoulSinkOptions.BUCKET_CHECK_INTERVAL;
import static org.apache.flink.lakeSoul.tools.LakeSoulSinkOptions.CATALOG_PATH;
import static org.apache.flink.lakeSoul.tools.LakeSoulKeyGen.DEFAULT_PARTITION_PATH;
import static org.apache.flink.lakeSoul.tools.LakeSoulSinkOptions.FILE_ROLLING_SIZE;
import static org.apache.flink.lakeSoul.tools.LakeSoulSinkOptions.FILE_ROLLING_TIME;
import static org.apache.flink.lakeSoul.tools.LakeSoulSinkOptions.USE_CDC_COLUMN;

public class LakeSoulTableSink implements DynamicTableSink, SupportsPartitioning, SupportsOverwrite {
  private EncodingFormat<BulkWriter.Factory<RowData>> bulkWriterFormat;
  private EncodingFormat<SerializationSchema<RowData>> serializationFormat;
  private boolean overwrite;
  private Path path;
  private DataType dataType;
  private ResolvedSchema schema;
  private Configuration flinkConf;
  private LakeSoulKeyGen keyGen;
  private List<String> partitionKeyList;

  private static LakeSoulTableSink createLakesoulTableSink(LakeSoulTableSink lts) {
    return new LakeSoulTableSink(lts);
  }

  public LakeSoulTableSink(
      DataType dataType,
      List<String> partitionKeyList,
      ReadableConfig flinkConf,
      EncodingFormat<BulkWriter.Factory<RowData>> bulkWriterFormat,
      EncodingFormat<SerializationSchema<RowData>> serializationFormat,
      ResolvedSchema schema
  ) {
    this.serializationFormat = serializationFormat;
    this.bulkWriterFormat = bulkWriterFormat;
    this.schema = schema;
    this.partitionKeyList = partitionKeyList;
    this.flinkConf = (Configuration) flinkConf;
    this.dataType = dataType;
  }

  private LakeSoulTableSink(LakeSoulTableSink tableSink) {
    this.overwrite = tableSink.overwrite;
  }

  @Override
  public SinkRuntimeProvider getSinkRuntimeProvider(Context context) {
    return (DataStreamSinkProvider) (dataStream) -> createStreamingSink(dataStream, context);
  }

  @Override
  public ChangelogMode getChangelogMode(ChangelogMode changelogMode) {
    ChangelogMode.Builder builder = ChangelogMode.newBuilder();
    for (RowKind kind : changelogMode.getContainedKinds()) {
      builder.addContainedKind(kind);
    }
    return builder.build();
  }

  private DataStreamSink<?> createStreamingSink(DataStream<RowData> dataStream,
                                                Context sinkContext) {
    this.path = new Path(flinkConf.getString(CATALOG_PATH));
    this.keyGen = new LakeSoulKeyGen((RowType) schema.toSourceRowDataType().notNull().getLogicalType(), flinkConf, partitionKeyList);
    OutputFileConfig fileNameConfig = OutputFileConfig.builder().build();

    LakeSoulCdcPartitionComputer partitionComputer = partitionCdcComputer(flinkConf.getBoolean(USE_CDC_COLUMN));
    FlinkBucketAssigner assigner = new FlinkBucketAssigner(partitionComputer);

    LakeSoulRollingPolicyImpl lakeSoulPolicy = new LakeSoulRollingPolicyImpl(
        flinkConf.getLong(FILE_ROLLING_SIZE), flinkConf.getLong(FILE_ROLLING_TIME), keyGen);
    dataStream = dataStream.partitionCustom(new BucketPartitioner<>(), keyGen::getBucketPartitionKey);
    Object writer = createWriter(sinkContext);

    DataStream<DataFileMetaData> writerStream = LakeSoulSink.writer(
        flinkConf.getLong(BUCKET_CHECK_INTERVAL),
        dataStream,
        bucketsBuilderFactory(writer, partitionComputer, assigner, fileNameConfig, lakeSoulPolicy),
        fileNameConfig,
        partitionKeyList,
        flinkConf);

    return LakeSoulSink.sink(writerStream, this.path, partitionKeyList, flinkConf);
  }

  private LakeSoulBucketsBuilder<RowData, String, ? extends LakeSoulBucketsBuilder<RowData, ?, ?>> bucketsBuilderFactory(
      Object writer, LakeSoulCdcPartitionComputer partitionComputer, FlinkBucketAssigner bucketAssigner,
      OutputFileConfig fileNameConfig, LakeSoulRollingPolicyImpl lakeSoulPolicy
  ) {
    //      noinspection unchecked
    return
        LakeSoulFileSink.forBulkFormat(
                this.path,
                new ProjectionBulkFactory((BulkWriter.Factory<RowData>) writer, partitionComputer))
            .withBucketAssigner(bucketAssigner)
            .withOutputFileConfig(fileNameConfig)
            .withRollingPolicy(lakeSoulPolicy);
  }

  private Object createWriter(Context sinkContext) {
    DataType partitionField = FlinkUtil.getFields(dataType, false).stream()
        .filter(field -> !partitionKeyList.contains(field.getName()))
        .collect(Collectors.collectingAndThen(Collectors.toList(), LakeSoulTableSink::getDataType));
    if (bulkWriterFormat != null) {
      return bulkWriterFormat.createRuntimeEncoder(
          sinkContext, partitionField);
    } else if (serializationFormat != null) {
      return new LakeSoulSchemaAdapter(
          serializationFormat.createRuntimeEncoder(
              sinkContext, partitionField));
    } else {
      throw new TableException("Can not find format factory.");
    }
  }

  private LakeSoulCdcPartitionComputer partitionCdcComputer(boolean useCdc) {
    return new LakeSoulCdcPartitionComputer(
        DEFAULT_PARTITION_PATH,
        FlinkUtil.getFieldNames(dataType).toArray(new String[0]),
        FlinkUtil.getFieldDataTypes(dataType).toArray(new DataType[0]),
        partitionKeyList.toArray(new String[0]), useCdc);
  }

  public static DataType getDataType(List<DataTypes.Field> fields) {
    return DataTypes.ROW(fields.toArray(new DataTypes.Field[0]));
  }

  @Override
  public DynamicTableSink copy() {
    return createLakesoulTableSink(this);
  }

  @Override
  public String asSummaryString() {
    return "lakeSoul table sink";
  }

  @Override
  public void applyOverwrite(boolean newOverwrite) {
    this.overwrite = newOverwrite;
  }

  @Override
  public void applyStaticPartition(Map<String, String> map) {
  }

  @Override
  public boolean requiresPartitionGrouping(boolean supportsGrouping) {
    return false;
  }

}