package io.cdap.plugin.gcp.dataplex.source.config;

import io.cdap.plugin.gcp.bigquery.source.PartitionedBigQueryInputFormat;
import io.cdap.plugin.gcp.dataplex.common.util.DataplexConstants;
import io.cdap.plugin.gcp.dataplex.common.util.DataplexUtil;
import io.cdap.plugin.gcp.dataplex.source.DataplexInputFormatProvider;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.api.mockito.PowerMockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DataplexInputFormatProviderTest {

    @Mock
    private JobContext mockContext;

    @Mock
    private Configuration configuration;

    @Mock
    private InputFormat inputFormat;

    @Mock
    private InputSplit inputSplit;

    @Mock
    private TaskAttemptContext context;


    @Test
    public void testGetInputFormatClassName() throws Exception {
        Configuration configuration = mock(Configuration.class);
        Map<String, String> inputFormatConfiguration = new HashMap<>();
        Collectors collectors = mock(Collectors.class);
        PowerMockito.mockStatic(StreamSupport.class);
        //PowerMockito.mockStatic(Collectors.class);
        // PartitionedBigQueryInputFormat partitionedBigQueryInputFormat = Mockito.mock
        // (PartitionedBigQueryInputFormat.class);
        String entityType = "entity";
        when(configuration.get(DataplexConstants.DATAPLEX_ENTITY_TYPE)).thenReturn(entityType);
        PartitionedBigQueryInputFormat partitionedBigQueryInputFormat = new PartitionedBigQueryInputFormat();
        PartitionedBigQueryInputFormat.class.getName();
        //when(StreamSupport.stream(configuration.spliterator() ,  false).collect(collectors.toMap(
             //   Map.Entry::getKey , Map.Entry::getValue))).thenReturn(inputFormatConfiguration);
        Mockito.mock(DataplexInputFormatProvider.DataplexInputFormat.class);
        DataplexInputFormatProvider dataplexInputFormatProvider = new DataplexInputFormatProvider(configuration);
        dataplexInputFormatProvider.getInputFormatClassName();

    }

    @Test
    public void testRecordReader() throws IOException, InterruptedException {
        PowerMockito.mock(DataplexUtil.class);
        List<InputFormat> split = new ArrayList<>();
        //when(inputFormat.getSplits(mockContext)).thenReturn(split);
        DataplexInputFormatProvider.DataplexInputFormat dataInputFormat = new
                DataplexInputFormatProvider.DataplexInputFormat(inputFormat);
        dataInputFormat.getSplits(mockContext);
    }

    @Test
    public void testCreateRecordReader() throws IOException, InterruptedException {
        DataplexInputFormatProvider.DataplexInputFormat dataInputFormat = new
                DataplexInputFormatProvider.DataplexInputFormat(inputFormat);
        dataInputFormat.createRecordReader(inputSplit, context);
    }

    @Test
    public void testgetInputFormatConfiguration() throws Exception {
        Configuration configuration = mock(Configuration.class);

    }

}
