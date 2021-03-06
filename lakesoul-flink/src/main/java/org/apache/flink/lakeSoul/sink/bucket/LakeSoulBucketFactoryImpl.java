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

package org.apache.flink.lakeSoul.sink.bucket;

import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.functions.sink.filesystem.BucketWriter;
import org.apache.flink.streaming.api.functions.sink.filesystem.FileLifeCycleListener;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;

import javax.annotation.Nullable;

import java.io.IOException;

public class LakeSoulBucketFactoryImpl<IN, BucketID> implements LakeSoulBucketFactory<IN, BucketID> {
  private static final long serialVersionUID = 1L;

  public LakeSoulBucketFactoryImpl() {
  }

  @Override
  public LakeSoulBucket<IN, BucketID> getNewBucket(int subtaskIndex, BucketID bucketId, Path bucketPath, long initialPartCounter, BucketWriter<IN, BucketID> bucketWriter,
                                                   LakeSoulRollingPolicyImpl rollingPolicy, @Nullable FileLifeCycleListener<BucketID> fileListener, OutputFileConfig outputFileConfig) {
    return LakeSoulBucket.getNew(subtaskIndex, bucketId, bucketPath, initialPartCounter, bucketWriter, rollingPolicy, fileListener, outputFileConfig);
  }

  @Override
  public LakeSoulBucket<IN, BucketID> restoreBucket(int subtaskIndex, long initialPartCounter, BucketWriter<IN, BucketID> bucketWriter, LakeSoulRollingPolicyImpl rollingPolicy,
                                                    BucketState<BucketID> bucketState, @Nullable FileLifeCycleListener<BucketID> fileListener, OutputFileConfig outputFileConfig) throws IOException {
    return LakeSoulBucket.restore(subtaskIndex, initialPartCounter, bucketWriter, rollingPolicy, bucketState, fileListener, outputFileConfig);
  }
}
