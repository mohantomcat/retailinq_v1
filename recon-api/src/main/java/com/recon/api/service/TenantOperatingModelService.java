package com.recon.api.service;

import com.recon.api.domain.SaveTenantOperatingModelRequest;
import com.recon.api.domain.TenantConfig;
import com.recon.api.domain.TenantOperatingModelDto;
import com.recon.api.util.TimezoneConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantOperatingModelService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_CALENDAR_DAYS_TO_SCAN = 370;

    private final TenantService tenantService;

    public TenantOperatingModelDto getOperatingModel(String tenantId) {
        return toDto(tenantService.getTenant(tenantId));
    }

    public TenantOperatingModelDto saveOperatingModel(String tenantId, SaveTenantOperatingModelRequest request) {
        return toDto(tenantService.saveOperatingModel(tenantId, request));
    }

    public TenantOperatingModelDto toDto(TenantConfig tenant) {
        OperatingCalendar calendar = calendar(tenant);
        ZonedDateTime now = ZonedDateTime.now(calendar.zoneId());
        return TenantOperatingModelDto.builder()
                .tenantId(tenant.getTenantId())
                .tenantName(tenant.getTenantName())
                .timezone(calendar.zoneId().getId())
                .countryCode(tenant.getCountryCode())
                .currencyCode(tenant.getCurrencyCode())
                .localeCode(tenant.getLocaleCode())
                .dateDisplayFormat(tenant.getDateDisplayFormat())
                .weekStartDay(calendar.weekStartDay().name())
                .businessDays(calendar.businessDays().stream()
                        .sorted(Comparator.comparingInt(DayOfWeek::getValue))
                        .map(DayOfWeek::name)
                        .toList())
                .businessDaysLabel(formatBusinessDaysLabel(calendar.businessDays(), calendar.locale()))
                .workdayStartTime(calendar.workdayStart().format(TIME_FORMATTER))
                .workdayEndTime(calendar.workdayEnd().format(TIME_FORMATTER))
                .workdayHoursLabel(calendar.workdayStart().format(TIME_FORMATTER)
                        + " - " + calendar.workdayEnd().format(TIME_FORMATTER))
                .holidayDates(calendar.holidays().stream()
                        .sorted()
                        .map(LocalDate::toString)
                        .toList())
                .holidayCount(calendar.holidays().size())
                .currentLocalTime(now.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm z", calendar.locale())))
                .currentBusinessDate(TimezoneConverter.dateToDisplay(now.toLocalDate().toString(), tenant))
                .calendarAwareSla(true)
                .build();
    }

    public LocalDateTime addBusinessMinutes(LocalDateTime serverLocalReference,
                                            Integer minutes,
                                            TenantConfig tenant) {
        if (serverLocalReference == null) {
            return null;
        }
        int remaining = Math.max(0, Objects.requireNonNullElse(minutes, 0));
        if (remaining == 0) {
            return serverLocalReference;
        }

        OperatingCalendar calendar = calendar(tenant);
        ZonedDateTime cursor = toTenantTime(serverLocalReference, calendar.zoneId());
        cursor = moveToBusinessMoment(cursor, calendar, false);

        while (remaining > 0) {
            ZonedDateTime windowEnd = cursor.toLocalDate()
                    .atTime(calendar.workdayEnd())
                    .atZone(calendar.zoneId());
            long available = Math.max(0, Duration.between(cursor, windowEnd).toMinutes());
            if (available == 0) {
                cursor = moveToBusinessMoment(cursor.plusMinutes(1), calendar, true);
                continue;
            }
            long consumed = Math.min(available, remaining);
            cursor = cursor.plusMinutes(consumed);
            remaining -= (int) consumed;
            if (remaining > 0) {
                cursor = moveToBusinessMoment(cursor.plusMinutes(1), calendar, true);
            }
        }

        return toServerLocalTime(cursor);
    }

    public long businessMinutesBetween(LocalDateTime serverLocalStart,
                                       LocalDateTime serverLocalEnd,
                                       TenantConfig tenant) {
        if (serverLocalStart == null || serverLocalEnd == null || !serverLocalEnd.isAfter(serverLocalStart)) {
            return 0L;
        }

        OperatingCalendar calendar = calendar(tenant);
        ZonedDateTime start = toTenantTime(serverLocalStart, calendar.zoneId());
        ZonedDateTime end = toTenantTime(serverLocalEnd, calendar.zoneId());
        LocalDate cursor = start.toLocalDate();
        LocalDate endDate = end.toLocalDate();
        long totalMinutes = 0L;

        while (!cursor.isAfter(endDate)) {
            if (isBusinessDate(cursor, calendar)) {
                ZonedDateTime businessStart = cursor.atTime(calendar.workdayStart()).atZone(calendar.zoneId());
                ZonedDateTime businessEnd = cursor.atTime(calendar.workdayEnd()).atZone(calendar.zoneId());
                ZonedDateTime effectiveStart = businessStart.isAfter(start) ? businessStart : start;
                ZonedDateTime effectiveEnd = businessEnd.isBefore(end) ? businessEnd : end;
                if (effectiveEnd.isAfter(effectiveStart)) {
                    totalMinutes += Duration.between(effectiveStart, effectiveEnd).toMinutes();
                }
            }
            cursor = cursor.plusDays(1);
        }

        return Math.max(totalMinutes, 0L);
    }

    public long businessAgeHours(LocalDateTime serverLocalStart, TenantConfig tenant) {
        return businessMinutesBetween(serverLocalStart, LocalDateTime.now(), tenant) / 60L;
    }

    public LocalDate currentBusinessDate(TenantConfig tenant) {
        return ZonedDateTime.now(calendar(tenant).zoneId()).toLocalDate();
    }

    public String toLocalDateTimeInput(LocalDateTime serverLocalDateTime, TenantConfig tenant) {
        if (serverLocalDateTime == null) {
            return null;
        }
        return toTenantTime(serverLocalDateTime, calendar(tenant).zoneId())
                .toLocalDateTime()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }

    public LocalDateTime parseLocalDateTimeInput(String localDateTimeValue, TenantConfig tenant) {
        String trimmed = trimToNull(localDateTimeValue);
        if (trimmed == null) {
            return null;
        }
        LocalDateTime tenantLocal = LocalDateTime.parse(trimmed);
        return tenantLocal.atZone(calendar(tenant).zoneId())
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private OperatingCalendar calendar(TenantConfig tenant) {
        ZoneId zoneId = ZoneId.of(tenant.getTimezone());
        Locale locale = Locale.forLanguageTag(tenant.getLocaleCode());
        DayOfWeek weekStartDay = DayOfWeek.valueOf(tenant.getWeekStartDay());
        Set<DayOfWeek> businessDays = parseBusinessDays(tenant.getBusinessDays());
        LocalTime workdayStart = LocalTime.parse(tenant.getWorkdayStartTime());
        LocalTime workdayEnd = LocalTime.parse(tenant.getWorkdayEndTime());
        Set<LocalDate> holidays = parseHolidayCalendar(tenant.getHolidayCalendar());
        return new OperatingCalendar(zoneId, locale, weekStartDay, businessDays, workdayStart, workdayEnd, holidays);
    }

    private Set<DayOfWeek> parseBusinessDays(String value) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String item : csvItems(value)) {
            try {
                days.add(DayOfWeek.valueOf(item));
            } catch (Exception ignored) {
                // Defaults are normalized earlier by TenantService.
            }
        }
        return days.isEmpty() ? EnumSet.allOf(DayOfWeek.class) : days;
    }

    private Set<LocalDate> parseHolidayCalendar(String value) {
        return csvItems(value).stream()
                .map(this::parseDate)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private ZonedDateTime moveToBusinessMoment(ZonedDateTime candidate,
                                               OperatingCalendar calendar,
                                               boolean skipCurrentMinute) {
        ZonedDateTime cursor = skipCurrentMinute ? candidate.plusSeconds(0) : candidate;
        for (int i = 0; i < MAX_CALENDAR_DAYS_TO_SCAN; i++) {
            LocalDate date = cursor.toLocalDate();
            if (!isBusinessDate(date, calendar)) {
                cursor = nextBusinessDayStart(date.plusDays(1), calendar);
                continue;
            }
            LocalTime time = cursor.toLocalTime();
            if (time.isBefore(calendar.workdayStart())) {
                return date.atTime(calendar.workdayStart()).atZone(calendar.zoneId());
            }
            if (!time.isBefore(calendar.workdayEnd())) {
                cursor = nextBusinessDayStart(date.plusDays(1), calendar);
                continue;
            }
            return cursor;
        }
        return candidate;
    }

    private ZonedDateTime nextBusinessDayStart(LocalDate candidateDate, OperatingCalendar calendar) {
        LocalDate cursor = candidateDate;
        for (int i = 0; i < MAX_CALENDAR_DAYS_TO_SCAN; i++) {
            if (isBusinessDate(cursor, calendar)) {
                return cursor.atTime(calendar.workdayStart()).atZone(calendar.zoneId());
            }
            cursor = cursor.plusDays(1);
        }
        return candidateDate.atTime(calendar.workdayStart()).atZone(calendar.zoneId());
    }

    private boolean isBusinessDate(LocalDate date, OperatingCalendar calendar) {
        return calendar.businessDays().contains(date.getDayOfWeek())
                && !calendar.holidays().contains(date);
    }

    private ZonedDateTime toTenantTime(LocalDateTime serverLocalDateTime, ZoneId tenantZone) {
        return serverLocalDateTime.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(tenantZone);
    }

    private LocalDateTime toServerLocalTime(ZonedDateTime tenantDateTime) {
        return tenantDateTime.withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private List<String> csvItems(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return List.of();
        }
        return List.of(trimmed.split(",")).stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .map(item -> item.toUpperCase(Locale.ROOT))
                .toList();
    }

    private String formatBusinessDaysLabel(Set<DayOfWeek> businessDays, Locale locale) {
        return businessDays.stream()
                .sorted(Comparator.comparingInt(DayOfWeek::getValue))
                .map(day -> day.getDisplayName(TextStyle.SHORT, locale))
                .collect(Collectors.joining(", "));
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record OperatingCalendar(ZoneId zoneId,
                                     Locale locale,
                                     DayOfWeek weekStartDay,
                                     Set<DayOfWeek> businessDays,
                                     LocalTime workdayStart,
                                     LocalTime workdayEnd,
                                     Set<LocalDate> holidays) {
    }
}
