/*
 * Copyright © 2022 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.plugin.gcp.dataplex.source;

import com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants;
import com.google.api.gax.core.BackgroundResource;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.bigtable.repackaged.org.apache.commons.lang3.builder.ToStringExclude;
import com.google.cloud.bigquery.*;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.dataplex.v1.*;
import io.cdap.cdap.etl.api.FailureCollector;
import io.cdap.cdap.etl.api.PipelineConfigurer;
import io.cdap.cdap.etl.api.StageConfigurer;
import io.cdap.cdap.etl.api.batch.BatchRuntimeContext;
import io.cdap.cdap.etl.api.batch.BatchSource;
import io.cdap.cdap.etl.api.batch.BatchSourceContext;
import io.cdap.cdap.etl.api.validation.FormatContext;
import io.cdap.cdap.etl.api.validation.ValidatingInputFormat;
import io.cdap.cdap.etl.api.validation.ValidationException;
import io.cdap.cdap.etl.mock.validation.MockFailureCollector;
import io.cdap.plugin.gcp.bigquery.source.BigQuerySourceUtils;
import io.cdap.plugin.gcp.bigquery.util.BigQueryUtil;
import io.cdap.plugin.gcp.common.GCPConnectorConfig;
import io.cdap.plugin.gcp.common.GCPUtils;
import io.cdap.plugin.gcp.dataplex.common.config.DataplexBaseConfig;
import io.cdap.plugin.gcp.dataplex.common.util.DataplexUtil;
import io.cdap.plugin.gcp.dataplex.sink.config.DataplexBatchSinkConfig;
import io.cdap.plugin.gcp.dataplex.source.config.DataplexBatchSourceConfig;
import net.jcip.annotations.ThreadSafe;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet;
import org.apache.tools.ant.Project;
import org.elasticsearch.ElasticsearchSecurityException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.validator.ValidateWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import scala.Enumeration;
import scala.tools.nsc.doc.model.ModelFactory$HigherKindedImpl$class;
import scala.tools.nsc.interpreter.Power;
import shapeless.ops.nat;

import javax.validation.Valid;
import javax.xml.crypto.Data;
import java.io.IOException;
import java.io.InvalidClassException;
import java.nio.channels.Pipe;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.mock;

/**
 * Tests for DataplexBatchSource
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({DataplexUtil.class , BigQueryUtil.class , Entity.class})
public class DataplexBatchSourceTest {
  @Mock
  private GCPConnectorConfig connection;

  @Mock
  StatusCode statusCode;

  private DataplexBatchSourceConfig.Builder getBuilder() {
    return DataplexBatchSourceConfig.builder()
            .setReferenceName("test");
  }

  @Test
  public void tryGetProjectTest() {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    DataplexBatchSourceConfig config = spy(builder.build());
    when(config.tryGetProject()).thenReturn(null);
    assertNull(config.tryGetProject());
    verify(config, times(1)).tryGetProject();
  }

  @Test
  public void validateEntityConfigWhenLocationIsNull() throws Exception {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    GoogleCredentials googleCredentials = PowerMockito.mock(GoogleCredentials.class);
    DataplexBatchSourceConfig dataplexBatchSourceConfig = PowerMockito.spy(builder.build());
    doReturn(googleCredentials).when(dataplexBatchSourceConfig).getCredentials(mockFailureCollector);
    dataplexBatchSourceConfig.getAndValidateEntityConfiguration(mockFailureCollector, googleCredentials);
    assertEquals(0, mockFailureCollector.getValidationFailures().size());
  }

  @Test
  public void validateEntityConfigWhenLocationIsNotNull() throws Exception {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    GoogleCredentials googleCredentials = PowerMockito.mock(GoogleCredentials.class);
    DataplexBatchSourceConfig dataplexBatchSourceConfig = PowerMockito.spy(builder.setLocation("Location").build());
    doReturn(googleCredentials).when(dataplexBatchSourceConfig).getCredentials(mockFailureCollector);
    dataplexBatchSourceConfig.getAndValidateEntityConfiguration(mockFailureCollector, googleCredentials);
    assertEquals(0, mockFailureCollector.getValidationFailures().size());
  }


  @Test
  public void validateEntityConfigWhenLakeIsNull() throws IOException {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    GoogleCredentials credentials = mock(GoogleCredentials.class);
    DataplexBatchSourceConfig dataplexBatchSourceConfig =
            PowerMockito.spy(builder.setLake(null).setLocation("test").build());
    doReturn("project").when(dataplexBatchSourceConfig).tryGetProject();
    Entity entity = dataplexBatchSourceConfig.getAndValidateEntityConfiguration(mockFailureCollector, credentials);
    assertEquals(null, entity);
    assertEquals(0, mockFailureCollector.getValidationFailures().size());
  }

  @Test
  public void validateEntityConfigWhenLakeIsNotNull() throws Exception {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    DataplexServiceClient dataplexServiceClient = mock(DataplexServiceClient.class);
    GoogleCredentials googleCredentials = mock(GoogleCredentials.class);
    PowerMockito.mockStatic(DataplexUtil.class);
    DataplexBatchSourceConfig dataplexBatchSourceConfig = PowerMockito.spy(builder.setLake("lake").
            setLocation("location").build());
    when(DataplexUtil.getDataplexServiceClient(googleCredentials)).thenReturn(dataplexServiceClient);
    doReturn("project").when(dataplexBatchSourceConfig).tryGetProject();
    doReturn(googleCredentials).when(dataplexBatchSourceConfig).getCredentials(mockFailureCollector);
    when(dataplexServiceClient.getLake((LakeName) any())).thenReturn(Lake.newBuilder().build());
    dataplexBatchSourceConfig.getAndValidateEntityConfiguration(mockFailureCollector, googleCredentials);
    assertEquals(0, mockFailureCollector.getValidationFailures().size());
  }

  @Test
  public void validateEntityConfigWhenLakeIsNotNullThrows() throws Exception {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    DataplexServiceClient dataplexServiceClient = Mockito.mock(DataplexServiceClient.class);
    GoogleCredentials googleCredentials = PowerMockito.mock(GoogleCredentials.class);
    PowerMockito.mockStatic(DataplexUtil.class);
    ApiException apiException = new ApiException(new RuntimeException(), statusCode, false);
    when(statusCode.getCode()).thenReturn(StatusCode.Code.NOT_FOUND);
    DataplexBatchSourceConfig dataplexBatchSourceConfig =
            PowerMockito.spy(builder.setLake("lake").setLocation("location").build());
    doReturn("project").when(dataplexBatchSourceConfig).tryGetProject();
    doReturn(googleCredentials).when(dataplexBatchSourceConfig).getCredentials(mockFailureCollector);
    when(DataplexUtil.getDataplexServiceClient(googleCredentials)).thenReturn(dataplexServiceClient);
    when(dataplexServiceClient.getLake((LakeName) any())).thenThrow(apiException);
    dataplexBatchSourceConfig.getAndValidateEntityConfiguration(mockFailureCollector, googleCredentials);
    assertEquals(1, mockFailureCollector.getValidationFailures().size());
  }

  @Test
  public void validateEntityConfigWhenZoneIsNotNull() throws Exception {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    DataplexServiceClient dataplexServiceClient = mock(DataplexServiceClient.class);
    GoogleCredentials googleCredentials = mock(GoogleCredentials.class);
    PowerMockito.mockStatic(DataplexUtil.class);
    DataplexBatchSourceConfig dataplexBatchSourceConfig = PowerMockito.spy
            (builder.setZone("zone").setLake("lake").setLocation("location").build());
    when(DataplexUtil.getDataplexServiceClient(googleCredentials)).thenReturn(dataplexServiceClient);
    doReturn("project").when(dataplexBatchSourceConfig).tryGetProject();
    doReturn(googleCredentials).when(dataplexBatchSourceConfig).getCredentials(mockFailureCollector);
    when(dataplexServiceClient.getLake((LakeName) any())).thenReturn(Lake.newBuilder().build());
    when(dataplexServiceClient.getZone((ZoneName) Mockito.any())).thenReturn(Zone.newBuilder().build());
    dataplexBatchSourceConfig.getAndValidateEntityConfiguration(mockFailureCollector, googleCredentials);
    assertEquals(0, mockFailureCollector.getValidationFailures().size());
  }

  @Test
  public void validateEntityConfigWhenZoneIsNotNullAndThrowsException() throws Exception {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    DataplexServiceClient dataplexServiceClient = mock(DataplexServiceClient.class);
    GoogleCredentials googleCredentials = mock(GoogleCredentials.class);
    ApiException apiException = new ApiException(new RuntimeException(), statusCode, false);
    PowerMockito.mockStatic(DataplexUtil.class);
    when(statusCode.getCode()).thenReturn(StatusCode.Code.NOT_FOUND);
    DataplexBatchSourceConfig dataplexBatchSourceConfig =
            PowerMockito.spy(builder.setLake("example-lake").setLocation("test").setZone("example-zone").build());
    doReturn("project").when(dataplexBatchSourceConfig).tryGetProject();
    when(DataplexUtil.getDataplexServiceClient(googleCredentials)).thenReturn(dataplexServiceClient);
    doReturn(googleCredentials).when(dataplexBatchSourceConfig).getCredentials(mockFailureCollector);
    when(dataplexServiceClient.getLake((LakeName) any())).thenReturn(Lake.newBuilder().build());
    when(dataplexServiceClient.getZone((ZoneName) any())).thenThrow(apiException);
    dataplexBatchSourceConfig.getAndValidateEntityConfiguration(mockFailureCollector, googleCredentials);
    assertEquals(0, mockFailureCollector.getValidationFailures().size());
  }

  @Test
  public void validateEntityConfigWhenConfigEntityIsNotNull() throws Exception {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    GoogleCredentials googleCredentials = mock(GoogleCredentials.class);
    MetadataServiceClient metadataServiceClient = mock(MetadataServiceClient.class);
    DataplexServiceClient dataplexServiceClient = mock(DataplexServiceClient.class);
    PowerMockito.mockStatic(DataplexUtil.class);
    //Entity entity = mock(Entity.class);
    DataplexBatchSourceConfig dataplexBatchSourceConfig = PowerMockito.spy
            (builder.setZone("zone").setLake("lake").setLocation("location").build());
    when(DataplexUtil.getMetadataServiceClient(googleCredentials)).thenReturn(metadataServiceClient);
    doReturn("project").when(dataplexBatchSourceConfig).tryGetProject();
    doReturn(googleCredentials).when(dataplexBatchSourceConfig).getCredentials(mockFailureCollector);
    //when(DataplexUtil.getMetadataServiceClient(googleCredentials)).thenReturn(metadataServiceClient);
    when(dataplexServiceClient.getLake((LakeName) any())).thenReturn(Lake.newBuilder().build());
    when(dataplexServiceClient.getZone((ZoneName) Mockito.any())).thenReturn(Zone.newBuilder().build());
    Mockito.when(metadataServiceClient.getEntity((GetEntityRequest) Mockito.any()));
    dataplexBatchSourceConfig.getAndValidateEntityConfiguration(mockFailureCollector, googleCredentials);
    assertEquals(0, mockFailureCollector.getValidationFailures().size());
  }

  @Test
  public void validateEntityConfigWhenEntityIsNotNullThrow() throws Exception {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    MetadataServiceClient metadataServiceClient = Mockito.mock(MetadataServiceClient.class);
    DataplexServiceClient dataplexServiceClient = Mockito.mock(DataplexServiceClient.class);
    GoogleCredentials googleCredentials = PowerMockito.mock(GoogleCredentials.class);
    PowerMockito.mockStatic(DataplexUtil.class);
    ApiException apiException = new ApiException(new RuntimeException(), statusCode, false);
    when(statusCode.getCode()).thenReturn(StatusCode.Code.NOT_FOUND);
    DataplexBatchSourceConfig dataplexBatchSourceConfig =
            PowerMockito.spy(builder.setZone("zone").setLake("lake").setLocation("test").build());
    when(DataplexUtil.getMetadataServiceClient(googleCredentials)).thenReturn(metadataServiceClient);
    doReturn("project").when(dataplexBatchSourceConfig).tryGetProject();
    doReturn(googleCredentials).when(dataplexBatchSourceConfig).getCredentials(mockFailureCollector);
    when(dataplexServiceClient.getLake((LakeName) any())).thenReturn(Lake.newBuilder().build());
    when(dataplexServiceClient.getZone((ZoneName) any())).thenReturn(Zone.newBuilder().build());
    when(metadataServiceClient.getEntity((GetEntityRequest.newBuilder().
            setName(EntityName.format("project", "location", "lake", "zone", "entity")).
            setView(GetEntityRequest.EntityView.FULL).build()))).
            thenThrow(apiException);
    dataplexBatchSourceConfig.getAndValidateEntityConfiguration(mockFailureCollector, googleCredentials);
    assertEquals(1, mockFailureCollector.getValidationFailures().size());
  }

   /* DataplexBatchSourceConfig.Builder builder = getBuilder();
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    MetadataServiceClient metadataServiceClient = Mockito.mock(MetadataServiceClient.class);
    DataplexServiceClient dataplexServiceClient = Mockito.mock(DataplexServiceClient.class);
    Entity entity = Mockito.mock(Entity.class);
    GoogleCredentials credentials = mock(GoogleCredentials.class);
    PowerMockito.mockStatic(DataplexUtil.class);
    DataplexBatchSourceConfig dataplexBatchSourceConfig = PowerMockito.spy(builder.setZone(null).
            setLake("lake").setLocation("Location").build());
    doReturn("Project").when(dataplexBatchSourceConfig).tryGetProject();
    when(DataplexUtil.getMetadataServiceClient(credentials)).thenReturn(metadataServiceClient);
    doReturn(credentials).when(dataplexBatchSourceConfig).getCredentials(mockFailureCollector);
    when(dataplexServiceClient.getLake((LakeName) any())).thenReturn(Lake.newBuilder().build());
    when(dataplexServiceClient.getZone((ZoneName)Mockito.any())).thenReturn(Zone.newBuilder().build());
    when(metadataServiceClient.getEntity((EntityName) Mockito.any())).thenReturn(Entity.newBuilder().build());
    try{
      dataplexBatchSourceConfig.getAndValidateEntityConfiguration(mockFailureCollector , credentials);
    }catch(ValidationException e){
      assertEquals(1 , mockFailureCollector.getValidationFailures().size());
    }
  }
  */

  @Test
  public void validateServiceAccount() {
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    GoogleCredentials credentials = mock(GoogleCredentials.class);
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    DataplexBatchSourceConfig config = spy(builder.setConnection(connection).build());
    doReturn(null).when(config).getCredentials(mockFailureCollector);
    try {
      config.validateAndGetServiceAccountCredentials(mockFailureCollector);
    } catch (Exception e) {
    }
    assertEquals(1, mockFailureCollector.getValidationFailures().size());
  }

  @Test
  public void validateServiceAccountIsServiceAccountJsonTrue() {
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    DataplexBatchSourceConfig config = spy(builder.setConnection(connection).build());
    GoogleCredentials googleCredentials = mock(GoogleCredentials.class);
    doReturn(googleCredentials).when(config).getCredentials(mockFailureCollector);
    config.validateAndGetServiceAccountCredentials(mockFailureCollector);
    assertEquals(0, mockFailureCollector.getValidationFailures().size());
  }

  @Test
  public void validateServiceAccountWhenIsServiceAccountJsonTrueAndFilePathIsNotNull() {
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    DataplexBatchSourceConfig config = spy(builder.setConnection(connection).build());
    GoogleCredentials googleCredentials = mock(GoogleCredentials.class);
    doReturn(googleCredentials).when(config).getCredentials(mockFailureCollector);
    config.validateAndGetServiceAccountCredentials(mockFailureCollector);
    assertEquals(0, mockFailureCollector.getValidationFailures().size());
  }

  @Test
  public void testValidateBigQuerySourceTable() throws Exception {
    DataplexBatchSourceConfig dataplexBatchSourceConfig = PowerMockito.spy(getBuilder().build());
    GoogleCredentials credentials = mock(GoogleCredentials.class);
    MockFailureCollector mockFailureCollector = new MockFailureCollector("stage name");
    Table table = PowerMockito.mock(Table.class);
    PowerMockito.mockStatic(BigQueryUtil.class);
    when(BigQueryUtil.getBigQueryTable("project", "datasets", "tablename",
            "service", true, mockFailureCollector)).thenReturn(table);
    doReturn(credentials).when(dataplexBatchSourceConfig).getCredentials(mockFailureCollector);
    doReturn(credentials).when(dataplexBatchSourceConfig).getCredentials(mockFailureCollector);

    try {
      dataplexBatchSourceConfig.validateBigQueryDataset(mockFailureCollector, "project",
              "dataset", "table");
    } catch (ValidationException e) {
    }
    assertEquals(0, mockFailureCollector.getValidationFailures().size());
  }

  @Test
  public void testValidateBigQuery() throws Exception {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    DataplexBatchSourceConfig dataplexBatchSourceConfig = builder.build();
    MockFailureCollector mockFailureCollector = new MockFailureCollector("stage name");
    PowerMockito.mockStatic(BigQueryUtil.class);
    Table table = mock(Table.class);

    /* dataplexBatchSourceConfig = builder.setpartitionFrom("set").build();
    dataplexBatchSourceConfig.validateBigQueryDataset(mockFailureCollector, "project", "dataset",
            "tablewrong");
    assertEquals(1, mockFailureCollector.getValidationFailures().size());*/
  }

  @Test
  public void testValidateBigQueryTO() throws Exception {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    DataplexBatchSourceConfig dataplexBatchSourceConfig = builder.build();
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    dataplexBatchSourceConfig = builder.setPartitionTo("SET").build();
    dataplexBatchSourceConfig.validateBigQueryDataset(mockFailureCollector, "project", "data-set", "table-wrong");
    assertEquals(1, mockFailureCollector.getValidationFailures().size());
  }

  @Test
  public void testValidateBigQueryPartitionFromIsNull() {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    DataplexBatchSourceConfig dataplexBatchSourceConfig = builder.build();
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    dataplexBatchSourceConfig = builder.setpartitionFrom(null).build();
    try {
      dataplexBatchSourceConfig.validateBigQueryDataset(mockFailureCollector, "project", "data-set", "table");
    } catch (Exception e) {
    }
    assertEquals(0, mockFailureCollector.getValidationFailures().size());
  }

  @Test
  public void testValidateBigQueryPartitionToIsNull() {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    DataplexBatchSourceConfig dataplexBatchSourceConfig = builder.build();
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    dataplexBatchSourceConfig = builder.setPartitionTo(null).build();
    try {
      dataplexBatchSourceConfig.validateBigQueryDataset(mockFailureCollector, "project", "data-set", "table");
    } catch (Exception e) {
    }
    assertEquals(0, mockFailureCollector.getValidationFailures().size());
  }

  /*@Test
  public void testGetSourceTableType() throws Exception {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    DataplexBatchSourceConfig dataplexBatchSourceConfig = PowerMockito.spy(builder.build());
    dataplexBatchSourceConfig.getSourceTableType("project", "dataset", "table");
    Table table = Mockito.mock(Table.class);
    //BigQuery bigQuery = mock(BigQuery.class);
    PowerMockito.mockStatic(BigQueryUtil.class);
    when(BigQueryUtil.getBigQueryTable("project", "dataset", "table", "service",
            true)).thenReturn(table);
    //DataplexBatchSourceConfig.Builder builder = getBuilder();
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    GoogleCredentials credentials = mock(GoogleCredentials.class);
    Table table = Mockito.mock(Table.class);
    PowerMockito.mockStatic(BigQueryUtil.class);
    when(BigQueryUtil.getBigQueryTable("project", "dataset", "table-name",
            "service-account", false)).thenReturn(table);
    doReturn(credentials).when(dataplexBatchSourceConfig).getCredentials(mockFailureCollector);
    //when(dataplexBatchSourceConfig.isServiceAccountFilePath()).thenReturn(true);
    when(dataplexBatchSourceConfig.getServiceAccount()).thenReturn("ServiceAccount");
    dataplexBatchSourceConfig.getSourceTableType("PROJECT", "DATASETS", "TABLE-NAME");
    //doReturn(table.getDefinition().getType()).when(table.equals("jhg"));
    // when(GCPUtils.getBigQuery("project" , credentials)).thenReturn(bigQuery);
    assertEquals(0, mockFailureCollector.getValidationFailures().size());
  }
} */

  @Test
  public void testSetUpValidatingInputFormat() {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    //PluginProperties.Builder builder1 = PluginProperties.builder();
    MockFailureCollector mockFailureCollector = Mockito.mock(MockFailureCollector.class);
    PipelineConfigurer pipelineConfigurer = mock(PipelineConfigurer.class);
    Entity entity = Mockito.mock(Entity.class);
    PowerMockito.mockStatic(DataplexUtil.class);
    String fileFormat = "format";
    //PowerMockito.mockStatic(StageConfigurer.class);
    DataplexBatchSourceConfig dataplexBatchSourceConfig = PowerMockito.spy(builder.setSchema(null).build());
    io.cdap.cdap.api.data.schema.Schema schema = io.cdap.cdap.api.data.schema.Schema.recordOf("record",
            io.cdap.cdap.api.data.schema.Schema.Field.of("id", io.cdap.cdap.api.data.schema.Schema.of
                    (io.cdap.cdap.api.data.schema.Schema.Type.LONG)));
    when(DataplexUtil.getTableSchema(entity.getSchema(), mockFailureCollector)).thenReturn(schema);
    dataplexBatchSourceConfig.setupValidatingInputFormat(pipelineConfigurer, mockFailureCollector, entity);
    assertEquals(0, mockFailureCollector.getValidationFailures().size());
  }

  @Test
  public void testGetValidatingInputFormat() throws Exception {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    DataplexBatchSourceConfig dataplexBatchSourceConfig = PowerMockito.spy(builder.build());
    BatchSourceContext batchSourceContext = Mockito.mock(BatchSourceContext.class);
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    ValidatingInputFormat validatingInputFormat = Mockito.mock(ValidatingInputFormat.class);
    String fileFormat = "format";
    when(batchSourceContext.getFailureCollector()).thenReturn(mockFailureCollector);
    when(batchSourceContext.newPluginInstance(fileFormat)).thenReturn(validatingInputFormat);
    try {
      dataplexBatchSourceConfig.getValidatingInputFormat(batchSourceContext);
    } catch (ValidationException e) {
      assertEquals(1, mockFailureCollector.getValidationFailures().size());
    }
  }

 /* @Test
  public void testGetValidatingInputFormatIterator() throws Exception{
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    DataplexBatchSourceConfig dataplexBatchSourceConfig = builder.build();
    BatchSourceContext batchSourceContext = Mockito.mock(BatchSourceContext.class);
    MockFailureCollector mockFailureCollector = Mockito.mock(MockFailureCollector.class);
    Iterator iterator = mock(Iterator.class);
    //InvalidClassException exception = new InvalidClassException("ERROR");
    String fileFormat = "format";
    PowerMockito.mockStatic(Iterator.class);
    Set<String> properties = new HashSet<>();
    String errorMessage = String.format("Format '%s' cannot be used because properties %s were not provided" +
                    " or were invalid when the pipeline was " + "deployed. Set the format to a different value," +
                    " or re-create the pipeline with all required properties.",
                      fileFormat, properties);
    InvalidPluginProperty invalidPluginProperty = new InvalidPluginProperty("Name" , errorMessage);
    dataplexBatchSourceConfig.getValidatingInputFormat(batchSourceContext);
    assertEquals(0 , mockFailureCollector.getValidationFailures().size());
*/

  @Test
  public void testCheckMetaStoreForGCSLakeIsNull() throws Exception {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    GoogleCredentials credentials = mock(GoogleCredentials.class);
    DataplexBatchSourceConfig dataplexBatchSourceConfig =
            PowerMockito.spy(builder.setLake(null).build());
    doReturn("project").when(dataplexBatchSourceConfig).tryGetProject();
    dataplexBatchSourceConfig.checkMetastoreForGCSEntity(mockFailureCollector, credentials);
    assertEquals(0, mockFailureCollector.getValidationFailures().size());
  }

  @Test
  public void testCheckMetaStoreForGCSLakeIsNotNull() throws Exception {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    DataplexServiceClient dataplexServiceClient = mock(DataplexServiceClient.class);
    GoogleCredentials googleCredentials = mock(GoogleCredentials.class);
    //Lake lake = mock(Lake.class);
    PowerMockito.mockStatic(DataplexUtil.class);
    DataplexBatchSourceConfig dataplexBatchSourceConfig = PowerMockito.spy(builder.
            setLake("lake").setLocation("location").build());
    when(DataplexUtil.getDataplexServiceClient(googleCredentials)).thenReturn(dataplexServiceClient);
    doReturn("project").when(dataplexBatchSourceConfig).tryGetProject();
    doReturn(googleCredentials).when(dataplexBatchSourceConfig).getCredentials(mockFailureCollector);
    when(dataplexServiceClient.getLake((LakeName) Mockito.any())).thenReturn(Lake.newBuilder().build());
    //when(dataplexServiceClient.getLake((LakeName) any())).thenReturn(Lake.newBuilder().build());
    dataplexBatchSourceConfig.checkMetastoreForGCSEntity(mockFailureCollector, googleCredentials);
    assertEquals(0, mockFailureCollector.getValidationFailures().size());
  }

  @Test
  public void testCheckMetaStoreForLakeBean() throws Exception {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    DataplexServiceClient dataplexServiceClient = mock(DataplexServiceClient.class);
    GoogleCredentials googleCredentials = mock(GoogleCredentials.class);
    PowerMockito.mockStatic(DataplexUtil.class);
    Lake lake = mock(Lake.class);
    DataplexBatchSourceConfig dataplexBatchSourceConfig;
    dataplexBatchSourceConfig = PowerMockito.spy
            (builder.setLake(null).setLocation("Location").build());
    doReturn(googleCredentials).when(dataplexBatchSourceConfig).getCredentials(mockFailureCollector);
    when(DataplexUtil.getDataplexServiceClient(googleCredentials)).thenReturn(dataplexServiceClient);
    doReturn("project").when(dataplexBatchSourceConfig).tryGetProject();
    //when(lake.getMetastore()).thenReturn(lake);
    dataplexBatchSourceConfig.checkMetastoreForGCSEntity(mockFailureCollector, googleCredentials);
    assertEquals(0, mockFailureCollector.getValidationFailures().size());
  }

  @Test
  public void testInitialize() throws Exception {
    DataplexBatchSourceConfig dataplexBatchSourceConfig = mock(DataplexBatchSourceConfig.class);
    DataplexBatchSource dataplexBatchSource = new DataplexBatchSource(DataplexBatchSourceConfig.builder().build());
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    // GoogleCredentials googleCredentials = mock(GoogleCredentials.class);
    BatchRuntimeContext batchRuntimeContext = mock(BatchRuntimeContext.class);
    Schema schema = Mockito.mock(Schema.class);
    MetadataServiceClient metadataServiceClient = mock(MetadataServiceClient.class);
    PowerMockito.mockStatic(DataplexUtil.class);
    //doReturn(googleCredentials).when(dataplexBatchSourceConfig).getCredentials(mockFailureCollector);
    doReturn("project").when(dataplexBatchSourceConfig).tryGetProject();
    when(DataplexUtil.getMetadataServiceClient(dataplexBatchSourceConfig.getCredentials
            (batchRuntimeContext.getFailureCollector()))).thenReturn(metadataServiceClient);
    dataplexBatchSource.initialize(batchRuntimeContext);
    assertEquals(0, mockFailureCollector.getValidationFailures().size());
  }

  @Test
  public void testInitializeEntity() throws Exception{
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    DataplexBatchSourceConfig dataplexBatchSourceConfig = builder.build();
    DataplexBatchSource dataplexBatchSource = new DataplexBatchSource(dataplexBatchSourceConfig);
    BatchRuntimeContext batchRuntimeContext = mock(BatchRuntimeContext.class);
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    PowerMockito.mockStatic(Entity.class);
    Entity entity = PowerMockito.mock(Entity.class);
    MetadataServiceClient metadataServiceClient = mock(MetadataServiceClient.class);
//    doReturn("project").when(dataplexBatchSourceConfig).tryGetProject();
      Mockito.when(metadataServiceClient.getEntity(EntityName.newBuilder().setProject("project").setLocation("location").
              setLake("lake").setZone("zone").setEntity("entity").build())).thenReturn(entity);
      try {
        dataplexBatchSource.initialize(batchRuntimeContext);

    }catch(ValidationException e) {
      assertEquals(1, mockFailureCollector.getValidationFailures().size());
    }
  }

  /*
    @Test
    public void testEntityInitialize() throws Exception{
      DataplexBatchSourceConfig dataplexBatchSourceConfig = mock(DataplexBatchSourceConfig.class);
      DataplexBatchSource dataplexBatchSource = new DataplexBatchSource(DataplexBatchSourceConfig.builder().build());
      BatchRuntimeContext batchRuntimeContext = mock(BatchRuntimeContext.class);
      MockFailureCollector mockFailureCollector = new MockFailureCollector();
      Schema schema = PowerMockito.mock(Schema.class);
      Entity entity = PowerMockito.mock(Entity.class);
      MetadataServiceClient metadataServiceClient = mock(MetadataServiceClient.class);
      //when(dataplexBatchSourceConfig.getSchema(batchRuntimeContext.getFailureCollector())).thenReturn(schema);
      when(metadataServiceClient.getEntity
              (EntityName.newBuilder().setProject(dataplexBatchSourceConfig.tryGetProject()).setLocation
                      (dataplexBatchSourceConfig.tryGetProject()).setLake(dataplexBatchSourceConfig.tryGetProject()).
                      setZone(dataplexBatchSourceConfig.tryGetProject()).setEntity(dataplexBatchSourceConfig.
                              tryGetProject()).build())).thenReturn(entity);
      dataplexBatchSource.initialize(batchRuntimeContext);
      assertEquals(1 , mockFailureCollector.getValidationFailures().size());
    }
  */
  @Test
  public void testConfigurePipeline() throws Exception {
    DataplexBatchSourceConfig dataplexBatchSourceConfig = mock(DataplexBatchSourceConfig.class);
    DataplexBatchSource dataplexBatchSource = new DataplexBatchSource(DataplexBatchSourceConfig.builder().build());
    PipelineConfigurer pipelineConfigurer = PowerMockito.mock(PipelineConfigurer.class);
    StageConfigurer stageConfigurer = PowerMockito.mock(StageConfigurer.class);
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    GoogleCredentials googleCredentials = mock(GoogleCredentials.class);
    //doReturn("project").when(dataplexBatchSourceConfig.tryGetProject().equals(null));
    when(pipelineConfigurer.getStageConfigurer()).thenReturn(stageConfigurer);
    when(stageConfigurer.getFailureCollector()).thenReturn(mockFailureCollector);
    when(dataplexBatchSourceConfig.validateAndGetServiceAccountCredentials(mockFailureCollector)).
            thenReturn(googleCredentials);
    dataplexBatchSource.configurePipeline(pipelineConfigurer);
    assertEquals(1, mockFailureCollector.getValidationFailures().size());
  }
  /*
  @Test
  public void testOnRunFinish() throws Exception{
    DataplexBatchSourceConfig dataplexBatchSourceConfig = mock(DataplexBatchSourceConfig.class);
    DataplexBatchSource dataplexBatchSource = new DataplexBatchSource(DataplexBatchSourceConfig.builder().build());
    MockFailureCollector mockFailureCollector = new MockFailureCollector();
    Credentials credentials = mock(Credentials.class);
    BatchSourceContext context = mock(BatchSourceContext.class);
    BigQuerySourceUtils bigQuerySourceUtils = new BigQuerySourceUtils();
    Configuration configuration = new Configuration();
    String bucketPath = "bucketPath";
    String temporaryTable = "CONFIG_TEMPORARY_TABLE_NAME";
    when(dataplexBatchSourceConfig.getCredentials(context.getFailureCollector())).thenCallRealMethod();
    //when(GCPUtils.getBigQuery(dataplexBatchSourceConfig.getProject() , credentials)).thenReturn(bigQuery);
    //when(GCPUtils.getStorage(dataplexBatchSourceConfig.tryGetProject() ,
      //      dataplexBatchSourceConfig.getCredentials(context.getFailureCollector()))).thenReturn(storage);
    dataplexBatchSource.onRunFinish(false , context);
    assertEquals(1 , mockFailureCollector.getValidationFailures().size());
   }*/

  @Test
  public void testPrepareRun() throws Exception {
    DataplexBatchSourceConfig.Builder builder = getBuilder();
    DataplexBatchSourceConfig dataplexBatchSourceConfig = builder.build();
    DataplexBatchSource dataplexBatchSource = new DataplexBatchSource(dataplexBatchSourceConfig);
    DataplexBaseConfig dataplexBaseConfig = new DataplexBaseConfig();
    BatchSourceContext context = mock(BatchSourceContext.class);
    PowerMockito.mock(FailureCollector.class);
    FailureCollector failureCollector = mock(FailureCollector.class);
    GoogleCredentials credentials = mock(GoogleCredentials.class);
    PowerMockito.mock(Entity.class);
    Entity entity = mock(Entity.class);
    when(context.getFailureCollector()).thenReturn(failureCollector);
    //when(dataplexBatchSourceConfig.validateAndGetServiceAccountCredentials(failureCollector)).thenReturn(credentials);
    //when(mock(dataplexBatchSourceConfig.getClass()).getAndValidateEntityConfiguration(failureCollector , credentials)).thenReturn(entity);
    try {
      dataplexBatchSource.prepareRun(context);
    } catch (ValidationException e) {
    }
    assertEquals(0, failureCollector.getValidationFailures().size());
  }
}
