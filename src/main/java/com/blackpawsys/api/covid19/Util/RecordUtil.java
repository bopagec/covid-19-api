package com.blackpawsys.api.covid19.Util;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;

import com.blackpawsys.api.covid19.component.Summary;
import com.blackpawsys.api.covid19.dto.DailyReportDto;
import com.blackpawsys.api.covid19.model.Record;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RecordUtil {

  private static final String UTF8_BOM = "\uFEFF";
  private static final String FILE_TYPE = "csv";
  public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("MM-dd-yyyy");
  private static String resourcesUrl;
  private static List<String> overseasTerritories = new ArrayList<>();
  private static Map<String, String> countryAbbrMap = new HashMap<>();


  public static String createReportUrl(LocalDateTime currLocalDateTime) {
    StringBuilder sb = new StringBuilder();

    sb.append(resourcesUrl)
        .append(currLocalDateTime.toLocalDate().format(FORMATTER))
        .append(".")
        .append(FILE_TYPE);

    return sb.toString();
  }

  public static String formatLatLong(String val) {
    int i = StringUtils.indexOf(val, ".");

    if (i >= 1) {
      return StringUtils.substring(val, 0, i + 2);
    }

    return val;
  }

  public static List<Record> parseRecord(String recordStr, LocalDateTime lastUpdated) throws IOException {
    StringReader in = new StringReader(removeUTF8BOM(recordStr));
    List<Record> recordList = new ArrayList<>();

    if (!StringUtils.isEmpty(recordStr)) {

      Iterable<CSVRecord> records = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in);
      for (CSVRecord rec : records) {
        String state = (rec.isMapped("Province/State") ? rec.get("Province/State") : (rec.isMapped("Province_State") ? rec.get("Province_State") : null));
        String country = (rec.isMapped("Country/Region") ? rec.get("Country/Region") : (rec.isMapped("Country_Region") ? rec.get("Country_Region") : null));
        String combinedKey = (rec.isMapped("Combined_Key") ? rec.get("Combined_Key") : null);
        Long confirmed = (rec.isMapped("Confirmed") && !StringUtils.isEmpty(rec.get("Confirmed"))) ? Long.parseLong(rec.get("Confirmed")) : null;
        Long deaths = (rec.isMapped("Deaths") && !StringUtils.isEmpty(rec.get("Deaths"))) ? Long.parseLong(rec.get("Deaths")) : null;
        String lat = rec.isMapped("Lat") ? formatLatLong(rec.get("Lat")) : null;
        String longt = rec.isMapped("Long_") ? formatLatLong(rec.get("Long_")) : null;

        List<String> combinedKeyList = Collections.emptyList();
        if (countryAbbrMap.containsKey(state.toLowerCase())) {
          state = countryAbbrMap.get(state.toLowerCase());
        }
        if (countryAbbrMap.containsKey(country.toLowerCase())) {
          country = countryAbbrMap.get(country.toLowerCase());
        }

        if (StringUtils.isEmpty(state) && StringUtils.isEmpty(combinedKey)) {
          combinedKey = country;
        } else if (state.equalsIgnoreCase(country) && StringUtils.isEmpty(combinedKey)) {
          combinedKey = country;
        }
        if (!StringUtils.isEmpty(combinedKey)) {
          String[] keys = StringUtils.split(combinedKey, ",");
          combinedKeyList = Stream.of(keys).filter(key -> !StringUtils.isEmpty(key)).collect(Collectors.toList());
        }

        if (!StringUtils.isEmpty(state) && overseasTerritories.contains(country.toLowerCase())
                && !state.equalsIgnoreCase("wales") && !state.equalsIgnoreCase("Scotland") && !state.equalsIgnoreCase("england")) {
          country = state;
        }

        Record record = Record.builder()
            .state(state)
            .country(country)
            .combinedKey(combinedKeyList)
            .confirmed(confirmed)
            .deaths(deaths)
            .lastUpdated(lastUpdated)
            .lat(lat)
            .longt(longt)
            .build();

        recordList.add(record);

      }
    }
    return recordList;
  }

  public static Summary createCountrySummary(List<DailyReportDto> dailyReportDtoList) {
    Optional<DailyReportDto> latestRecord = dailyReportDtoList.stream()
        .sorted(Comparator.comparing(DailyReportDto::getLastUpdated).reversed())
        .findFirst();

    Summary summary = Summary.builder().build();

    if (latestRecord.isPresent()) {
      DailyReportDto report = latestRecord.get();

      summary = Summary.builder()
          .totalConfirmed(report.getConfirmed())
          .totalNewCases(report.getNewCases())
          .totalDeaths(report.getDeaths())
          .totalNewDeaths(report.getNewDeaths())
          .build();

    }

    return summary;
  }

  public static Summary createSummary(Collection<DailyReportDto> collection) {
    return Summary.builder()
        .totalConfirmed(collection.stream().mapToLong(value -> value.getConfirmed()).sum())
        .totalDeaths(collection.stream().mapToLong(value -> value.getDeaths()).sum())
        .totalNewCases(collection.stream().mapToLong(value -> value.getNewCases()).sum())
        .totalNewDeaths(collection.stream().mapToLong(value -> value.getNewDeaths()).sum())
        .build();
  }

  private static String removeUTF8BOM(String s) {
    if (s.startsWith(UTF8_BOM)) {
      s = s.substring(1);
    }

    return s;
  }

  public static List<DailyReportDto> createDailyReportList(List<Record> records) {
    List<DailyReportDto> dailyReportDtos = new ArrayList<>();

    records.stream().forEach(record -> {
      DailyReportDto report = DailyReportDto.builder()
          .confirmed(record.getConfirmed())
          .country(record.getCountry())
          .deaths(record.getDeaths())
          .lastUpdated(record.getLastUpdated())
          .newCases(record.getNewCases())
          .newDeaths(record.getNewDeaths())
          .build();

      dailyReportDtos.add(report);
    });

    return dailyReportDtos;
  }
  public static Aggregation createWorldGraphDataAggregation(LocalDateTime dateTime){
    LocalDateTime endDateTime = dateTime.toLocalDate().atTime(23, 00, 00);
    MatchOperation matchOperation = new MatchOperation(Criteria.where("lastUpdated").gte(LocalDateTime.now().minusMonths(2)).lte(endDateTime));

    GroupOperation groupOperation = group("lastUpdated")
        .first("lastUpdated").as("lastUpdated")
        .sum("newCases").as("newCases")
        .sum("newDeaths").as("newDeaths");

    return newAggregation(
        matchOperation,
        groupOperation,
        sort(Direction.ASC, "lastUpdated" ));
  }

  public static Aggregation createAggregation(Object criteria, String matchBy, String sortBy, Optional<String> optGroupBy) {
    MatchOperation matchOperation = new MatchOperation(Criteria.where(matchBy).is(criteria));

    if (criteria instanceof LocalDateTime) {
      LocalDateTime startDateTime = (LocalDateTime) criteria;
      LocalDateTime endDateTime = startDateTime.toLocalDate().atTime(23, 00, 00);
      matchOperation = new MatchOperation(Criteria.where(matchBy).gte(startDateTime).lte(endDateTime));
    }

    if (optGroupBy.isPresent()) {
      GroupOperation groupOperation = group(optGroupBy.get())
          .first("country").as("country")
          .first("lastUpdated").as("lastUpdated")
          .first("lat").as("lat")
          .first("longt").as("longt")
          .sum("confirmed").as("confirmed")
          .sum("deaths").as("deaths")
          .sum("newCases").as("newCases")
          .sum("newDeaths").as("newDeaths");

      return newAggregation(
          matchOperation,
          groupOperation,
          sort(Direction.DESC, sortBy)
      );
    } else {
      return newAggregation(
          matchOperation,
          sort(Direction.DESC, sortBy)
      );
    }
  }


  @Value("${external.resources.url}")
  public void setResourcesUrl(String url) {
    resourcesUrl = url;
  }

  @Value("${overseas.territories}")
  public void setOverseasTerritories(List<String> list) {
    overseasTerritories.addAll(list);
  }


  @Value("#{${country.abbr.map}}")
  public void setCountryAbbrMap(Map<String, String> map) {
    countryAbbrMap.putAll(map);
  }
}
