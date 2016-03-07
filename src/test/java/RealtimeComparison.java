import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Test;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.metamx.common.Granularity;
import com.metamx.tranquility.beam.ClusteredBeamTuning;
import com.metamx.tranquility.druid.DruidBeamConfig;
import com.metamx.tranquility.druid.DruidBeams;
import com.metamx.tranquility.druid.DruidDimensions;
import com.metamx.tranquility.druid.DruidLocation;
import com.metamx.tranquility.druid.DruidRollup;
import com.metamx.tranquility.tranquilizer.Tranquilizer;
import com.metamx.tranquility.typeclass.Timestamper;
import com.opencsv.CSVParser;
import com.twitter.util.FutureEventListener;

import io.druid.data.input.impl.TimestampSpec;
import io.druid.granularity.QueryGranularity;
import io.druid.indexer.HadoopIngestionSpec;
import io.druid.jackson.DefaultObjectMapper;
import io.druid.query.aggregation.AggregatorFactory;
import io.druid.segment.indexing.DataSchema;
import scala.runtime.BoxedUnit;

public class RealtimeComparison {
    public List<String> dimensions;
    public List<AggregatorFactory> aggregators = new ArrayList<AggregatorFactory>();
    public QueryGranularity queryGranularity;
    public static CSVParser CSV_PARSER = new CSVParser();

    public static final String DATA_FILE = "data/data.csv";
    public static final String SPEC_FILE = "realtime/realtime_spec.json";

    public static final DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")
            .withZoneUTC();

    @Test
    public void loadSmall() throws Exception {
        Tranquilizer<Map<String, Object>> tranquilizer = buildTranquilizer(SPEC_FILE);
        tranquilizer.start();
        try (BufferedReader br = new BufferedReader(new FileReader(DATA_FILE))) {
            for (String line; (line = br.readLine()) != null;) {
                String[] record = CSV_PARSER.parseLine(line);
                Map<String, Object> data = new HashMap<String, Object>();
                data.put("account_id", record[0]);
                data.put("stime", System.currentTimeMillis());
                data.put("os", record[2]);
                String[] products = record[3].split("\\|");
                if (products[0].length() != 0)
                    data.put("products", record[3].split("\\|"));
                else
                    System.out.println("Empty products dimension.");
                data.put("cost", record[4]);
                System.out.println("Sending ["+line+"]");
                tranquilizer.send(data).addEventListener(new FutureEventListener<BoxedUnit>() {
                    @Override
                    public void onSuccess(BoxedUnit value) {
                    }

                    @Override
                    public void onFailure(Throwable e) {
                        System.out.println("FAILED TO SEND");
                    }
                });
            }
        }
    }

    @SuppressWarnings("serial")
    private Tranquilizer<Map<String, Object>> buildTranquilizer(String specLocation) throws Exception {
        DefaultObjectMapper mapper = new DefaultObjectMapper();
        mapper.setInjectableValues(new InjectableValues.Std().addValue(ObjectMapper.class, mapper));
        mapper.registerSubtypes(HadoopIngestionSpec.class);
        mapper.registerSubtypes(DataSchema.class);
        HadoopIngestionSpec spec = mapper.readValue(new File(specLocation), HadoopIngestionSpec.class);

        aggregators = new ArrayList<AggregatorFactory>(Arrays.asList(spec.getDataSchema().getAggregators()));
        dimensions = spec.getDataSchema().getParser().getParseSpec().getDimensionsSpec().getDimensions();

        CuratorFramework curator = CuratorFrameworkFactory.builder().connectString("192.168.50.4")
                .retryPolicy(new ExponentialBackoffRetry(1000, 20, 30000))
                .build();
        curator.start();

        DruidBeamConfig druidBeamConfig = DruidBeamConfig.builder()
                .firehoseGracePeriod(new Period("PT1M"))
                .randomizeTaskId(false)
                .build();

        Tranquilizer.Builder tranquilizerBuilder = Tranquilizer.builder()
                .maxBatchSize(1)
                .maxPendingBatches(1);

        Timestamper<Map<String, Object>> timestamper = new Timestamper<Map<String, Object>>() {
            @Override
            public DateTime timestamp(Map<String, Object> theMap) {
                Object time = theMap.get("stime");
                DateTime dateTime = new DateTime(time);
                return dateTime;
            }
        };

        return DruidBeams.builder(timestamper)
                .curator(curator)
                .discoveryPath("/druid/discovery")
                .location(DruidLocation.create("overlord", "druid:firehose:%s", "rt4"))
                .timestampSpec(new TimestampSpec("stime", "auto", null))
                .druidBeamConfig(druidBeamConfig)
                .rollup(DruidRollup.create(DruidDimensions.specific(dimensions), aggregators,  
                        QueryGranularity.fromString(("MINUTE"))))
                .tuning(ClusteredBeamTuning.builder()
                        .segmentGranularity(Granularity.valueOf("MINUTE"))
                        .windowPeriod(new Period("PT1M"))
                        .partitions(1)
                        .replicants(1).build())
                .buildTranquilizer(tranquilizerBuilder);
    }
}
