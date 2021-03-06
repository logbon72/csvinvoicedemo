package co.petproject.billable.csvservice.impl;

import co.petproject.billable.csvservice.api.InvoiceSourceReader;
import co.petproject.billable.csvservice.models.CsvItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class CsvInvoiceSourceReaderImpl implements InvoiceSourceReader {

    public static final int COLUMN_COUNT = 6;

    private static final int COLUMN_IDX_EMPLOYEE_ID = 0;
    private static final int COLUMN_IDX_RATE = 1;
    private static final int COLUMN_IDX_PROJECT = 2;
    private static final int COLUMN_IDX_DATE = 3;
    private static final int COLUMN_IDX_START_TIME = 4;
    private static final int COLUMN_IDX_END_TIME = 5;

    public static final SimpleDateFormat TIME_PARSER = new SimpleDateFormat("HH:mm");
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    static {
        DATE_FORMAT.setLenient(false);
        TIME_PARSER.setLenient(false);
    }

    @Override
    public List<CsvItem> readRecords(InputStream inputStream) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .withIgnoreEmptyLines();
        CSVParser parser = CSVParser.parse(inputStream, StandardCharsets.UTF_8, format);
        List<CSVRecord> records = parser.getRecords();
        log.debug("{}  record(s) found", records.size());
        List<CsvItem> items = new ArrayList<>((int) parser.getRecordNumber());
        records.forEach((line) -> items.add(getItemFromLine(line)));
        return items;
    }


    CsvItem getItemFromLine(CSVRecord csvRecord) {
        int lineNumber = (int) csvRecord.getRecordNumber();
        if (csvRecord.size() < COLUMN_COUNT) {
            throw new InvalidLineFormatException(lineNumber, "Column count is less than expected");
        }

        CsvItem item = new CsvItem();

        try {
            item.setEmployeeId(csvRecord.get(COLUMN_IDX_EMPLOYEE_ID));
            item.setCompany(csvRecord.get(COLUMN_IDX_PROJECT));
            BigDecimal rate = BigDecimal.valueOf(Double.parseDouble(csvRecord.get(COLUMN_IDX_RATE)))
                    .setScale(2, BigDecimal.ROUND_HALF_EVEN);
            item.setRatePerHour(rate);
            item.setDate(DATE_FORMAT.parse(csvRecord.get(COLUMN_IDX_DATE)));

            String startTime = csvRecord.get(COLUMN_IDX_START_TIME);
            String endTime = csvRecord.get(COLUMN_IDX_END_TIME);
            int billableHours;

            try {
                billableHours = getHoursPassed(startTime, endTime);
            } catch (ParseException pe) {
                throw new InvalidLineFormatException(lineNumber, "Invalid time value detected: " + pe.getMessage());
            }

            if (billableHours < 1) {
                throw new InvalidLineFormatException(lineNumber, "Invalid billable hours found");
            }

            item.setHoursWorked(billableHours);
        } catch (NumberFormatException nfe) {
            throw new InvalidLineFormatException(lineNumber, "Could not parse numeric value: " + nfe.getMessage());
        } catch (ParseException pe) {
            throw new InvalidLineFormatException(lineNumber, "Could not parse date value: " + pe.getMessage());
        }

        return item;
    }

    int getHoursPassed(String startTime, String endTime) throws ParseException {
        long startAt = TIME_PARSER.parse(startTime).getTime();
        long endAt = TIME_PARSER.parse(endTime).getTime();

        return (int) ((endAt - startAt) / 3600000L);
    }

}
