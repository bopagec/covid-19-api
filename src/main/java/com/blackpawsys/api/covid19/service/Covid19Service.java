package com.blackpawsys.api.covid19.service;

import com.blackpawsys.api.covid19.dto.WorldGraphDataDto;
import com.blackpawsys.api.covid19.model.Record;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface Covid19Service {

  void deleteAll();

  void saveAll(List<Record> recordList);

  Record findLatestRecord();

  List<Record> findByDate(LocalDateTime date, Optional<String> optGroupBy);

  List<Record> findByCountry(String country);

  List<WorldGraphDataDto> generateWorldGraphData(LocalDateTime date);
}
