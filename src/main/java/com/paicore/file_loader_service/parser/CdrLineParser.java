package com.paicore.file_loader_service.parser;

import com.paicore.file_loader_service.entity.CallDetailRecord;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

@Component
public class CdrLineParser {

	private static final int EXPECTED_COLUMN_COUNT = 33;

	private static final int RECORD_DATE_INDEX = 0;
	private static final int TSTAMP_INDEX = 27;
	private static final int ID_INDEX = 32;

	private static final DateTimeFormatter RECORD_DATE_FORMATTER =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS");

	private static final DateTimeFormatter TSTAMP_FORMATTER =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

	public CallDetailRecord parse(String line) {
		String[] columns = line.split("\\|", -1);

		return CallDetailRecord.builder()
				.recordDate(parseRequiredTimestamp(columns, RECORD_DATE_INDEX, RECORD_DATE_FORMATTER, "RECORD_DATE"))
				.lSpc(parseInteger(column(columns, 1)))
				.lSsn(parseInteger(column(columns, 2)))
				.lRi(parseInteger(column(columns, 3)))
				.lGtI(parseInteger(column(columns, 4)))
				.lGtDigits(parseString(column(columns, 5)))
				.rSpc(parseInteger(column(columns, 6)))
				.rSsn(parseInteger(column(columns, 7)))
				.rRi(parseInteger(column(columns, 8)))
				.rGtI(parseInteger(column(columns, 9)))
				.rGtDigits(parseString(column(columns, 10)))
				.serviceCode(parseString(column(columns, 11)))
				.orNature(parseInteger(column(columns, 12)))
				.orPlan(parseInteger(column(columns, 13)))
				.orDigits(parseString(column(columns, 14)))
				.deNature(parseInteger(column(columns, 15)))
				.dePlan(parseInteger(column(columns, 16)))
				.deDigits(parseString(column(columns, 17)))
				.isdnNature(parseInteger(column(columns, 18)))
				.isdnPlan(parseInteger(column(columns, 19)))
				.msisdn(parseString(column(columns, 20)))
				.vlrNature(parseInteger(column(columns, 21)))
				.vlrPlan(parseInteger(column(columns, 22)))
				.vlrDigits(parseString(column(columns, 23)))
				.imsi(parseString(column(columns, 24)))
				.status(parseRequiredString(column(columns, 25), "STATUS"))
				.type(parseRequiredString(column(columns, 26), "TYPE"))
				.tstamp(parseRequiredTimestamp(columns, TSTAMP_INDEX, TSTAMP_FORMATTER, "TSTAMP"))
				.localDialogId(parseLong(column(columns, 28)))
				.remoteDialogId(parseLong(column(columns, 29)))
				.dialogDuration(parseLong(column(columns, 30)))
				.ussdString(parseString(column(columns, 31)))
				.id(parseRequiredString(column(columns, ID_INDEX), "ID"))
				.build();
	}

	private String column(String[] columns, int index) {
		return index < columns.length ? columns[index] : "";
	}

	private String parseString(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private String parseRequiredString(String value, String fieldName) {
		String parsed = parseString(value);
		if (parsed == null) {
			throw new IllegalArgumentException("Required field '" + fieldName + "' is missing or blank");
		}
		return parsed;
	}

	private Integer parseInteger(String value) {
		String parsed = parseString(value);
		if (parsed == null) {
			return null;
		}
		return Integer.valueOf(parsed);
	}

	private Long parseLong(String value) {
		String parsed = parseString(value);
		if (parsed == null) {
			return null;
		}
		return Long.valueOf(parsed);
	}

	private LocalDateTime parseRequiredTimestamp(
			String[] columns, int index, DateTimeFormatter formatter, String fieldName) {
		String value = parseRequiredString(column(columns, index), fieldName);
		try {
			return LocalDateTime.parse(value, formatter);
		}
		catch (DateTimeParseException ex) {
			throw new IllegalArgumentException(
					"Invalid timestamp for field '" + fieldName + "': " + value, ex);
		}
	}
}
